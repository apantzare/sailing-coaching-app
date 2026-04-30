package se.pantzare.startstop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun WindScreen(repository: Repository) {
    val readings = repository.data.windReadings
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val parsed = input.toDoubleOrNull()
        if (parsed == null || parsed < 0.0 || parsed > 360.0) {
            error = "Enter 0–360"
            return
        }
        repository.addWindReading(parsed)
        input = ""
        error = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it.filter { c -> c.isDigit() || c == '.' }
                    error = null
                },
                singleLine = true,
                label = { Text("Wind from °") },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Number,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { submit() }) { Text("Log") }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        readings.lastOrNull()?.let { latest ->
            Text(
                "Latest: ${cardinal(latest.directionDeg)} ${latest.directionDeg.roundToInt()}°  " +
                        "(${formatTime(latest.timestampMs)})",
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (readings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No wind readings yet.\nLog one above to start the graph.",
                    fontSize = 14.sp,
                )
            }
        } else {
            WindGraph(
                readings = readings,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp)
                    .weight(1f, fill = true),
            )
        }

        if (readings.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Readings (${readings.size})", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val sorted = readings.sortedByDescending { it.timestampMs }
                items(sorted, key = { it.id }) { r ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${cardinal(r.directionDeg)} ${r.directionDeg.roundToInt()}°",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                )
                                Text(formatTime(r.timestampMs), fontSize = 11.sp)
                            }
                            IconButton(onClick = { repository.deleteWindReading(r.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WindGraph(
    readings: List<WindReading>,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = onSurface.copy(alpha = 0.18f)
    val lineColor = MaterialTheme.colorScheme.primary
    val pointHalo = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val sorted = remember(readings) { readings.sortedBy { it.timestampMs } }
    val unwrapped = remember(sorted) { unwrapDirections(sorted.map { it.directionDeg }) }

    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier.onSizeChanged { size = it },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width == 0 || size.height == 0 || sorted.isEmpty()) return@Canvas

            val padLeft = with(density) { 44.dp.toPx() }
            val padRight = with(density) { 16.dp.toPx() }
            val padTop = with(density) { 12.dp.toPx() }
            val padBottom = with(density) { 28.dp.toPx() }
            val plotW = (size.width - padLeft - padRight).coerceAtLeast(1f)
            val plotH = (size.height - padTop - padBottom).coerceAtLeast(1f)

            // X-axis: unwrapped degrees. Pad ±5° if the range collapses.
            val rawMinX = unwrapped.min()
            val rawMaxX = unwrapped.max()
            val xPad = if (rawMaxX - rawMinX < 1e-6) 5.0 else (rawMaxX - rawMinX) * 0.1
            val minX = rawMinX - xPad
            val maxX = rawMaxX + xPad

            // Y-axis: time. Newest at bottom.
            val tMin = sorted.first().timestampMs
            val tMax = sorted.last().timestampMs
            val tRange = (tMax - tMin).coerceAtLeast(1L)

            fun xPx(deg: Double) = padLeft + ((deg - minX) / (maxX - minX) * plotW).toFloat()
            fun yPx(t: Long) = padTop + ((t - tMin).toDouble() / tRange.toDouble() * plotH).toFloat()

            val labelStyle = TextStyle(fontSize = 11.sp, color = onSurface)

            // Vertical gridlines + x-tick labels (compass degrees mod 360).
            val xStep = chooseDegreeStep(maxX - minX)
            var tick = floor(minX / xStep) * xStep
            while (tick <= maxX) {
                if (tick >= minX) {
                    val x = xPx(tick)
                    drawLine(
                        color = gridColor,
                        start = Offset(x, padTop),
                        end = Offset(x, padTop + plotH),
                        strokeWidth = 1f,
                    )
                    val deg = ((tick.roundToInt() % 360) + 360) % 360
                    val layout = textMeasurer.measure("${deg}°", labelStyle)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x - layout.size.width / 2f,
                            padTop + plotH + 4f,
                        ),
                    )
                }
                tick += xStep
            }

            // Horizontal gridlines + y-tick time labels.
            val yTicks = chooseTimeTicks(tMin, tMax, targetCount = 4)
            yTicks.forEach { t ->
                val y = yPx(t)
                drawLine(
                    color = gridColor,
                    start = Offset(padLeft, y),
                    end = Offset(padLeft + plotW, y),
                    strokeWidth = 1f,
                )
                val layout = textMeasurer.measure(formatTimeShort(t), labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        padLeft - layout.size.width - 6f,
                        y - layout.size.height / 2f,
                    ),
                )
            }

            // Axis frame.
            drawLine(
                color = onSurface,
                start = Offset(padLeft, padTop),
                end = Offset(padLeft, padTop + plotH),
                strokeWidth = 2f,
            )
            drawLine(
                color = onSurface,
                start = Offset(padLeft, padTop + plotH),
                end = Offset(padLeft + plotW, padTop + plotH),
                strokeWidth = 2f,
            )

            // Polyline through points.
            for (i in 1 until sorted.size) {
                drawLine(
                    color = lineColor,
                    start = Offset(xPx(unwrapped[i - 1]), yPx(sorted[i - 1].timestampMs)),
                    end = Offset(xPx(unwrapped[i]), yPx(sorted[i].timestampMs)),
                    strokeWidth = 3f,
                )
            }

            // Points on top.
            val pointR = with(density) { 4.dp.toPx() }
            for (i in sorted.indices) {
                val p = Offset(xPx(unwrapped[i]), yPx(sorted[i].timestampMs))
                drawCircle(color = pointHalo, radius = pointR + 2f, center = p)
                drawCircle(color = lineColor, radius = pointR, center = p)
            }

            // Axis titles.
            val xTitle = textMeasurer.measure(
                "Wind direction (°)",
                TextStyle(fontSize = 11.sp, color = onSurface, fontWeight = FontWeight.Medium),
            )
            drawText(
                textLayoutResult = xTitle,
                topLeft = Offset(
                    padLeft + plotW - xTitle.size.width,
                    padTop + plotH - xTitle.size.height - 2f,
                ),
            )
            // Y-axis title: small "time ↓" hint at the top of the axis.
            val yTitle = textMeasurer.measure(
                "time ↓",
                TextStyle(fontSize = 11.sp, color = onSurface, fontWeight = FontWeight.Medium),
            )
            drawText(
                textLayoutResult = yTitle,
                topLeft = Offset(2f, padTop - yTitle.size.height - 2f),
            )
        }
        // Outline so the canvas is visible against backgrounds.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = gridColor,
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()),
                style = Stroke(width = 1f),
            )
        }
    }
}

