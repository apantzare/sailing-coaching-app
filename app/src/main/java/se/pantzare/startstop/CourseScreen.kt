package se.pantzare.startstop

import android.location.Location
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
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
    val windFromDeg = repository.data.windFromDeg

    var showAdd by remember { mutableStateOf<MarkKind?>(null) }
    var showManage by remember { mutableStateOf(false) }
    var showWind by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ActionBar(
            fixAvailable = location.value != null,
            markCount = marks.size,
            onAddMark = { showAdd = MarkKind.MARK },
            onAddBoat = { showAdd = MarkKind.BOAT },
            onManage = { showManage = true },
        )
        WindBar(
            windFromDeg = windFromDeg,
            onEdit = { showWind = true },
        )
        Text(
            "Drag marks/boats to reposition. Measurements stay at their GPS location.",
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
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
                CourseCanvas(
                    measurements = measurements,
                    marks = marks,
                    windFromDeg = windFromDeg,
                    onMarkMoved = { id, lat, lng ->
                        repository.updateMarkPosition(id, lat, lng)
                    },
                )
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

    if (showWind) {
        WindDialog(
            initialDeg = windFromDeg,
            onDismiss = { showWind = false },
            onSave = {
                repository.setWindDirection(it)
                showWind = false
            },
        )
    }
}

@Composable
private fun WindBar(windFromDeg: Double?, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (windFromDeg == null) "Wind: not set"
            else "Wind from ${cardinal(windFromDeg)} ${windFromDeg.roundToInt()}°",
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onEdit) {
            Text(if (windFromDeg == null) "Set wind" else "Edit")
        }
    }
}

