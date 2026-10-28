package com.kharlos.finanzas.domain

import com.kharlos.finanzas.parser.Kind
import java.text.Normalizer
import java.time.YearMonth
import java.time.ZoneId

object Categorizer {

    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "").uppercase()

    /** Coincide si el patrón aparece al inicio de una palabra ("PASCU" encuentra "PASCUAL", "TIGO" no encuentra "CONTIGO"). */
    fun matches(pattern: String, text: String): Boolean {
        val p = norm(pattern).trim()
        if (p.isEmpty()) return false
        val t = norm(text)
        var i = t.indexOf(p)
        while (i >= 0) {
            if (i == 0 || !t[i - 1].isLetterOrDigit()) return true
            i = t.indexOf(p, i + 1)
        }
        return false
    }

    fun categorize(description: String, kind: Kind, isIncome: Boolean, rules: List<Rule>, categories: List<Category>): Long {
        val incomeById = categories.associate { it.id to it.income }
        fun find(list: List<Rule>) = list
            .filter { incomeById[it.categoryId] == isIncome && matches(it.pattern, description) }
            .maxByOrNull { it.pattern.length }?.categoryId

        // 1. Las reglas que creaste tú mandan sobre todo lo demás
        find(rules.filter { !it.builtin })?.let { return it }
        // 2. Algunos tipos de operación ya dicen la categoría
        when (kind) {
            Kind.RETIRO -> return Cat.EFECTIVO
            Kind.NOMINA -> return Cat.SALARIO
            Kind.PAGO_TC -> return Cat.PAGO_TARJETA
            else -> {}
        }
        // 3. Palabras clave de comercios conocidos
        find(rules.filter { it.builtin })?.let { return it }
        // 4. Por defecto según el tipo
        return when {
            isIncome -> Cat.OTROS_INGRESOS
            kind == Kind.TRANSFERENCIA -> Cat.TRANSFERENCIAS
            kind == Kind.RECARGA -> Cat.SERVICIOS
            else -> Cat.OTROS_GASTOS
        }
    }
}

data class CategoryTotal(val category: Category, val cents: Long, val count: Int)

data class MonthSummary(
    val month: YearMonth,
    val incomeCents: Long,
    val expenseCents: Long,
    val expenseByCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
) {
    val balanceCents get() = incomeCents - expenseCents
    /** % del ingreso que no se gastó; null si no hubo ingresos */
    val savingsRate: Int? get() = if (incomeCents > 0) ((balanceCents * 100) / incomeCents).toInt() else null
}

object Stats {

    /** Movimientos que cuentan para ingresos/gastos (sin reversados ni pagos de tarjeta/entre cuentas). */
    fun counted(txs: List<Tx>, cats: Map<Long, Category>) =
        txs.filter { !it.excluded && cats[it.categoryId]?.excludeFromStats != true }

    fun summary(month: YearMonth, txs: List<Tx>, categories: List<Category>, zone: ZoneId = ZoneId.systemDefault()): MonthSummary {
        val cats = categories.associateBy { it.id }
        val inMonth = counted(txs, cats).filter { it.timeMillis.toYearMonth(zone) == month }
        fun group(list: List<Tx>) = list.groupBy { it.categoryId }
            .map { (id, l) -> CategoryTotal(cats[id] ?: Category(id, "Sin categoría", "❔", false), l.sumOf { it.amountCents }, l.size) }
            .sortedByDescending { it.cents }
        val (inc, exp) = inMonth.partition { it.isIncome }
        return MonthSummary(month, inc.sumOf { it.amountCents }, exp.sumOf { it.amountCents }, group(exp), group(inc))
    }

    /** Resumen de los últimos [n] meses terminando en [end], del más viejo al más nuevo. */
    fun series(end: YearMonth, n: Int, txs: List<Tx>, categories: List<Category>, zone: ZoneId = ZoneId.systemDefault()) =
        (n - 1 downTo 0).map { summary(end.minusMonths(it.toLong()), txs, categories, zone) }

    /** Gasto proyectado al cierre del mes con el ritmo diario actual. */
    fun projection(spentCents: Long, month: YearMonth, today: java.time.LocalDate): Long {
        if (YearMonth.from(today) != month) return spentCents
        val day = today.dayOfMonth.coerceAtLeast(1)
        return spentCents * month.lengthOfMonth() / day
    }

    fun topMerchants(month: YearMonth, txs: List<Tx>, categories: List<Category>, limit: Int = 5, zone: ZoneId = ZoneId.systemDefault()) =
        counted(txs, categories.associateBy { it.id })
            .filter { !it.isIncome && it.timeMillis.toYearMonth(zone) == month }
            .groupBy { it.description }
            .map { (d, l) -> Triple(d, l.sumOf { it.amountCents }, l.size) }
            .sortedByDescending { it.second }
            .take(limit)
}
