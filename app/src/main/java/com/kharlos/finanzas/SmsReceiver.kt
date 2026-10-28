package com.kharlos.finanzas

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kharlos.finanzas.data.Repo

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}

/** Recibe cada SMS que llega; si es de Bancolombia o Caja Social lo guarda como movimiento. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Un SMS largo llega partido: se unen las partes por remitente
        val bySender = messages.filterNotNull().groupBy { it.originatingAddress ?: "" }
        val pending = goAsync()
        Thread {
            try {
                for ((_, parts) in bySender) {
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val time = parts.first().timestampMillis
                    runCatching { Repo.ingestSms(body, time, live = true) }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