@Composable
private fun WindDialog(
    initialDeg: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    var text by remember { mutableStateOf(initialDeg?.roundToInt()?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wind direction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Compass bearing the wind is coming FROM (0–360°). " +
                            "The course view rotates so this direction is at the top.",
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { c -> c.isDigit() || c == '.' }
                        error = null
                    },
                    singleLine = true,
                    label = { Text("Degrees from north") },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = text.toDoubleOrNull()
                if (parsed == null || parsed < 0.0 || parsed > 360.0) {
                    error = "Enter a number between 0 and 360"
                } else {
                    onSave(parsed)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null) }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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
                        "Will record at current GPS (±${location.accuracy.roundToInt()} m). " +
                                "You can drag it on the canvas afterwards.",
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
                    val label = selectedLabel
                    val loc = location
                    if (label != null && loc != null) {
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
                    }
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
                                    Text("±${m.accuracyM.roundToInt()} m original GPS", fontSize = 12.sp)
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

private data class ProjectionParams(
    val canvasW: Float,
    val canvasH: Float,
    val midLat: Double,
    val midLng: Double,
    val metersPerDegLat: Double,
    val metersPerDegLng: Double,
    val scale: Float,
    val rotationDeg: Double,
) {
    private val rotRad get() = Math.toRadians(rotationDeg)

    fun project(lat: Double, lng: Double): Offset {
        val xMeters = (lng - midLng) * metersPerDegLng
        val yMeters = (lat - midLat) * metersPerDegLat
        val cosR = cos(rotRad)
        val sinR = sin(rotRad)
        val xMRot = xMeters * cosR - yMeters * sinR
        val yMRot = xMeters * sinR + yMeters * cosR
        return Offset(
            canvasW / 2f + (xMRot * scale).toFloat(),
            canvasH / 2f - (yMRot * scale).toFloat(),
        )
    }

    fun unproject(p: Offset): Pair<Double, Double> {
        val xMRot = (p.x - canvasW / 2f) / scale
        val yMRot = (canvasH / 2f - p.y) / scale
        val cosR = cos(rotRad)
        val sinR = sin(rotRad)
        val xM = xMRot * cosR + yMRot * sinR
        val yM = -xMRot * sinR + yMRot * cosR
        val lng = midLng + xM / metersPerDegLng
        val lat = midLat + yM / metersPerDegLat
        return lat to lng
    }
}

private fun computeProjection(
    canvasSize: IntSize,
    measurements: List<SavedMeasurement>,
    marks: List<CourseMark>,
    innerPadding: Float,
    rotationDeg: Double,
): ProjectionParams? {
    val lats = measurements.map { it.startLat } + marks.map { it.lat }
    val lngs = measurements.map { it.startLng } + marks.map { it.lng }
    if (lats.isEmpty() || canvasSize.width == 0 || canvasSize.height == 0) return null

    val minLat = lats.min()
    val maxLat = lats.max()
    val minLng = lngs.min()
    val maxLng = lngs.max()
    val midLat = (minLat + maxLat) / 2
    val midLng = (minLng + maxLng) / 2
    val midLatRad = Math.toRadians(midLat)

    val metersPerDegLat = 111_320.0
    val metersPerDegLng = 111_320.0 * cos(midLatRad)

    val rotRad = Math.toRadians(rotationDeg)
    val cosR = cos(rotRad)
    val sinR = sin(rotRad)

    val rotatedXs = mutableListOf<Double>()
    val rotatedYs = mutableListOf<Double>()
    measurements.forEach {
        val xm = (it.startLng - midLng) * metersPerDegLng
        val ym = (it.startLat - midLat) * metersPerDegLat
        rotatedXs += xm * cosR - ym * sinR
        rotatedYs += xm * sinR + ym * cosR
    }
    marks.forEach {
        val xm = (it.lng - midLng) * metersPerDegLng
        val ym = (it.lat - midLat) * metersPerDegLat
        rotatedXs += xm * cosR - ym * sinR
        rotatedYs += xm * sinR + ym * cosR
    }
    val widthMeters = (rotatedXs.max() - rotatedXs.min()).coerceAtLeast(50.0)
    val heightMeters = (rotatedYs.max() - rotatedYs.min()).coerceAtLeast(50.0)

    val availW = (canvasSize.width - innerPadding * 2).coerceAtLeast(1f)
    val availH = (canvasSize.height - innerPadding * 2).coerceAtLeast(1f)
    val scale = min(availW / widthMeters, availH / heightMeters).toFloat()

    return ProjectionParams(
        canvasW = canvasSize.width.toFloat(),
        canvasH = canvasSize.height.toFloat(),
        midLat = midLat,
        midLng = midLng,
        metersPerDegLat = metersPerDegLat,
        metersPerDegLng = metersPerDegLng,
        scale = scale,
        rotationDeg = rotationDeg,
    )
}

@Composable
private fun CourseCanvas(
    measurements: List<SavedMeasurement>,
    marks: List<CourseMark>,
    windFromDeg: Double?,
    onMarkMoved: (id: String, lat: Double, lng: Double) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val measurementColor = MaterialTheme.colorScheme.primary
    val markColor = Color(0xFFFF9800)
    val boatColor = Color(0xFF455A64)
    val windColor = Color(0xFF1976D2)
    val committeeFlagColor = Color(0xFFFF6F00)
    val finishFlagColor = Color(0xFF1565C0)

    val innerPadPx = 60f
    val hitRadiusPx = with(density) { 28.dp.toPx() }
    val rotationDeg = windFromDeg ?: 0.0

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val projection = remember(canvasSize, measurements, marks, rotationDeg) {
        computeProjection(canvasSize, measurements, marks, innerPadPx, rotationDeg)
    }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var currentTouchPos by remember { mutableStateOf(Offset.Zero) }
    var anchorOffset by remember { mutableStateOf(Offset.Zero) }

    val marksRef = rememberUpdatedState(marks)
    val projRef = rememberUpdatedState(projection)
    val onMarkMovedRef = rememberUpdatedState(onMarkMoved)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val proj = projRef.value
                        val mks = marksRef.value
                        if (proj != null) {
                            val hit = mks.minByOrNull { m ->
                                (proj.project(m.lat, m.lng) - offset).getDistance()
                            }
                            if (hit != null) {
                                val mp = proj.project(hit.lat, hit.lng)
                                if ((mp - offset).getDistance() <= hitRadiusPx) {
                                    draggingId = hit.id
                                    currentTouchPos = offset
                                    anchorOffset = mp - offset
                                }
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (draggingId != null) {
                            currentTouchPos += dragAmount
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        val id = draggingId
                        val proj = projRef.value
                        if (id != null && proj != null) {
                            val finalPos = currentTouchPos + anchorOffset
                            val (lat, lng) = proj.unproject(finalPos)
                            onMarkMovedRef.value(id, lat, lng)
                        }
                        draggingId = null
                        currentTouchPos = Offset.Zero
                        anchorOffset = Offset.Zero
                    },
                    onDragCancel = {
                        draggingId = null
                        currentTouchPos = Offset.Zero
                        anchorOffset = Offset.Zero
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val proj = projection ?: return@Canvas

            val maxKnots = measurements.maxOfOrNull { it.knots }?.coerceAtLeast(0.01) ?: 1.0
            val maxArrowPx = with(density) { 80.dp.toPx() }
            val minArrowPx = with(density) { 16.dp.toPx() }
            val measureRadius = with(density) { 5.dp.toPx() }
            val markRadius = with(density) { 8.dp.toPx() }
            val labelStyle = TextStyle(fontSize = 12.sp, color = onSurface)
            val velocityStyle = TextStyle(fontSize = 11.sp, color = onSurface)

            // Marks first (under the arrows).
            marks.forEach { m ->
                val isDragging = m.id == draggingId
                val pos = if (isDragging) currentTouchPos + anchorOffset else proj.project(m.lat, m.lng)
                val highlightBoost = if (isDragging) 4f else 0f
                when (m.kind) {
                    MarkKind.MARK -> {
                        drawCircle(color = surface, radius = markRadius + 2f + highlightBoost, center = pos)
                        drawCircle(color = markColor, radius = markRadius + highlightBoost, center = pos)
                        val markFlagColor = when (m.label) {
                            "Pin end" -> committeeFlagColor
                            "Finish line" -> finishFlagColor
                            else -> null
                        }
                        if (markFlagColor != null) {
                            val poleH = with(density) { 22.dp.toPx() }
                            val flagW = with(density) { 12.dp.toPx() }
                            val flagH = with(density) { 9.dp.toPx() }
                            val poleX = pos.x
                            val poleBaseY = pos.y - markRadius - highlightBoost
                            val poleTopY = poleBaseY - poleH
                            drawLine(
                                color = onSurface,
                                start = Offset(poleX, poleBaseY),
                                end = Offset(poleX, poleTopY),
                                strokeWidth = 2f,
                            )
                            val flagPath = Path().apply {
                                moveTo(poleX, poleTopY)
                                lineTo(poleX + flagW, poleTopY + flagH * 0.4f)
                                lineTo(poleX, poleTopY + flagH)
                                close()
                            }
                            drawPath(
                                path = flagPath,
                                color = surface,
                                style = Stroke(width = 3f, join = StrokeJoin.Round),
                            )
                            drawPath(path = flagPath, color = markFlagColor)
                        }
                    }
                    MarkKind.BOAT -> {
                        val length = with(density) { 30.dp.toPx() } + highlightBoost
                        val beam = with(density) { 12.dp.toPx() } + highlightBoost
                        // Boats always point head-to-wind. The canvas is already rotated so
                        // wind-from is at the top, so bow up = bow into the wind.
                        val flagColor = when (m.label) {
                            "Committee boat" -> committeeFlagColor
                            "Finish boat" -> finishFlagColor
                            else -> null
                        }

                        rotate(degrees = 0f, pivot = pos) {
                            val bowY = pos.y - length * 0.5f
                            val shoulderY = pos.y - length * 0.25f
                            val sternY = pos.y + length * 0.5f
                            val cabinHalf = beam * 0.28f
                            val cabinY1 = pos.y - length * 0.05f
                            val cabinY2 = pos.y + length * 0.22f

                            val hull = Path().apply {
                                moveTo(pos.x, bowY)
                                lineTo(pos.x + beam / 2f, shoulderY)
                                lineTo(pos.x + beam / 2f, sternY)
                                lineTo(pos.x - beam / 2f, sternY)
                                lineTo(pos.x - beam / 2f, shoulderY)
                                close()
                            }
                            // Outline halo for contrast.
                            drawPath(
                                path = hull,
                                color = surface,
                                style = Stroke(width = 5f, join = StrokeJoin.Round),
                            )
                            drawPath(path = hull, color = boatColor)

                            // Cabin highlight.
                            drawRect(
                                color = surface.copy(alpha = 0.55f),
                                topLeft = Offset(pos.x - cabinHalf, cabinY1),
                                size = Size(cabinHalf * 2f, cabinY2 - cabinY1),
                            )

                            // Mast + flag (only for committee / finish boats).
                            if (flagColor != null) {
                                val mastTopY = bowY - length * 0.45f
                                val mastBaseY = pos.y - length * 0.05f
                                drawLine(
                                    color = onSurface,
                                    start = Offset(pos.x, mastBaseY),
                                    end = Offset(pos.x, mastTopY),
                                    strokeWidth = 2f,
                                )
                                val flagW = beam * 0.9f
                                val flagH = length * 0.22f
                                val flagPath = Path().apply {
                                    moveTo(pos.x, mastTopY)
                                    lineTo(pos.x + flagW, mastTopY + flagH * 0.3f)
                                    lineTo(pos.x, mastTopY + flagH)
                                    close()
                                }
                                drawPath(
                                    path = flagPath,
                                    color = surface,
                                    style = Stroke(width = 3f, join = StrokeJoin.Round),
                                )
                                drawPath(path = flagPath, color = flagColor)
                            }
                        }
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
                val pos = proj.project(mm.startLat, mm.startLng)
                val ratio = (mm.knots / maxKnots).toFloat().coerceIn(0f, 1f)
                val arrowLen = (minArrowPx + ratio * (maxArrowPx - minArrowPx))
                val bearingRad = Math.toRadians(mm.bearingDeg + 180.0 - rotationDeg)
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

            // North indicator: arrow pointing where actual north is (relative to rotation).
            val rotRad = Math.toRadians(rotationDeg)
            val northDirX = (-sin(rotRad)).toFloat()
            val northDirY = (-cos(rotRad)).toFloat()
            val northCx = proj.canvasW - 36f
            val northCy = 40f
            val northLen = with(density) { 22.dp.toPx() }
            val northTipX = northCx + northDirX * northLen
            val northTipY = northCy + northDirY * northLen
            drawLine(
                color = onSurface,
                start = Offset(northCx - northDirX * 4f, northCy - northDirY * 4f),
                end = Offset(northTipX, northTipY),
                strokeWidth = 3f,
            )
            val barbAng = Math.toRadians(30.0)
            val barb = with(density) { 7.dp.toPx() }
            // Compute perpendicular for barbs (rotated relative to arrow direction)
            val arrowAng = kotlin.math.atan2(northDirY.toDouble(), northDirX.toDouble())
            drawLine(
                color = onSurface,
                start = Offset(northTipX, northTipY),
                end = Offset(
                    northTipX - (cos(arrowAng - barbAng) * barb).toFloat(),
                    northTipY - (sin(arrowAng - barbAng) * barb).toFloat(),
                ),
                strokeWidth = 3f,
            )
            drawLine(
                color = onSurface,
                start = Offset(northTipX, northTipY),
                end = Offset(
                    northTipX - (cos(arrowAng + barbAng) * barb).toFloat(),
                    northTipY - (sin(arrowAng + barbAng) * barb).toFloat(),
                ),
                strokeWidth = 3f,
            )
            val nLabel = textMeasurer.measure(
                "N",
                TextStyle(fontSize = 14.sp, color = onSurface, textAlign = TextAlign.Center),
            )
            drawText(
                textLayoutResult = nLabel,
                topLeft = Offset(
                    northTipX + northDirX * 12f - nLabel.size.width / 2f,
                    northTipY + northDirY * 12f - nLabel.size.height / 2f,
                ),
            )

            // Wind indicator at top center, points downward (wind comes from the top).
            if (windFromDeg != null) {
                val wcx = proj.canvasW / 2f
                val wTopY = 14f
                val wBottomY = wTopY + with(density) { 28.dp.toPx() }
                drawLine(
                    color = windColor,
                    start = Offset(wcx, wTopY),
                    end = Offset(wcx, wBottomY),
                    strokeWidth = 4f,
                )
                val wBarb = with(density) { 7.dp.toPx() }
                drawLine(
                    color = windColor,
                    start = Offset(wcx, wBottomY),
                    end = Offset(wcx - wBarb, wBottomY - wBarb),
                    strokeWidth = 4f,
                )
                drawLine(
                    color = windColor,
                    start = Offset(wcx, wBottomY),
                    end = Offset(wcx + wBarb, wBottomY - wBarb),
                    strokeWidth = 4f,
                )
                val wLabel = textMeasurer.measure(
                    "Wind ${windFromDeg.roundToInt()}°",
                    TextStyle(fontSize = 12.sp, color = windColor, fontWeight = FontWeight.Medium),
                )
                drawText(
                    textLayoutResult = wLabel,
                    topLeft = Offset(wcx + 10f, wTopY),
                )
            }

            // Scale bar bottom-left.
            val availWMeters = (proj.canvasW - innerPadPx * 2) / proj.scale
            val scaleBarMeters = chooseScaleBarLength(availWMeters.toDouble())
            val scaleBarPx = (scaleBarMeters * proj.scale).toFloat()
            val sbX = innerPadPx / 2
            val sbY = proj.canvasH - innerPadPx / 2
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
