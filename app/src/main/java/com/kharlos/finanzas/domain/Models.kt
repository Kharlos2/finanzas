package com.kharlos.finanzas.domain

import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

data class Tx(
    val id: Long = 0,
    val timeMillis: Long,
    val amountCents: Long,
    val isIncome: Boolean,
    val bank: String,
    val kind: String,
    val description: String,
    val product: String? = null,
    val categoryId: Long,
    val source: String = "MANUAL",   // SMS | MANUAL
    val note: String = "",
    val excluded: Boolean = false,  // reversadas: no cuentan
    val reviewed: Boolean = true,   // false = vino de un formato desconocido
    val hash: String? = null,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val income: Boolean,
    val excludeFromStats: Boolean = false, // pagos de tarjeta, movimientos entre mis cuentas
)

data class Rule(val id: Long = 0, val pattern: String, val categoryId: Long, val builtin: Boolean)

data class Goal(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val targetCents: Long,
    val savedCents: Long,
    val deadlineMillis: Long? = null,
)

/** IDs fijos de las categorías que crea la app al iniciar. */
object Cat {
    const val COMIDA = 1L; const val MERCADO = 2L; const val TRANSPORTE = 3L; const val SALUD = 4L
    const val EDUCACION = 5L; const val SERVICIOS = 6L; const val SUSCRIPCIONES = 7L; const val EFECTIVO = 8L
    const val TRANSFERENCIAS = 9L; const val HOGAR = 10L; const val OCIO = 11L; const val COMPRAS = 12L
    const val OTROS_GASTOS = 13L
    const val SALARIO = 20L; const val OTROS_INGRESOS = 21L; const val REEMBOLSOS = 22L
    const val PAGO_TARJETA = 30L; const val ENTRE_CUENTAS = 31L
    const val TOTAL = 0L // usado en presupuestos: tope total del mes

    val defaults = listOf(
        Category(COMIDA, "Comida", "🍔", false),
        Category(MERCADO, "Mercado", "🛒", false),
        Category(TRANSPORTE, "Transporte", "🚌", false),
        Category(SALUD, "Salud", "💊", false),
        Category(EDUCACION, "Educación", "🎓", false),
        Category(SERVICIOS, "Servicios", "💡", false),
        Category(SUSCRIPCIONES, "Suscripciones", "📺", false),
        Category(EFECTIVO, "Efectivo", "💵", false),
        Category(TRANSFERENCIAS, "Transferencias", "↗️", false),
        Category(HOGAR, "Hogar", "🏠", false),
        Category(OCIO, "Ocio", "🎉", false),
        Category(COMPRAS, "Compras", "🛍️", false),
        Category(OTROS_GASTOS, "Otros gastos", "📦", false),
        Category(SALARIO, "Salario", "💼", true),
        Category(OTROS_INGRESOS, "Otros ingresos", "💰", true),
        Category(REEMBOLSOS, "Reembolsos", "↩️", true),
        Category(PAGO_TARJETA, "Pago tarjeta", "💳", false, excludeFromStats = true),
        Category(ENTRE_CUENTAS, "Entre mis cuentas", "🔁", false, excludeFromStats = true),
    )

