package com.kharlos.finanzas.parser

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// Tipo de operación detectada en el SMS
enum class Kind { COMPRA, TRANSFERENCIA, RECARGA, NOMINA, INGRESO, PAGO, PAGO_TC, RETIRO, OTRO }

data class ParsedTx(
    val bank: String,            // "Bancolombia" | "Caja Social"
    val kind: Kind,
    val isIncome: Boolean,
    val amountCents: Long,
    val description: String,
    val product: String?,        // "T.Deb *8773", "Cta *2084", "TC *3069"
    val timeMillis: Long?,       // null si el SMS no trae fecha
    val reversal: Boolean = false,
    val recognized: Boolean = true // false = lo sacó el parser genérico, conviene revisarlo
)

object SmsParser {

    const val BANCOLOMBIA = "Bancolombia"
    const val CAJA_SOCIAL = "Caja Social"

    private const val AMT = """\$?\s?([\d][\d.,]*\d|\d)"""
    private const val DATE = """(\d{1,2}/\d{1,2}/\d{2,4})"""
    private const val TIME = """(\d{1,2}:\d{2}(?::\d{2})?)"""
    private val opts = setOf(RegexOption.IGNORE_CASE)

    private val bcTransfer = Regex("""Transferiste $AMT desde tu cuenta \*+(\d+) a la cuenta \*+(\d+) el $DATE a las $TIME""", opts)
    private val bcRecarga = Regex("""Recargaste $AMT a (.+?) desde tu cuenta \*+(\d+) el $DATE (?:a las )?$TIME""", opts)
    private val bcRecibiste = Regex("""Recibiste un pago de (.+?) por $AMT en tu cuenta(?: de)? (\S+) el $DATE a las $TIME""", opts)
    private val bcCompra = Regex("""Compraste $AMT en (.+?) con tu T\.?\s?(Deb|Cred)\w* \*+(\d+),? el $DATE a las $TIME""", opts)
    private val bcPago = Regex("""Pagaste $AMT a (.+?) desde tu producto \*?(\d+) el $DATE (?:a las )?$TIME""", opts)
    private val bcRetiro = Regex("""Retiraste $AMT en (.+?) de tu T\.?\s?(Deb|Cred)\w* \*+(\d+),? el $DATE a las $TIME""", opts)

    private val csTx = Regex("""TX (EXITOSA|REVERSADA) en \*(\d+)\.\s*El (\d{4})\.(\d{2})\.(\d{2}) a las $TIME un\(a\) (.+?) por $AMT en (.+?)\.?\s*Banco Caja Social""", opts)
    private val csPago = Regex("""Su pago por $AMT para (.+?) quedo Aprobado\.?\s*(\d{1,2})/(\d{1,2})/(\d{4}) $TIME""", opts)

    // Genéricos para formatos que aún no conocemos
    private val anyAmount = Regex("""\$\s?([\d][\d.,]*\d|\d)""")
    private val anyDate = Regex("""(\d{1,2}/\d{1,2}/\d{2,4})(?:\s*(?:a las\s*)?$TIME)?""", opts)
    private val incomeWords = Regex("""recibiste|abono|consignaci|te enviaron|ingreso|devoluci|reembolso""", opts)
    private val expenseWords = Regex("""compraste|pagaste|transferiste|retiraste|recargaste|enviaste|compra|pago|retiro|debito|débito""", opts)

    /** true si el texto parece venir de uno de los dos bancos. */
    fun isBankSms(body: String): Boolean {
        val b = body.trimStart()
        return b.startsWith("Bancolombia", true) || b.contains("Banco Caja Social", true)
    }

    fun parse(rawBody: String, zone: ZoneId = ZoneId.systemDefault()): ParsedTx? {
        val body = rawBody.replace(Regex("""\s+"""), " ").trim()
        if (!isBankSms(body)) return null
        return if (body.contains("Caja Social", true)) parseCajaSocial(body, zone) ?: generic(body, CAJA_SOCIAL, zone)
        else parseBancolombia(body, zone) ?: generic(body, BANCOLOMBIA, zone)
    }

    private fun parseBancolombia(b: String, zone: ZoneId): ParsedTx? {
        bcCompra.find(b)?.let { m ->
            val (amt, merchant, tipo, card, date, time) = m.destructured
            return ParsedTx(BANCOLOMBIA, Kind.COMPRA, false, amountToCents(amt), clean(merchant),
                "T.${tipo.cap()} *$card", toMillis(date, time, zone))
        }
        bcTransfer.find(b)?.let { m ->
            val (amt, from, to, date, time) = m.destructured
            return ParsedTx(BANCOLOMBIA, Kind.TRANSFERENCIA, false, amountToCents(amt),
                "Transferencia a *$to", "Cta *$from", toMillis(date, time, zone))
        }
        bcRecarga.find(b)?.let { m ->
            val (amt, target, from, date, time) = m.destructured
            return ParsedTx(BANCOLOMBIA, Kind.RECARGA, false, amountToCents(amt),
                "Recarga ${clean(target)}", "Cta *$from", toMillis(date, time, zone))
        }
        bcPago.find(b)?.let { m ->
            val (amt, to, prod, date, time) = m.destructured
            return ParsedTx(BANCOLOMBIA, Kind.PAGO, false, amountToCents(amt), clean(to),
                "Cta *${prod.takeLast(4)}", toMillis(date, time, zone))
        }
        bcRetiro.find(b)?.let { m ->
            val (amt, place, tipo, card, date, time) = m.destructured
            return ParsedTx(BANCOLOMBIA, Kind.RETIRO, false, amountToCents(amt), "Retiro ${clean(place)}",
                "T.${tipo.cap()} *$card", toMillis(date, time, zone))
        }
        bcRecibiste.find(b)?.let { m ->
            val (from, amt, acc, date, time) = m.destructured
            val desc = clean(from)
            val kind = if (desc.startsWith("Nomina", true) || desc.startsWith("Nómina", true)) Kind.NOMINA else Kind.INGRESO
            return ParsedTx(BANCOLOMBIA, kind, true, amountToCents(amt), desc,
                "Cta ${acc.cap()}", toMillis(date, time, zone))
        }
        return null
    }

