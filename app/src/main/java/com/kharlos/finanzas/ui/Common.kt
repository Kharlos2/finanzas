package com.kharlos.finanzas.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kharlos.finanzas.data.Repo
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val ES = Locale("es", "CO")
val Zone: ZoneId get() = ZoneId.systemDefault()

val IncomeColor = Color(0xFF2E7D32)
val WarnColor = Color(0xFFF9A825)
val DangerColor = Color(0xFFC62828)

/** Recalcula [block] cada vez que cambia la base de datos o alguna de las [keys]. */
@Composable
fun <T> load(vararg keys: Any?, block: () -> T): T {
    val v by Repo.version.collectAsState()
    return remember(v, *keys) { block() }
}

fun YearMonth.range(): Pair<Long, Long> =
    atDay(1).atStartOfDay(Zone).toInstant().toEpochMilli() to plusMonths(1).atDay(1).atStartOfDay(Zone).toInstant().toEpochMilli()

fun YearMonth.label(): String = month.getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() } + " " + year
fun YearMonth.short(): String = month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')

fun Long.toLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), Zone)
fun LocalDateTime.toMillis(): Long = atZone(Zone).toInstant().toEpochMilli()
val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", ES)
val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", ES)
val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", ES)

@Composable
fun MonthSelector(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.Default.KeyboardArrowLeft, "Mes anterior") }
        Text(month.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = month < YearMonth.now()) {
            Icon(Icons.Default.KeyboardArrowRight, "Mes siguiente")
        }
    }
}

@Composable
fun Section(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Box(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

fun budgetColor(pct: Int) = when { pct >= 100 -> DangerColor; pct >= 80 -> WarnColor; else -> IncomeColor }

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp).clip(shape).clickable(onClick = onClick)
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RowItem(label: String, value: String, onClick: (() -> Unit)? = null, valueColor: Color = Color.Unspecified) {
    Row(
        (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyLarge)
    }
}

fun pickDate(ctx: Context, initial: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
    DatePickerDialog(ctx, { _, y, m, d -> onPicked(initial.withYear(y).withMonth(m + 1).withDayOfMonth(d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

fun pickTime(ctx: Context, initial: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPicked(initial.withHour(h).withMinute(m)) }, initial.hour, initial.minute, true).show()
}
