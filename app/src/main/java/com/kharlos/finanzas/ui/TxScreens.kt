package com.kharlos.finanzas.ui

import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kharlos.finanzas.data.Repo
import com.kharlos.finanzas.data.kindLabel
import com.kharlos.finanzas.domain.Cat
import com.kharlos.finanzas.domain.Categorizer
import com.kharlos.finanzas.domain.Money
import com.kharlos.finanzas.domain.Tx
import java.time.LocalDateTime
import java.time.YearMonth

/** Filtro de la lista: categoría concreta, "por revisar" o todo */
const val FILTER_REVIEW = -1L

@Composable
fun TxListScreen(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    filter: Long?,
    onFilter: (Long?) -> Unit,
    onOpen: (Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val data = load(month) {
        val (from, to) = month.range()
        Repo.db.txBetween(from, to) to Repo.db.categories().associateBy { it.id }
    }
    val (all, cats) = data
    val shown = all.filter { t ->
        (filter == null || (filter == FILTER_REVIEW && !t.reviewed) || t.categoryId == filter) &&
            (query.isBlank() || Categorizer.matches(query, t.description) || t.description.contains(query, true))
    }
    val usedCats = all.map { it.categoryId }.distinct().mapNotNull { cats[it] }.sortedBy { it.name }

    LazyColumn(Modifier.fillMaxSize()) {
        item { MonthSelector(month, onMonth) }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Buscar comercio o descripción") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        item {
            LazyRow(Modifier.padding(start = 16.dp, top = 8.dp)) {
                item { Chip("Todos", filter == null) { onFilter(null) } }
                if (all.any { !it.reviewed }) item { Chip("⚠️ Por revisar", filter == FILTER_REVIEW) { onFilter(FILTER_REVIEW) } }
                items(usedCats) { c -> Chip("${c.emoji} ${c.name}", filter == c.id) { onFilter(if (filter == c.id) null else c.id) } }
            }
        }
        item {
            val counted = shown.filter { !it.excluded && cats[it.categoryId]?.excludeFromStats != true }
            val out = counted.filter { !it.isIncome }.sumOf { it.amountCents }
            val inc = counted.filter { it.isIncome }.sumOf { it.amountCents }
            Text("${shown.size} movimientos · gastos ${Money.format(out)} · ingresos ${Money.format(inc)}",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (shown.isEmpty()) item {
            Text("No hay movimientos. Importa tus SMS desde Ajustes o registra uno con +.",
                Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium)
        }
        shown.groupBy { it.timeMillis.toLocal().toLocalDate() }.forEach { (day, list) ->
            item(key = "d$day") {
                val dayOut = list.filter { !it.isIncome && !it.excluded && cats[it.categoryId]?.excludeFromStats != true }.sumOf { it.amountCents }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)) {
                    Text(day.format(dayFmt).replaceFirstChar { it.uppercase() }, Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (dayOut > 0) Text("−${Money.format(dayOut)}", style = MaterialTheme.typography.labelMedium)
                }
            }
            items(list, key = { it.id }) { t ->
                val c = cats[t.categoryId]
                Row(Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(c?.emoji ?: "❔", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text((if (!t.reviewed) "⚠️ " else "") + t.description, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (t.excluded) TextDecoration.LineThrough else null)
                        Text(listOfNotNull(c?.name, kindLabel(t.kind), t.product, t.timeMillis.toLocal().format(timeFmt)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(Money.signed(t.amountCents, t.isIncome), fontWeight = FontWeight.SemiBold,
                        color = if (t.isIncome) IncomeColor else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (t.excluded) TextDecoration.LineThrough else null)
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

private val BANKS = listOf("Bancolombia", "Caja Social", "Nequi", "Efectivo", "Otro")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxEditScreen(txId: Long?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val original = remember(txId) { txId?.let { Repo.db.txById(it) } }
    val categories = remember { Repo.db.categories() }

    var isIncome by remember { mutableStateOf(original?.isIncome ?: false) }
    var amount by remember { mutableStateOf(original?.let { (it.amountCents / 100).toString() } ?: "") }
    var description by remember { mutableStateOf(original?.description ?: "") }
    var categoryId by remember { mutableStateOf(original?.categoryId ?: Cat.OTROS_GASTOS) }
    var bank by remember { mutableStateOf(original?.bank ?: "Caja Social") }
    var dateTime by remember { mutableStateOf(original?.timeMillis?.toLocal() ?: LocalDateTime.now().withSecond(0).withNano(0)) }
    var note by remember { mutableStateOf(original?.note ?: "") }
    var excluded by remember { mutableStateOf(original?.excluded ?: false) }
    var makeRule by remember { mutableStateOf(false) }
    var rulePattern by remember { mutableStateOf(original?.description ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val categoryChanged = original != null && categoryId != original.categoryId
    val visibleCats = categories.filter { it.income == isIncome }

    fun save() {
        val cents = Money.parseInput(amount)
        if (cents == null) { Toast.makeText(ctx, "Escribe un monto válido", Toast.LENGTH_SHORT).show(); return }
        if (description.isBlank()) { Toast.makeText(ctx, "Escribe una descripción", Toast.LENGTH_SHORT).show(); return }
        val cat = if (categories.firstOrNull { it.id == categoryId }?.income == isIncome) categoryId
        else if (isIncome) Cat.OTROS_INGRESOS else Cat.OTROS_GASTOS
        val tx = (original ?: Tx(timeMillis = 0, amountCents = 0, isIncome = false, bank = bank, kind = "MANUAL",
            description = "", categoryId = cat)).copy(
            timeMillis = dateTime.toMillis(), amountCents = cents, isIncome = isIncome, bank = bank,
            description = description.trim(), categoryId = cat, note = note.trim(), excluded = excluded, reviewed = true,
        )
        Repo.saveManual(tx)
        if (categoryChanged && makeRule && rulePattern.isNotBlank()) {
            val n = Repo.addRuleAndApply(rulePattern, cat, isIncome)
            Toast.makeText(ctx, "Regla creada: $n movimiento(s) recategorizados", Toast.LENGTH_SHORT).show()
        }
        onClose()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (original == null) "Nuevo movimiento" else "Editar movimiento") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Volver") } },
            actions = {
                if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar") }
            }
        )
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (original != null && original.source == "SMS") {
                Text("Leído de SMS · ${original.bank}${original.product?.let { " · $it" } ?: ""} · ${kindLabel(original.kind)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!original.reviewed) Text("Formato no reconocido del todo: revisa monto, fecha y descripción.",
                    style = MaterialTheme.typography.bodySmall, color = WarnColor)
                Spacer(Modifier.height(8.dp))
            }
            Row {
                Chip("Gasto", !isIncome) { isIncome = false; if (categories.firstOrNull { it.id == categoryId }?.income == true) categoryId = Cat.OTROS_GASTOS }
                Chip("Ingreso", isIncome) { isIncome = true; if (categories.firstOrNull { it.id == categoryId }?.income == false) categoryId = Cat.OTROS_INGRESOS }
            }
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Monto (COP)") },
                singleLine = true, prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descripción / comercio") }, singleLine = true)

            Text("Categoría", Modifier.padding(top = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
            FlowRow {
                visibleCats.forEach { c -> Chip("${c.emoji} ${c.name}", c.id == categoryId) { categoryId = c.id } }
            }
            if (categoryChanged) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(makeRule, { makeRule = it })
                    Text("Aplicar siempre a movimientos que contengan:", style = MaterialTheme.typography.bodyMedium)
                }
                if (makeRule) OutlinedTextField(rulePattern, { rulePattern = it }, Modifier.fillMaxWidth(), singleLine = true,
                    supportingText = { Text("Recorta el texto a lo que identifica al comercio o cuenta, ej. WHOOSH o 3017413746") })
            }

            Text("Banco / medio", Modifier.padding(top = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
            FlowRow { (BANKS + listOfNotNull(bank.takeIf { it !in BANKS })).forEach { b -> Chip(b, b == bank) { bank = b } } }

            Text("Fecha", Modifier.padding(top = 16.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(ctx, dateTime) { dateTime = it } }) { Text(dateTime.format(dateFmt)) }
                OutlinedButton(onClick = { pickTime(ctx, dateTime) { dateTime = it } }) { Text(dateTime.format(timeFmt)) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Nota (opcional)") })

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("No contar en análisis")
                    Text("Para reversos o movimientos duplicados", style = MaterialTheme.typography.bodySmall)
                }
                Switch(excluded, { excluded = it })
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Button(onClick = { save() }, Modifier.fillMaxWidth()) { Text("Guardar") }
        }
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar movimiento?") },
            text = { Text("${original.description} · ${Money.format(original.amountCents)}") },
            confirmButton = { TextButton(onClick = { Repo.deleteTx(original.id); confirmDelete = false; onClose() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