    // Palabra clave (en mayúsculas) → categoría. Editables desde Ajustes > Reglas.
    val defaultRules = listOf(
        "WHOOSH" to TRANSPORTE, "CIVICA" to TRANSPORTE, "UBER" to TRANSPORTE, "DIDI" to TRANSPORTE,
        "CABIFY" to TRANSPORTE, "INDRIVE" to TRANSPORTE, "METRO DE MEDELLIN" to TRANSPORTE, "TERPEL" to TRANSPORTE,
        "PRIMAX" to TRANSPORTE, "BIOMAX" to TRANSPORTE, "ESTACION DE SERVICIO" to TRANSPORTE, "PEAJE" to TRANSPORTE,
        "BURGER" to COMIDA, "RESTAURANTE" to COMIDA, "RAPPI" to COMIDA, "PIZZA" to COMIDA, "FRISBY" to COMIDA,
        "KFC" to COMIDA, "MCDONALD" to COMIDA, "CREPES" to COMIDA, "PANADERIA" to COMIDA, "JUAN VALDEZ" to COMIDA,
        "TOSTAO" to COMIDA, "IFOOD" to COMIDA, "CORRAL" to COMIDA,
        "EXITO" to MERCADO, "CARULLA" to MERCADO, "D1 " to MERCADO, "TIENDAS D1" to MERCADO, "ARA " to MERCADO,
        "JERONIMO MARTINS" to MERCADO, "OLIMPICA" to MERCADO, "JUMBO" to MERCADO, "EURO SUPERMERCADO" to MERCADO,
        "SUPERMERCADO" to MERCADO, "CONSUMO" to MERCADO, "PRICESMART" to MERCADO, "MAKRO" to MERCADO,
        "DROGUERIA" to SALUD, "FARMACIA" to SALUD, "CRUZ VERDE" to SALUD, "FARMATODO" to SALUD, "LA REBAJA" to SALUD,
        "CLINICA" to SALUD, "LABORATORIO" to SALUD,
        "INSTITUTO UNIV" to EDUCACION, "UNIVERSIDAD" to EDUCACION, "PASCU" to EDUCACION, "PLATZI" to EDUCACION,
        "UDEMY" to EDUCACION, "COURSERA" to EDUCACION, "LIBRERIA" to EDUCACION, "PANAMERICANA" to EDUCACION,
        "EMPRESAS PUBLICAS" to SERVICIOS, "EPM" to SERVICIOS, "CLARO" to SERVICIOS, "TIGO" to SERVICIOS,
        "MOVISTAR" to SERVICIOS, "RECARGA SALDO" to SERVICIOS, "WOM" to SERVICIOS,
        "ANTHROPIC" to SUSCRIPCIONES, "CLAUDE" to SUSCRIPCIONES, "NETFLIX" to SUSCRIPCIONES,
        "SPOTIFY" to SUSCRIPCIONES, "DISNEY" to SUSCRIPCIONES, "YOUTUBE" to SUSCRIPCIONES,
        "GOOGLE" to SUSCRIPCIONES, "APPLE.COM" to SUSCRIPCIONES, "OPENAI" to SUSCRIPCIONES,
        "PRIME VIDEO" to SUSCRIPCIONES, "HBO" to SUSCRIPCIONES, "MAX.COM" to SUSCRIPCIONES, "GITHUB" to SUSCRIPCIONES,
        "AMAZON" to COMPRAS, "MERCADOLIBRE" to COMPRAS, "MERCADO LIBRE" to COMPRAS, "FALABELLA" to COMPRAS,
        "TEMU" to COMPRAS, "SHEIN" to COMPRAS, "ALKOSTO" to COMPRAS, "KTRONIX" to COMPRAS, "HOMECENTER" to HOGAR,
        "CINE" to OCIO, "CINEMARK" to OCIO, "PROCINAL" to OCIO, "STEAM" to OCIO, "PLAYSTATION" to OCIO,
        "SMARTFIT" to OCIO, "BODYTECH" to OCIO,
    )
}

object Money {
    private val fmt = NumberFormat.getNumberInstance(Locale("es", "CO")).apply { maximumFractionDigits = 0 }
    fun format(cents: Long): String = "$" + fmt.format(Math.round(cents / 100.0))
    fun signed(cents: Long, income: Boolean) = (if (income) "+" else "−") + format(cents)
    /** "58400" "58.400" "58400,5" → centavos; null si no es número */
    fun parseInput(s: String): Long? {
        val t = s.replace("$", "").replace(" ", "").replace(".", "").replace(",", ".")
        return t.toBigDecimalOrNull()?.movePointRight(2)?.toLong()?.takeIf { it > 0 }
    }
}

fun Long.toYearMonth(zone: ZoneId = ZoneId.systemDefault()): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(this).atZone(zone))
