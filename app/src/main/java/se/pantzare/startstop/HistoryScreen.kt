package se.pantzare.startstop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(repository: Repository) {
    val measurements = repository.data.measurements
    var editing by remember { mutableStateOf<SavedMeasurement?>(null) }

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
            MeasurementRow(
                m = m,
                onEdit = { editing = m },
                onDelete = { repository.deleteMeasurement(m.id) },
            )
        }
    }

    editing?.let { m ->
        EditLabelDialog(
            measurement = m,
            repository = repository,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MeasurementRow(m: SavedMeasurement, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (m.positionLabel.isEmpty()) {
                    Text(
                        "(unlabeled)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                    )
                } else {
                    Text(m.positionLabel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
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
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit label")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditLabelDialog(
    measurement: SavedMeasurement,
    repository: Repository,
    onDismiss: () -> Unit,
) {
    val customLabels = repository.data.customLabels
    var text by remember { mutableStateOf(measurement.positionLabel) }
    var showAddNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Label") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        repository.updateMeasurementLabel(measurement.id, text)
                        onDismiss()
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Quick pick", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DEFAULT_LABELS.forEach { label ->
                        FilterChip(
                            selected = text == label,
                            onClick = { text = label },
                            label = { Text(label) },
                        )
                    }
                    customLabels.forEach { label ->
                        FilterChip(
                            selected = text == label,
                            onClick = { text = label },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showAddNew = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("+ New label") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                repository.updateMeasurementLabel(measurement.id, text)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showAddNew) {
        AddLabelDialog(
            onDismiss = { showAddNew = false },
            onConfirm = { value ->
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) {
                    repository.addCustomLabel(trimmed)
                    text = trimmed
                }
                showAddNew = false
            },
        )
    }
}

@Composable
private fun AddLabelDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add new label") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                label = { Text("Label") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(input) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatTimestamp(ms: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(ms))
}
