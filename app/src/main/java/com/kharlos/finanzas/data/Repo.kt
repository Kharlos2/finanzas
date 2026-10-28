package com.kharlos.finanzas.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kharlos.finanzas.MainActivity
import com.kharlos.finanzas.R
import com.kharlos.finanzas.domain.Cat
import com.kharlos.finanzas.domain.Categorizer
import com.kharlos.finanzas.domain.Money
import com.kharlos.finanzas.domain.Tx
import com.kharlos.finanzas.parser.Kind
import com.kharlos.finanzas.parser.SmsParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.security.MessageDigest
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

object Repo {
    lateinit var db: Db
    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context

    /** Sube cada vez que algo cambia en la base; las pantallas lo observan para recargar. */
    val version = MutableStateFlow(0)

    fun init(context: Context) {
        app = context.applicationContext
        db = Db(app)
        prefs = app.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
        Notifier.createChannels(app)
    }

    fun changed() = version.update { it + 1 }

    var bigExpenseCents: Long
        get() = prefs.getLong("big_expense", 300_000_00L)
        set(v) { prefs.edit().putLong("big_expense", v).apply(); changed() }

    var notifyEachTx: Boolean
        get() = prefs.getBoolean("notify_each", true)
        set(v) { prefs.edit().putBoolean("notify_each", v).apply(); changed() }

    // ---------- Entrada de movimientos ----------

    /** Procesa un SMS. Devuelve el movimiento guardado, o null si no era bancario o ya estaba. */
    fun ingestSms(body: String, smsTimeMillis: Long, live: Boolean): Tx? {
        val parsed = SmsParser.parse(body) ?: return null
        val hash = sha256(body.replace(Regex("""\s+"""), " ").trim())
        if (db.hashExists(hash)) return null
        val time = parsed.timeMillis ?: smsTimeMillis

        if (parsed.reversal) {
            val original = db.findForReversal(parsed.bank, parsed.amountCents, parsed.description, parsed.product)
            val tx = if (original != null) {
                db.updateTx(original.copy(excluded = true, note = "Reversada"))
                Tx(timeMillis = time, amountCents = parsed.amountCents, isIncome = false, bank = parsed.bank, kind = "REVERSO",
                    description = "Reverso ${parsed.description}", product = parsed.product, categoryId = original.categoryId,
                    source = "SMS", excluded = true, hash = hash)
            } else {
                Tx(timeMillis = time, amountCents = parsed.amountCents, isIncome = true, bank = parsed.bank, kind = "REVERSO",
                    description = "Reverso ${parsed.description}", product = parsed.product, categoryId = Cat.REEMBOLSOS,
                    source = "SMS", hash = hash)
            }
            db.insertTx(tx)
            changed()
            if (live) Notifier.tx(app, tx, "Reverso registrado")
            return tx
        }

        val categoryId = Categorizer.categorize(parsed.description, parsed.kind, parsed.isIncome, db.rules(), db.categories())
        val tx = Tx(
            timeMillis = time, amountCents = parsed.amountCents, isIncome = parsed.isIncome, bank = parsed.bank,
            kind = parsed.kind.name, description = parsed.description, product = parsed.product, categoryId = categoryId,
            source = "SMS", reviewed = parsed.recognized, hash = hash,
        )
        val id = db.insertTx(tx)
        if (id == -1L) return null
        val saved = tx.copy(id = id)
        changed()
        if (live) {
            if (notifyEachTx || !parsed.recognized) Notifier.tx(app, saved, if (parsed.recognized) null else "Formato nuevo: revísalo")
            checkAlerts(saved)
        }
        return saved
    }

    fun saveManual(tx: Tx) {
        if (tx.id == 0L) {
            val id = db.insertTx(tx.copy(source = "MANUAL"))
            changed()
            checkAlerts(tx.copy(id = id))
        } else {
            db.updateTx(tx)
            changed()
        }
    }

    fun deleteTx(id: Long) { db.deleteTx(id); changed() }