    private fun parseCajaSocial(b: String, zone: ZoneId): ParsedTx? {
        csTx.find(b)?.let { m ->
            val g = m.groupValues
            val status = g[1]; val card = g[2]
            val time = g[6]; val op = g[7]; val amt = g[8]; val merchant = g[9]
            val date = LocalDate.of(g[3].toInt(), g[4].toInt(), g[5].toInt())
            val isCredit = op.contains("CREDITO", true) || op.contains("CRÉDITO", true)
            val kind = when {
                op.contains("RETIRO", true) -> Kind.RETIRO
                op.contains("COMPRA", true) -> Kind.COMPRA
                op.contains("PAGO", true) -> Kind.PAGO
                else -> Kind.OTRO
            }
            return ParsedTx(CAJA_SOCIAL, kind, false, amountToCents(amt), clean(merchant),
                (if (isCredit) "TC *" else "Tarj *") + card, millis(date, time, zone),
                reversal = status.equals("REVERSADA", true))
        }
        csPago.find(b)?.let { m ->
            val g = m.groupValues
            val amt = g[1]; val target = clean(g[2])
            // Este formato viene como MM/dd/yyyy (ej. 10/18/2026); si el primer número pasa de 12 es dd/MM
            var month = g[3].toInt(); var day = g[4].toInt()
            if (month > 12) { val t = month; month = day; day = t }
            val date = LocalDate.of(g[5].toInt(), month, day)
            val isCard = target.contains("TARJETA", true)
            return ParsedTx(CAJA_SOCIAL, if (isCard) Kind.PAGO_TC else Kind.PAGO, false, amountToCents(amt),
                if (isCard) "Pago tarjeta de crédito" else "Pago $target", null, millis(date, g[6], zone))
        }
        return null
    }

    // Plan B: cualquier SMS del banco con un monto se registra para revisión en vez de perderse
    private fun generic(b: String, bank: String, zone: ZoneId): ParsedTx? {
        val amt = anyAmount.find(b)?.groupValues?.get(1) ?: return null
        val cents = runCatching { amountToCents(amt) }.getOrNull() ?: return null
        if (cents <= 0) return null
        val income = incomeWords.containsMatchIn(b) && !Regex("""compraste|pagaste|transferiste""", opts).containsMatchIn(b)
        if (!income && !expenseWords.containsMatchIn(b)) return null
        val d = anyDate.find(b)
        val millis = d?.let { runCatching { toMillis(it.groupValues[1], it.groupValues[2].ifEmpty { "00:00" }, zone) }.getOrNull() }
        val snippet = b.removePrefix("Bancolombia:").trim().take(60)
        return ParsedTx(bank, if (income) Kind.INGRESO else Kind.OTRO, income, cents, snippet, null, millis, recognized = false)
    }

    /** "$150,000.00" "$58.400,00" "$50,000" "20,000.00" "$64,337.15" → centavos */
    fun amountToCents(raw: String): Long {
        val s = raw.replace("$", "").replace(" ", "").trim()
        val lastSep = maxOf(s.lastIndexOf('.'), s.lastIndexOf(','))
        if (lastSep < 0) return s.toLong() * 100
        val decimals = s.substring(lastSep + 1)
        return if (decimals.length in 1..2) {
            val intPart = s.substring(0, lastSep).replace(".", "").replace(",", "")
            (if (intPart.isEmpty()) 0L else intPart.toLong()) * 100 + decimals.padEnd(2, '0').toLong()
        } else {
            s.replace(".", "").replace(",", "").toLong() * 100
        }
    }

    // Bancolombia usa dd/MM/yy o dd/MM/yyyy
    private fun toMillis(date: String, time: String, zone: ZoneId): Long {
        val p = date.split("/").map { it.toInt() }
        val year = if (p[2] < 100) 2000 + p[2] else p[2]
        return millis(LocalDate.of(year, p[1], p[0]), time, zone)
    }

    private fun millis(date: LocalDate, time: String, zone: ZoneId): Long {
        val t = time.split(":").map { it.toInt() }
        val lt = LocalTime.of(t[0], t[1], t.getOrElse(2) { 0 })
        return LocalDateTime.of(date, lt).atZone(zone).toInstant().toEpochMilli()
    }

    private fun clean(s: String) = s.trim().trimEnd('.', ',').replace(Regex("""\s+"""), " ")
    private fun String.cap() = lowercase().replaceFirstChar { it.uppercase() }
}
