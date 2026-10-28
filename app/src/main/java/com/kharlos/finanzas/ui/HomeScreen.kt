package com.kharlos.finanzas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kharlos.finanzas.data.Repo
import com.kharlos.finanzas.domain.Cat
import com.kharlos.finanzas.domain.Money
import com.kharlos.finanzas.domain.Stats
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onCategory: (Long) -> Unit,
    onPendingReview: () -> Unit,
) {
    val data = load(month) {
        val cats = Repo.db.categories()
        val (_, to) = month.range()
        val (prevFrom, _) = month.minusMonths(1).range()
        val txs = Repo.db.txBetween(prevFrom, to)
        val (from, _) = month.range()
        HomeData(
            summary = Stats.summary(month, txs, cats, Zone),
            prev = Stats.summary(month.minusMonths(1), txs, cats, Zone),
            budgets = Repo.db.budgets(),
            categories = cats.associateBy { it.id },
            pending = txs.count { !it.reviewed && it.timeMillis >= from },
        )
    }
    val s = data.summary

    LazyColumn {
        item { MonthSelector(month, onMonth) }

        item {
            Section {
                Text("Balance del mes", style = MaterialTheme.typography.labelLarge)
                Text(Money.format(s.balanceCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    color = if (s.balanceCents >= 0) IncomeColor else DangerColor)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Ingresos", style = MaterialTheme.typography.labelMedium)
                        Text(Money.format(s.incomeCents), color = IncomeColor, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Gastos", style = MaterialTheme.typography.labelMedium)
                        Text(Money.format(s.expenseCents), fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Ahorro", style = MaterialTheme.typography.labelMedium)
                        Text(s.savingsRate?.let { "$it%" } ?: "—", fontWeight = FontWeight.SemiBold)
                    }
                }
                val prevExp = data.prev.expenseCents
                if (prevExp > 0) {
                    val delta = ((s.expenseCents - prevExp) * 100 / prevExp).toInt()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (delta >= 0) "▲ $delta%" else "▼ ${-delta}%") + " en gastos vs ${month.minusMonths(1).short()} (${Money.format(prevExp)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (delta > 0) DangerColor else IncomeColor
                    )
                }
                if (month == YearMonth.now() && s.expenseCents > 0) {
                    val proj = Stats.projection(s.expenseCents, month, LocalDate.now())
                    Text("A este ritmo cerrarías el mes con ${Money.format(proj)} en gastos",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (data.pending > 0) {
            item {
                Section(modifier = Modifier.clickable(onClick = onPendingReview)) {
                    Text("⚠️ ${data.pending} movimiento(s) con formato nuevo por revisar", color = WarnColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (data.budgets.isNotEmpty()) {
            item {
                Section("Presupuestos") {
                    data.budgets.entries.sortedBy { if (it.key == Cat.TOTAL) -1 else it.key }.forEach { (catId, limit) ->
                        val spent = if (catId == Cat.TOTAL) s.expenseCents
                        else s.expenseByCategory.firstOrNull { it.category.id == catId }?.cents ?: 0L
                        val pct = (spent * 100 / limit).toInt()
                        val name = if (catId == Cat.TOTAL) "🎯 Total del mes"
                        else data.categories[catId]?.let { "${it.emoji} ${it.name}" } ?: "?"
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, Modifier.weight(1f))
                            Text("${Money.format(spent)} / ${Money.format(limit)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Bar(spent.toFloat() / limit, budgetColor(pct), Modifier.padding(top = 4.dp))
                        Text(
                            if (spent <= limit) "Quedan ${Money.format(limit - spent)}" else "Te pasaste ${Money.format(spent - limit)}",
                            style = MaterialTheme.typography.labelSmall, color = budgetColor(pct)
                        )
                    }
                }
            }
        }

        item {
            Section("Gastos por categoría") {
                if (s.expenseByCategory.isEmpty()) Text("Sin gastos este mes", style = MaterialTheme.typography.bodyMedium)
                s.expenseByCategory.forEach { ct ->
                    val share = if (s.expenseCents > 0) ct.cents.toFloat() / s.expenseCents else 0f
                    Column(Modifier.fillMaxWidth().clickable { onCategory(ct.category.id) }.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ct.category.emoji)
                            Box(Modifier.width(8.dp))
                            Text(ct.category.name, Modifier.weight(1f))
                            Text("${(share * 100).toInt()}%  ", style = MaterialTheme.typography.bodySmall)
                            Text(Money.format(ct.cents), fontWeight = FontWeight.SemiBold)
                        }
                        Bar(share, MaterialTheme.colorScheme.primary, Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        if (s.incomeByCategory.isNotEmpty()) {
            item {
                Section("Ingresos") {
                    s.incomeByCategory.forEach { ct ->
                        RowItem("${ct.category.emoji} ${ct.category.name}", Money.format(ct.cents), { onCategory(ct.category.id) }, IncomeColor)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

private data class HomeData(
    val summary: com.kharlos.finanzas.domain.MonthSummary,
    val prev: com.kharlos.finanzas.domain.MonthSummary,
    val budgets: Map<Long, Long>,
    val categories: Map<Long, com.kharlos.finanzas.domain.Category>,
    val pending: Int,
)
