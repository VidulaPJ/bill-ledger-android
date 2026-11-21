package com.vidula.billledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 24.dp, bottom = 10.dp))

@Composable
fun LedgerCard(modifier: Modifier = Modifier, dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val l = LocalLedger.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (dark) l.ink else l.card).padding(18.dp),
        content = content
    )
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, onDark: Boolean = true) {
    val l = LocalLedger.current
    Column(modifier) {
        Text(value, color = if (onDark) l.onInk else l.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = (if (onDark) l.onInk else l.mute).copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
    }
}

/** One horizontal share bar: name, track, value. */
@Composable
fun Strip(name: String, fraction: Float, value: String, sub: String, highlight: Boolean, onClick: (() -> Unit)? = null) {
    val l = LocalLedger.current
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 48.dp).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontWeight = FontWeight.SemiBold)
            Text("  $sub", color = l.mute, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)).background(l.panel)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.01f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(if (highlight) l.red else l.bar))
        }
    }
}

/** Vertical bars with the value above and the label below; the last bar is red. */
@Composable
fun BarChart(labels: List<String>, values: List<Double>, valueText: (Double) -> String, modifier: Modifier = Modifier, height: Int = 190) {
    val l = LocalLedger.current
    val max = (values.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
    val dense = values.size > 6
    Row(modifier.fillMaxWidth().height(height.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val last = i == values.lastIndex
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (!dense || last || i % 2 == values.size % 2)
                    Text(valueText(v), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                Box(Modifier.width(28.dp).fillMaxHeight((v / max * 0.78).toFloat().coerceAtLeast(0.005f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (last) l.red else l.bar))
                Text(labels[i], fontSize = 11.sp, color = l.mute, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun ListRow(left: String, right: String, sub: String? = null, bold: Boolean = false) {
    val l = LocalLedger.current
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(left, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = l.mute, style = MaterialTheme.typography.labelMedium)
        }
        Text(right, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
    HorizontalDivider(color = l.line)
}

@Composable
fun Empty(text: String) = Text(text, color = LocalLedger.current.mute, modifier = Modifier.padding(vertical = 18.dp))