    /** Lee la bandeja de SMS desde [fromMillis]. Devuelve cuántos movimientos nuevos se guardaron. */
    fun importInbox(fromMillis: Long): Int {
        var count = 0
        app.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(fromMillis.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val body = c.getString(0) ?: continue
                if (!SmsParser.isBankSms(body)) continue
                if (ingestSms(body, c.getLong(1), live = false) != null) count++
            }
        }
        return count
    }

    fun addRuleAndApply(pattern: String, categoryId: Long, income: Boolean): Int {
        db.addRule(pattern, categoryId)
        val n = db.applyRule({ Categorizer.matches(pattern, it) }, categoryId, income)
        changed()
        return n
    }

    // ---------- Alertas ----------

    private fun checkAlerts(tx: Tx) {
        if (tx.isIncome || tx.excluded) return
        val cats = db.categories().associateBy { it.id }
        val cat = cats[tx.categoryId]
        if (cat?.excludeFromStats == true) return

        if (tx.amountCents >= bigExpenseCents) {
            Notifier.alert(app, "Gasto grande: ${Money.format(tx.amountCents)}", "${tx.description} · ${cat?.name ?: ""}")
        }

        val zone = ZoneId.systemDefault()
        val ym = YearMonth.from(Instant.ofEpochMilli(tx.timeMillis).atZone(zone))
        if (ym != YearMonth.now(zone)) return
        val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val budgets = db.budgets()

        for (c in listOf(tx.categoryId, Cat.TOTAL)) {
            val limit = budgets[c] ?: continue
            val spent = db.spentInCategory(c, from, to)
            val pct = (spent * 100 / limit).toInt()
            val name = if (c == Cat.TOTAL) "el presupuesto total" else "${cats[c]?.emoji ?: ""} ${cats[c]?.name ?: ""}"
            if (pct >= 100) {
                db.markAlert(c, ym.toString(), 80)
                if (db.markAlert(c, ym.toString(), 100))
                    Notifier.alert(app, "Superaste $name", "Llevas ${Money.format(spent)} de ${Money.format(limit)} ($pct%)")
            } else if (pct >= 80 && db.markAlert(c, ym.toString(), 80)) {
                Notifier.alert(app, "Vas en $pct% de $name", "Llevas ${Money.format(spent)} de ${Money.format(limit)}. Quedan ${Money.format(limit - spent)}")
            }
        }
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

object Notifier {
    private const val CH_TX = "movimientos"
    private const val CH_ALERT = "alertas"
    private var nextId = (System.currentTimeMillis() % 100000).toInt()

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TX, "Movimientos", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Alertas de presupuesto", NotificationManager.IMPORTANCE_HIGH))
    }

    fun tx(ctx: Context, tx: Tx, extra: String?) {
        val cat = Repo.db.categories().firstOrNull { it.id == tx.categoryId }
        val title = (if (tx.isIncome) "Ingreso " else "Gasto ") + Money.format(tx.amountCents)
        val text = listOfNotNull(tx.description, cat?.let { "${it.emoji} ${it.name}" }, extra).joinToString(" · ")
        show(ctx, CH_TX, title, text)
    }

    fun alert(ctx: Context, title: String, text: String) = show(ctx, CH_ALERT, title, text)

    private fun show(ctx: Context, channel: String, title: String, text: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(nextId++, n) } catch (_: SecurityException) {}
    }
}

/** Kind del parser guardado como texto → etiqueta legible */
fun kindLabel(kind: String) = when (kind) {
    Kind.COMPRA.name -> "Compra"; Kind.TRANSFERENCIA.name -> "Transferencia"; Kind.RECARGA.name -> "Recarga"
    Kind.NOMINA.name -> "Nómina"; Kind.INGRESO.name -> "Ingreso"; Kind.PAGO.name -> "Pago"
    Kind.PAGO_TC.name -> "Pago tarjeta"; Kind.RETIRO.name -> "Retiro"; "REVERSO" -> "Reverso"; "MANUAL" -> "Manual"
    else -> "Otro"
}
