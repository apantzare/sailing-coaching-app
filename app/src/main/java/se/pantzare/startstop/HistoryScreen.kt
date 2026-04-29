package se.pantzare.startstop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(repository: Repository) {
    val measurements = repository.data.measurements
    if (measurements.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No measurements saved yet.\nTake one on the Measure tab.",
                fontSize = 16.sp,
            )
        }
        return
    }

    val sorted = measurements.sortedByDescending { it.timestampMs }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sorted, key = { it.id }) { m ->
            MeasurementRow(m, onDelete = { repository.deleteMeasurement(m.id) })
        }
    }
}

@Composable
private fun MeasurementRow(m: SavedMeasurement, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.positionLabel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(formatTimestamp(m.timestampMs), fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "${cardinal(m.bearingDeg)} ${m.bearingDeg.roundToInt()}° ± ${m.bearingSigmaDeg.roundToInt()}°",
                    fontSize = 14.sp,
                )
                Text(
                    "${"%.2f".format(m.knots)} ± ${"%.2f".format(m.knotsSigma)} knots  •  " +
                            "${"%.1f".format(m.mPerMin)} ± ${"%.1f".format(m.mPerMinSigma)} m/min",
                    fontSize = 14.sp,
                )
                Text("elapsed ${formatElapsed(m.elapsedS)}", fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(ms))
}
