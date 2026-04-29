package se.pantzare.startstop

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CourseScreen(repository: Repository, location: State<Location?>) {
    val measurements = repository.data.measurements
    val marks = repository.data.marks

    var showAdd by remember { mutableStateOf<MarkKind?>(null) }
    var showManage by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ActionBar(
            fixAvailable = location.value != null,
            markCount = marks.size,
            onAddMark = { showAdd = MarkKind.MARK },
            onAddBoat = { showAdd = MarkKind.BOAT },
            onManage = { showManage = true },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            if (measurements.isEmpty() && marks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No data yet.\nAdd marks/boats above or save a current measurement.",
                        fontSize = 16.sp,
                    )
                }
            } else {
                CourseCanvas(measurements = measurements, marks = marks)
            }
        }
    }

    showAdd?.let { kind ->
        AddMarkDialog(
            kind = kind,
            location = location.value,
            repository = repository,
            onDismiss = { showAdd = null },
        )
    }

    if (showManage) {
        ManageMarksDialog(
            marks = marks,
            onDelete = { repository.deleteMark(it) },
            onDismiss = { showManage = false },
        )
    }
}

@Composable
private fun ActionBar(
    fixAvailable: Boolean,
    markCount: Int,
    onAddMark: () -> Unit,
    onAddBoat: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onAddMark,
            enabled = fixAvailable,
            modifier = Modifier.weight(1f),
        ) { Text("+ Mark") }
        OutlinedButton(
            onClick = onAddBoat,
            enabled = fixAvailable,
            modifier = Modifier.weight(1f),
        ) { Text("+ Boat") }
        OutlinedButton(
            onClick = onManage,
            enabled = markCount > 0,
        ) { Text("List ($markCount)") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMarkDialog(
    kind: MarkKind,
    location: Location?,
    repository: Repository,
    onDismiss: () -> Unit,
) {
    val defaults = if (kind == MarkKind.MARK) DEFAULT_MARK_LABELS else DEFAULT_BOAT_LABELS
    val customs = if (kind == MarkKind.MARK) repository.data.customMarkLabels else repository.data.customBoatLabels

    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var showFreeText by remember { mutableStateOf(false) }
    var showAddNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == MarkKind.MARK) "Add mark" else "Add boat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (location != null) {
                    Text(
                        "Will record at current GPS (±${location.accuracy.roundToInt()} m)",
                        fontSize = 14.sp,
                    )
                } else {
                    Text("Waiting for GPS fix…", fontSize = 14.sp)
                }
                Text("Label", fontWeight = FontWeight.Medium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    defaults.forEach { label ->
                        FilterChip(
                            selected = selectedLabel == label,
                            onClick = { selectedLabel = label },
                            label = { Text(label) },
                        )
                    }
                    customs.forEach { label ->
                        FilterChip(
                            selected = selectedLabel == label,
                            onClick = { selectedLabel = label },
                            label = { Text(label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showFreeText = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("Free text") }
                    OutlinedButton(
                        onClick = { showAddNew = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("+ New") }
                }
                selectedLabel?.takeIf { it !in defaults && it !in customs }?.let {
                    Text("Selected: $it", fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val label = selectedLabel ?: return@TextButton
                    val loc = location ?: return@TextButton
                    repository.addMark(
                        CourseMark(
                            kind = kind,
                            label = label,
                            lat = loc.latitude,
                            lng = loc.longitude,
                            accuracyM = loc.accuracy.toDouble(),
                            timestampMs = System.currentTimeMillis(),
                        )
                    )
                    onDismiss()
                },
                enabled = selectedLabel != null && location != null,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showFreeText) {
        TextEntryDialogLocal(
            title = "Free text label",
            description = "Used for this mark only.",
            onDismiss = { showFreeText = false },
            onConfirm = {
                if (it.isNotBlank()) selectedLabel = it.trim()
                showFreeText = false
            },
        )
    }
    if (showAddNew) {
        TextEntryDialogLocal(
            title = "Add new label",
            description = "Saved for next time.",
            onDismiss = { showAddNew = false },
            onConfirm = {
                val trimmed = it.trim()
                if (trimmed.isNotEmpty()) {
                    if (kind == MarkKind.MARK) repository.addCustomMarkLabel(trimmed)
                    else repository.addCustomBoatLabel(trimmed)
                    selectedLabel = trimmed
                }
                showAddNew = false
            },
        )
    }
}

@Composable
private fun TextEntryDialogLocal(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, fontSize = 14.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ManageMarksDialog(
    marks: List<CourseMark>,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marks & boats") },
        text = {
            if (marks.isEmpty()) {
                Text("None yet.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(marks, key = { it.id }) { m ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${if (m.kind == MarkKind.MARK) "Mark" else "Boat"}: ${m.label}",
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        "±${m.accuracyM.roundToInt()} m",
                                        fontSize = 12.sp,
                                    )
                                }
                                IconButton(onClick = { onDelete(m.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun CourseCanvas(measurements: List<SavedMeasurement>, marks: List<CourseMark>) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val measurementColor = MaterialTheme.colorScheme.primary
    val markColor = Color(0xFFFF9800) // orange
    val boatColor = Color(0xFF455A64) // slate gray

    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val canvasW = size.width
        val canvasH = size.height
        val padding = 60f

        // Combine all positions for bounding box.
        val lats = measurements.map { it.startLat } + marks.map { it.lat }
        val lngs = measurements.map { it.startLng } + marks.map { it.lng }
        if (lats.isEmpty()) return@Canvas

        val minLat = lats.min()
        val maxLat = lats.max()
        val minLng = lngs.min()
        val maxLng = lngs.max()
        val midLat = (minLat + maxLat) / 2
        val midLng = (minLng + maxLng) / 2
        val midLatRad = Math.toRadians(midLat)

        val metersPerDegLat = 111_320.0
        val metersPerDegLng = 111_320.0 * cos(midLatRad)

        val widthMeters = ((maxLng - minLng) * metersPerDegLng).coerceAtLeast(50.0)
        val heightMeters = ((maxLat - minLat) * metersPerDegLat).coerceAtLeast(50.0)

        val availW = canvasW - padding * 2
        val availH = canvasH - padding * 2
        val scale = min(availW / widthMeters, availH / heightMeters).toFloat()

        fun project(lat: Double, lng: Double): Offset {
            val xMeters = (lng - midLng) * metersPerDegLng
            val yMeters = (lat - midLat) * metersPerDegLat
            val x = canvasW / 2f + (xMeters * scale).toFloat()
            val y = canvasH / 2f - (yMeters * scale).toFloat()
            return Offset(x, y)
        }

        val maxKnots = measurements.maxOfOrNull { it.knots }?.coerceAtLeast(0.01) ?: 1.0
        val maxArrowPx = with(density) { 80.dp.toPx() }
        val minArrowPx = with(density) { 16.dp.toPx() }
        val measureRadius = with(density) { 5.dp.toPx() }
        val markRadius = with(density) { 8.dp.toPx() }
        val labelStyle = TextStyle(fontSize = 12.sp, color = onSurface)
        val velocityStyle = TextStyle(fontSize = 11.sp, color = onSurface)

        // Marks first (under the arrows).
        marks.forEach { m ->
            val pos = project(m.lat, m.lng)
            when (m.kind) {
                MarkKind.MARK -> {
                    drawCircle(color = surface, radius = markRadius + 2f, center = pos)
                    drawCircle(color = markColor, radius = markRadius, center = pos)
                }
                MarkKind.BOAT -> {
                    val w = with(density) { 22.dp.toPx() }
                    val h = with(density) { 10.dp.toPx() }
                    drawRect(
                        color = surface,
                        topLeft = Offset(pos.x - w / 2 - 2f, pos.y - h / 2 - 2f),
                        size = Size(w + 4f, h + 4f),
                    )
                    drawRect(
                        color = boatColor,
                        topLeft = Offset(pos.x - w / 2, pos.y - h / 2),
                        size = Size(w, h),
                    )
                }
            }
            val labelLayout = textMeasurer.measure(m.label, labelStyle)
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(pos.x + markRadius + 4f, pos.y - labelLayout.size.height - 2f),
            )
        }

        // Measurements with arrows on top.
        measurements.forEach { mm ->
            val pos = project(mm.startLat, mm.startLng)
            val ratio = (mm.knots / maxKnots).toFloat().coerceIn(0f, 1f)
            val arrowLen = (minArrowPx + ratio * (maxArrowPx - minArrowPx))
            val bearingRad = Math.toRadians(mm.bearingDeg)
            val tipX = pos.x + (sin(bearingRad) * arrowLen).toFloat()
            val tipY = pos.y - (cos(bearingRad) * arrowLen).toFloat()

            drawLine(
                color = measurementColor,
                start = pos,
                end = Offset(tipX, tipY),
                strokeWidth = 3f,
            )
            val barbAngle = Math.toRadians(28.0)
            val barbLen = with(density) { 8.dp.toPx() }
            val b = bearingRad
            val barb1 = Offset(
                tipX - (sin(b + barbAngle) * barbLen).toFloat(),
                tipY + (cos(b + barbAngle) * barbLen).toFloat(),
            )
            val barb2 = Offset(
                tipX - (sin(b - barbAngle) * barbLen).toFloat(),
                tipY + (cos(b - barbAngle) * barbLen).toFloat(),
            )
            drawLine(measurementColor, Offset(tipX, tipY), barb1, strokeWidth = 3f)
            drawLine(measurementColor, Offset(tipX, tipY), barb2, strokeWidth = 3f)

            drawCircle(color = surface, radius = measureRadius + 2f, center = pos)
            drawCircle(color = measurementColor, radius = measureRadius, center = pos)

            val labelLayout = textMeasurer.measure(mm.positionLabel, labelStyle)
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(pos.x + measureRadius + 4f, pos.y - labelLayout.size.height - 2f),
            )
            val vText = "${"%.2f".format(mm.knots)} kn"
            val vLayout = textMeasurer.measure(vText, velocityStyle)
            drawText(
                textLayoutResult = vLayout,
                topLeft = Offset(pos.x + measureRadius + 4f, pos.y + 2f),
            )
        }

        // North arrow top-right.
        val northX = canvasW - padding / 2
        val northY = padding / 2 + 24f
        val northLen = with(density) { 24.dp.toPx() }
        drawLine(
            color = onSurface,
            start = Offset(northX, northY + northLen),
            end = Offset(northX, northY),
            strokeWidth = 3f,
        )
        val barbAng = Math.toRadians(30.0)
        val barb = with(density) { 8.dp.toPx() }
        drawLine(
            color = onSurface,
            start = Offset(northX, northY),
            end = Offset(northX - sin(barbAng).toFloat() * barb, northY + cos(barbAng).toFloat() * barb),
            strokeWidth = 3f,
        )
        drawLine(
            color = onSurface,
            start = Offset(northX, northY),
            end = Offset(northX + sin(barbAng).toFloat() * barb, northY + cos(barbAng).toFloat() * barb),
            strokeWidth = 3f,
        )
        val nLabel = textMeasurer.measure(
            "N",
            TextStyle(fontSize = 14.sp, color = onSurface, textAlign = TextAlign.Center),
        )
        drawText(
            textLayoutResult = nLabel,
            topLeft = Offset(northX - nLabel.size.width / 2f, northY - nLabel.size.height - 2f),
        )

        // Scale bar bottom-left.
        val scaleBarMeters = chooseScaleBarLength(availW.toDouble() / scale)
        val scaleBarPx = (scaleBarMeters * scale).toFloat()
        val sbX = padding / 2
        val sbY = canvasH - padding / 2
        drawLine(color = onSurface, start = Offset(sbX, sbY), end = Offset(sbX + scaleBarPx, sbY), strokeWidth = 3f)
        drawLine(color = onSurface, start = Offset(sbX, sbY - 6f), end = Offset(sbX, sbY + 6f), strokeWidth = 3f)
        drawLine(
            color = onSurface,
            start = Offset(sbX + scaleBarPx, sbY - 6f),
            end = Offset(sbX + scaleBarPx, sbY + 6f),
            strokeWidth = 3f,
        )
        val sbLabel = textMeasurer.measure(
            formatMeters(scaleBarMeters),
            TextStyle(fontSize = 12.sp, color = onSurface),
        )
        drawText(
            textLayoutResult = sbLabel,
            topLeft = Offset(sbX, sbY - sbLabel.size.height - 8f),
        )
    }
}

private fun chooseScaleBarLength(availWidthMeters: Double): Double {
    val target = availWidthMeters * 0.25
    val candidates = doubleArrayOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0)
    var best = candidates[0]
    var bestDiff = Double.MAX_VALUE
    for (c in candidates) {
        val diff = kotlin.math.abs(c - target)
        if (diff < bestDiff) {
            bestDiff = diff
            best = c
        }
    }
    return best
}

private fun formatMeters(m: Double): String {
    return if (m >= 1000) "${(m / 1000).roundToInt()} km" else "${m.roundToInt()} m"
}
