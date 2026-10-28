package com.kharlos.finanzas.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kharlos.finanzas.data.Repo
import com.kharlos.finanzas.data.kindLabel
import com.kharlos.finanzas.domain.Cat
import com.kharlos.finanzas.domain.Category
import com.kharlos.finanzas.domain.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(hasSmsPermission: Boolean, hasNotifPermission: Boolean, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cats = load { Repo.db.categories() }
    val budgets = load { Repo.db.budgets() }
    val rules = load { Repo.db.rules() }
    val bigExpense = load { Repo.bigExpenseCents }
    val notifyEach = load { Repo.notifyEachTx }

    var importing by remember { mutableStateOf(false) }
    var budgetFor by remember { mutableStateOf<Long?>(null) }
    var editCat by remember { mutableStateOf<Category?>(null) }
    var newRule by remember { mutableStateOf(false) }
    var showBuiltin by remember { mutableStateOf(false) }
    var editBig by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) {
                val names = Repo.db.categories().associate { it.id to it.name }
                val txs = Repo.db.allTx()
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                    w.write("fecha,tipo,monto,descripcion,categoria,banco,producto,operacion,origen,excluido,nota\n")
                    txs.sortedBy { it.timeMillis }.forEach { t ->
                        val cols = listOf(
                            t.timeMillis.toLocal().toString(), if (t.isIncome) "ingreso" else "gasto",
                            (t.amountCents / 100.0).toString(), t.description, names[t.categoryId] ?: "", t.bank,
                            t.product ?: "", kindLabel(t.kind), t.source, if (t.excluded) "si" else "no", t.note,
                        )
                        w.write(cols.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" } + "\n")
                    }
                }
                txs.size
            }
            Toast.makeText(ctx, "Exportados $n movimientos", Toast.LENGTH_SHORT).show()
        }
    }

    fun doImport(months: Long) {
        importing = true
        scope.launch {
            val from = LocalDate.now().minusMonths(months).withDayOfMonth(1).atStartOfDay().toMillis()
            val n = withContext(Dispatchers.IO) { runCatching { Repo.importInbox(from) } }
            importing = false
            Toast.makeText(ctx, n.fold({ "Importados $it movimientos nuevos" }, { "No se pudo leer la bandeja: ${it.message}" }), Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn {
        item {
            Section("Permisos") {
                RowItem("Leer SMS", if (hasSmsPermission) "✅" else "❌")
                RowItem("Notificaciones", if (hasNotifPermission) "✅" else "❌")
                if (!hasSmsPermission || !hasNotifPermission) {
                    Button(onClick = onRequestPermissions, Modifier.fillMaxWidth()) { Text("Conceder permisos") }
                    Text("Si Android dice \"Ajuste restringido\": Ajustes del teléfono › Apps › Finanzas › ⋮ › Permitir ajustes restringidos.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item {
            Section("Importar SMS antiguos") {
                Text("Lee la bandeja y registra los SMS de Bancolombia y Caja Social que no estén guardados. No duplica.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (importing) Text("Importando…")
                else FlowRow {
                    listOf(1L to "Este mes", 3L to "3 meses", 6L to "6 meses", 12L to "1 año").forEach { (m, label) ->
                        Chip(label, false) { if (hasSmsPermission) doImport(if (m == 1L) 0L else m) else onRequestPermissions() }
                    }
                }
            }
        }
        item {
            Section("Presupuestos mensuales") {
                Text("Te aviso al llegar al 80% y al pasar el 100%.", style = MaterialTheme.typography.bodySmall)
                RowItem("🎯 Total del mes", budgets[Cat.TOTAL]?.let { Money.format(it) } ?: "Sin tope", { budgetFor = Cat.TOTAL })
                cats.filter { !it.income && !it.excludeFromStats }.forEach { c ->
                    RowItem("${c.emoji} ${c.name}", budgets[c.id]?.let { Money.format(it) } ?: "—", { budgetFor = c.id })
                }
            }
        }
        item {
            Section("Alertas") {
                RowItem("Avisar gasto mayor a", Money.format(bigExpense), { editBig = true })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Notificar cada movimiento leído", Modifier.weight(1f))
                    Switch(notifyEach, { Repo.notifyEachTx = it })
                }
            }
        }
        item {
            Section("Categorías") {
                cats.forEach { c ->
                    RowItem("${c.emoji} ${c.name}", (if (c.income) "ingreso" else "gasto") + if (c.excludeFromStats) " · no cuenta" else "", { editCat = c })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editCat = Category(name = "", emoji = "🏷️", income = false) }) { Text("+ Gasto") }
                    OutlinedButton(onClick = { editCat = Category(name = "", emoji = "💰", income = true) }) { Text("+ Ingreso") }
                }
            }
        }
        item {
            Section("Reglas de categorización") {
                Text("Si la descripción contiene el texto, el movimiento va a esa categoría. Las tuyas mandan sobre las predeterminadas.",
                    style = MaterialTheme.typography.bodySmall)
                val byId = cats.associateBy { it.id }
                rules.filter { !it.builtin }.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\"${r.pattern}\" → ${byId[r.categoryId]?.let { "${it.emoji} ${it.name}" } ?: "?"}", Modifier.weight(1f))
                        IconButton(onClick = { Repo.db.deleteRule(r.id); Repo.changed() }) { Icon(Icons.Default.Delete, "Borrar regla") }
                    }
                }
                OutlinedButton(onClick = { newRule = true }, Modifier.padding(top = 4.dp)) { Text("+ Nueva regla") }
                val builtin = rules.filter { it.builtin }
                Text(if (showBuiltin) "Ocultar predeterminadas" else "Ver ${builtin.size} predeterminadas",
                    Modifier.clickable { showBuiltin = !showBuiltin }.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
                if (showBuiltin) builtin.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\"${r.pattern.trim()}\" → ${byId[r.categoryId]?.name ?: "?"}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { Repo.db.deleteRule(r.id); Repo.changed() }) { Icon(Icons.Default.Delete, "Borrar regla") }
                    }
                }
            }
        }
        item {
            Section("Datos") {
                Text("Los datos viven solo en este teléfono. Exporta un CSV de vez en cuando como respaldo.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { exporter.launch("finanzas-${LocalDate.now()}.csv") }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Exportar CSV")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    budgetFor?.let { id ->
        AmountDialog(
            title = if (id == Cat.TOTAL) "Tope total del mes" else "Presupuesto: ${cats.firstOrNull { it.id == id }?.name}",
            initial = budgets[id], allowEmpty = true,
            onDismiss = { budgetFor = null },
        ) { cents -> Repo.db.setBudget(id, cents); Repo.changed(); budgetFor = null }
    }
    if (editBig) AmountDialog("Alerta de gasto grande", bigExpense, false, { editBig = false }) { c ->
        if (c != null) Repo.bigExpenseCents = c; editBig = false
    }
    editCat?.let { c -> CategoryDialog(c) { editCat = null } }
    if (newRule) RuleDialog(cats) { newRule = false }
}

@Composable
private fun AmountDialog(title: String, initial: Long?, allowEmpty: Boolean, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { (it / 100).toString() } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, prefix = { Text("$") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (allowEmpty) Text("Déjalo vacío para quitarlo", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(Money.parseInput(text)) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CategoryDialog(cat: Category, onDone: () -> Unit) {
    var name by remember { mutableStateOf(cat.name) }
    var emoji by remember { mutableStateOf(cat.emoji) }
    var exclude by remember { mutableStateOf(cat.excludeFromStats) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (cat.id == 0L) "Nueva categoría de ${if (cat.income) "ingreso" else "gasto"}" else "Editar categoría") },
        text = {
            Column {
                Row {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, Modifier.weight(0.25f), label = { Text("Ícono") }, singleLine = true)
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(name, { name = it }, Modifier.weight(0.75f), label = { Text("Nombre") }, singleLine = true)
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("No contar en análisis")
                        Text("Ej. pagos de tarjeta o pasar plata entre tus cuentas", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(exclude, { exclude = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    Repo.db.saveCategory(cat.copy(name = name.trim(), emoji = emoji.ifBlank { "🏷️" }, excludeFromStats = exclude))
                    Repo.changed()
                }
                onDone()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleDialog(cats: List<Category>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var pattern by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Nueva regla") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(pattern, { pattern = it }, label = { Text("Si la descripción contiene…") }, singleLine = true)
                Text("…va a la categoría:", Modifier.padding(vertical = 8.dp))
                FlowRow { cats.forEach { c -> Chip("${c.emoji} ${c.name}", c.id == catId) { catId = c.id } } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val id = catId
                if (pattern.isNotBlank() && id != null) {
                    val income = cats.first { it.id == id }.income
                    val n = Repo.addRuleAndApply(pattern, id, income)
                    Toast.makeText(ctx, "$n movimiento(s) recategorizados", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }) { Text("Crear y aplicar") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } },
    )
}
