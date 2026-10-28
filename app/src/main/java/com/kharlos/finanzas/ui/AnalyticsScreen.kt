package com.kharlos.finanzas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.kharlos.finanzas.data.Repo
import com.kharlos.finanzas.domain.Money
import com.kharlos.finanzas.domain.MonthSummary
import com.kharlos.finanzas.domain.Stats
import java.time.YearMonth

@Composable
fun AnalyticsScreen(month: YearMonth, onMonth: (YearMonth) -> Unit, onCategory: (Long) -> Unit) {
    val data = load(month) {
        val cats = Repo.db.categories()
        val (from, _) = month.minusMonths(5).range()
        val (_, to) = month.range()
        val txs = Repo.db.txBetween(from, to)
        Triple(Stats.series(month, 6, txs, cats, Zone), Stats.topMerchants(month, txs, cats, 5, Zone), cats)
    }
    val (series, top, _) = data
    val current = series.last()
    val prev = series[series.size - 2]
    val history = series.dropLast(1).takeLast(3).filter { it.expenseCents > 0 }

    LazyColumn {
        item { MonthSelector(month, onMonth) }

        item {
            Section("Ingresos vs gastos (6 meses)") {
                BarsChart(series)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(IncomeColor, "Ingresos"); Spacer(Modifier.width(16.dp)); Legend(MaterialTheme.colorScheme.primary, "Gastos")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Cell("Mes", 1f, true); Cell("Ingresos", 1.4f, true); Cell("Gastos", 1.4f, true); Cell("Balance", 1.4f, true)
                }
                series.reversed().forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Cell(s.month.short() + " " + (s.month.year % 100), 1f)
                        Cell(Money.format(s.incomeCents), 1.4f)
                        Cell(Money.format(s.expenseCents), 1.4f)
                        Cell(Money.format(s.balanceCents), 1.4f, color = if (s.balanceCents >= 0) IncomeColor else DangerColor)
                    }
                }
            }
        }

        item {
            Section("${month.short()} vs ${prev.month.short()} por categoría") {
                val ids = (current.expenseByCategory.map { it.category } + prev.expenseByCategory.map { it.category }).distinctBy { it.id }
                if (ids.isEmpty()) Text("Sin datos", style = MaterialTheme.typography.bodyMedium)
                else Row(Modifier.fillMaxWidth()) {
                    Cell("Categoría", 1.6f, true); Cell(month.short(), 1.1f, true); Cell(prev.month.short(), 1.1f, true)
                    Cell("Cambio", 0.8f, true); Cell("Prom 3m", 1.1f, true)
                }
                ids.sortedByDescending { c -> current.expenseByCategory.firstOrNull { it.category.id == c.id }?.cents ?: 0L }.forEach { c ->
                    val now = current.expenseByCategory.firstOrNull { it.category.id == c.id }?.cents ?: 0L
                    val before = prev.expenseByCategory.firstOrNull { it.category.id == c.id }?.cents ?: 0L
                    val avg = if (history.isEmpty()) 0L
                    else history.sumOf { h -> h.expenseByCategory.firstOrNull { it.category.id == c.id }?.cents ?: 0L } / history.size
                    val delta = when {
                        before == 0L && now > 0 -> "nuevo"
                        before == 0L -> "—"
                        else -> ((now - before) * 100 / before).let { if (it >= 0) "▲$it%" else "▼${-it}%" }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Cell("${c.emoji} ${c.name}", 1.6f, onClick = { onCategory(c.id) })
                        Cell(Money.format(now), 1.1f)
                        Cell(Money.format(before), 1.1f)
                        Cell(delta, 0.8f, color = when {
                            delta.startsWith("▲") || delta == "nuevo" -> DangerColor
                            delta.startsWith("▼") -> IncomeColor
                            else -> Color.Unspecified
                        })
                        Cell(if (avg > 0) Money.format(avg) else "—", 1.1f)
                    }
                }
            }
        }

        item {
            Section("Indicadores de ${month.short()}") {
                val days = if (month == YearMonth.now()) java.time.LocalDate.now().dayOfMonth else month.lengthOfMonth()
                RowItem("Gasto diario promedio", Money.format(current.expenseCents / days.coerceAtLeast(1)))
                val count = current.expenseByCategory.sumOf { it.count }
                RowItem("Número de gastos", "$count")
                if (count > 0) RowItem("Ticket promedio", Money.format(current.expenseCents / count))
                RowItem("Tasa de ahorro", current.savingsRate?.let { "$it%" } ?: "—")
                if (history.isNotEmpty()) {
                    val avg = history.sumOf { it.expenseCents } / history.size
                    RowItem("Gasto promedio 3 meses previos", Money.format(avg))
                }
            }
        }

        item {
            Section("Dónde más gastaste") {
                if (top.isEmpty()) Text("Sin gastos", style = MaterialTheme.typography.bodyMedium)
                top.forEach { (desc, cents, n) -> RowItem("$desc  ×$n", Money.format(cents)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BarsChart(series: List<MonthSummary>) {
    val max = series.maxOf { maxOf(it.incomeCents, it.expenseCents) }.coerceAtLeast(1)
    val incomeColor = IncomeColor
    val expenseColor = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val slot = size.width / series.size
        val barW = slot * 0.3f
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        series.forEachIndexed { i, s ->
            val x = i * slot + slot * 0.15f
            val hi = size.height * s.incomeCents / max
            val he = size.height * s.expenseCents / max
            drawRoundRect(incomeColor, Offset(x, size.height - hi), Size(barW, hi), CornerRadius(6f, 6f))
            drawRoundRect(expenseColor, Offset(x + barW + slot * 0.05f, size.height - he), Size(barW, he), CornerRadius(6f, 6f))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        series.forEach { Text(it.month.short(), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(
    text: String, weight: Float, header: Boolean = false, color: Color = Color.Unspecified, onClick: (() -> Unit)? = null,
) {
    Text(
        text,
        Modifier.weight(weight).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (header) FontWeight.SemiBold else null,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

