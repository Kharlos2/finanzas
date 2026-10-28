package com.kharlos.finanzas.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kharlos.finanzas.data.Repo
import com.kharlos.finanzas.domain.Goal
import com.kharlos.finanzas.domain.Money
import com.kharlos.finanzas.domain.Stats
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun GoalsScreen(openNew: Boolean, onOpenedNew: () -> Unit) {
    val goals = load { Repo.db.goals() }
    val monthBalance = load {
        Stats.summary(YearMonth.now(), Repo.db.txBetween(YearMonth.now().range().first, YearMonth.now().range().second),
            Repo.db.categories(), Zone).balanceCents
    }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var moving by remember { mutableStateOf<Pair<Goal, Boolean>?>(null) } // (meta, true=abonar)
    // El botón + de la pantalla principal abre una meta nueva
    LaunchedEffect(openNew) {
        if (openNew) { editing = Goal(name = "", emoji = "🎯", targetCents = 0, savedCents = 0); onOpenedNew() }
    }

    LazyColumn {
        item {
            Section {
                Text("Disponible este mes (ingresos − gastos)", style = MaterialTheme.typography.labelMedium)
                Text(Money.format(monthBalance), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = if (monthBalance >= 0) IncomeColor else DangerColor)
                Text("Puedes abonar parte de esto a tus metas.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (goals.isEmpty()) item {
            Text("Aún no tienes metas. Crea una con el botón +.", Modifier.padding(24.dp))
        }
        items(goals, key = { it.id }) { g ->
            val pct = if (g.targetCents > 0) (g.savedCents * 100 / g.targetCents).toInt() else 0
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${g.emoji} ${g.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { editing = g }) { Text("Editar") }
                }
                Text("${Money.format(g.savedCents)} de ${Money.format(g.targetCents)} · $pct%", style = MaterialTheme.typography.bodyMedium)
                Bar(g.savedCents.toFloat() / g.targetCents.coerceAtLeast(1), if (pct >= 100) IncomeColor else MaterialTheme.colorScheme.primary,
                    Modifier.padding(vertical = 6.dp))
                val left = (g.targetCents - g.savedCents).coerceAtLeast(0)
                if (pct >= 100) Text("🎉 ¡Meta cumplida!", color = IncomeColor)
                else g.deadlineMillis?.let { dl ->
                    val end = dl.toLocal().toLocalDate()
                    val months = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(end)).coerceAtLeast(0) + 1
                    Text("Faltan ${Money.format(left)} hasta ${end.format(dateFmt)} → ${Money.format(left / months)}/mes",
                        style = MaterialTheme.typography.bodySmall)
                } ?: Text("Faltan ${Money.format(left)}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { moving = g to true }) { Text("Abonar") }
                    OutlinedButton(onClick = { moving = g to false }) { Text("Retirar") }
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }

    editing?.let { g -> GoalDialog(g) { editing = null } }
    moving?.let { (g, add) -> MoveDialog(g, add) { moving = null } }
}

@Composable
private fun GoalDialog(goal: Goal, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(goal.name) }
    var emoji by remember { mutableStateOf(goal.emoji) }
    var target by remember { mutableStateOf(if (goal.targetCents > 0) (goal.targetCents / 100).toString() else "") }
    var deadline by remember { mutableStateOf(goal.deadlineMillis) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (goal.id == 0L) "Nueva meta" else "Editar meta") },
        text = {
            Column {
                Row {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, Modifier.weight(0.25f), label = { Text("Ícono") }, singleLine = true)
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(name, { name = it }, Modifier.weight(0.75f), label = { Text("Nombre") }, singleLine = true)
                }
                OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Monto objetivo") },
                    prefix = { Text("$") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val init = deadline?.toLocal() ?: LocalDate.now().plusMonths(6).atStartOfDay()
                        pickDate(ctx, init) { deadline = it.toMillis() }
                    }) { Text(deadline?.let { "Fecha: " + it.toLocal().format(dateFmt) } ?: "Fecha límite (opcional)") }
                    if (deadline != null) TextButton(onClick = { deadline = null }) { Text("Quitar") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = Money.parseInput(target)
                if (name.isBlank() || cents == null) {
                    Toast.makeText(ctx, "Pon nombre y monto", Toast.LENGTH_SHORT).show()
                } else {
                    Repo.db.saveGoal(goal.copy(name = name.trim(), emoji = emoji.ifBlank { "🎯" }, targetCents = cents, deadlineMillis = deadline))
                    Repo.changed(); onDone()
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (goal.id != 0L) TextButton(onClick = { Repo.db.deleteGoal(goal.id); Repo.changed(); onDone() }) { Text("Eliminar", color = DangerColor) }
                TextButton(onClick = onDone) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun MoveDialog(goal: Goal, add: Boolean, onDone: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text((if (add) "Abonar a " else "Retirar de ") + goal.name) },
        text = {
            OutlinedTextField(amount, { amount = it }, label = { Text("Monto") }, prefix = { Text("$") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        },
        confirmButton = {
            TextButton(onClick = {
                Money.parseInput(amount)?.let { c ->
                    val saved = (goal.savedCents + if (add) c else -c).coerceAtLeast(0)
                    Repo.db.saveGoal(goal.copy(savedCents = saved)); Repo.changed()
                }
                onDone()
            }) { Text("Listo") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } },
    )
}