/**
 * Convert a sequence of compass directions (each in [0, 360)) into an unwrapped sequence:
 * each step is shifted by the multiple of 360 that minimizes |Δ|, so 355 → 005 plots as 355 → 365.
 */
internal fun unwrapDirections(input: List<Double>): List<Double> {
    if (input.isEmpty()) return emptyList()
    val out = ArrayList<Double>(input.size)
    out.add(input[0])
    for (i in 1 until input.size) {
        var delta = input[i] - input[i - 1]
        while (delta > 180.0) delta -= 360.0
        while (delta < -180.0) delta += 360.0
        out.add(out.last() + delta)
    }
    return out
}

private fun chooseDegreeStep(rangeDeg: Double): Double {
    val candidates = doubleArrayOf(1.0, 2.0, 5.0, 10.0, 15.0, 30.0, 45.0, 60.0, 90.0, 180.0)
    val target = rangeDeg / 6.0
    var best = candidates[0]
    var bestDiff = Double.MAX_VALUE
    for (c in candidates) {
        val d = abs(c - target)
        if (d < bestDiff) {
            bestDiff = d
            best = c
        }
    }
    return best
}

private fun chooseTimeTicks(tMin: Long, tMax: Long, targetCount: Int): List<Long> {
    if (tMax <= tMin) return listOf(tMin)
    val rangeMs = tMax - tMin
    val steps = longArrayOf(
        60_000L,             // 1 min
        5 * 60_000L,
        15 * 60_000L,
        30 * 60_000L,
        60 * 60_000L,        // 1 h
        2 * 60 * 60_000L,
        6 * 60 * 60_000L,
        12 * 60 * 60_000L,
        24 * 60 * 60_000L,
    )
    val target = rangeMs / targetCount.toLong()
    var step = steps[0]
    var bestDiff = Long.MAX_VALUE
    for (s in steps) {
        val d = abs(s - target)
        if (d < bestDiff) {
            bestDiff = d
            step = s
        }
    }
    val first = ceil(tMin.toDouble() / step) * step
    val out = mutableListOf<Long>()
    var t = first.toLong()
    while (t <= tMax) {
        out += t
        t += step
    }
    if (out.isEmpty()) out += tMin
    return out
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

private fun formatTimeShort(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
