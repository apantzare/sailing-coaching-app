package se.pantzare.startstop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CourseScreen(repository: Repository) {
    val measurements = repository.data.measurements
    if (measurements.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No measurements yet.\nSave a few on the Measure tab and they'll plot here.",
                fontSize = 16.sp,
            )
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val arrowColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val canvasW = size.width
            val canvasH = size.height
            val padding = 60f

            // Bounding box from start positions only (where the measurement was taken).
            val lats = measurements.map { it.startLat }
            val lngs = measurements.map { it.startLng }
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
            val scale = min(availW / widthMeters, availH / heightMeters).toFloat() // px per meter

            fun project(lat: Double, lng: Double): Offset {
                val xMeters = (lng - midLng) * metersPerDegLng
                val yMeters = (lat - midLat) * metersPerDegLat
                val x = canvasW / 2f + (xMeters * scale).toFloat()
                val y = canvasH / 2f - (yMeters * scale).toFloat()
                return Offset(x, y)
            }

            val maxKnots = measurements.maxOf { it.knots }.coerceAtLeast(0.01)
            val maxArrowPx = with(density) { 80.dp.toPx() }
            val minArrowPx = with(density) { 16.dp.toPx() }
            val dotRadius = with(density) { 5.dp.toPx() }
            val labelStyle = TextStyle(
                fontSize = 12.sp,
                color = onSurface,
            )
            val velocityStyle = TextStyle(
                fontSize = 11.sp,
                color = onSurface,
            )

            measurements.forEach { m ->
                val pos = project(m.startLat, m.startLng)
                val ratio = (m.knots / maxKnots).toFloat().coerceIn(0f, 1f)
                val arrowLen = (minArrowPx + ratio * (maxArrowPx - minArrowPx))
                val bearingRad = Math.toRadians(m.bearingDeg)
                val tipX = pos.x + (sin(bearingRad) * arrowLen).toFloat()
                val tipY = pos.y - (cos(bearingRad) * arrowLen).toFloat()

                // shaft
                drawLine(
                    color = arrowColor,
                    start = pos,
                    end = Offset(tipX, tipY),
                    strokeWidth = 3f,
                )

                // arrowhead barbs
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
                drawLine(arrowColor, Offset(tipX, tipY), barb1, strokeWidth = 3f)
                drawLine(arrowColor, Offset(tipX, tipY), barb2, strokeWidth = 3f)

                // dot at start position
                drawCircle(color = surface, radius = dotRadius + 2f, center = pos)
                drawCircle(color = dotColor, radius = dotRadius, center = pos)

                // label
                val labelLayout = textMeasurer.measure(m.positionLabel, labelStyle)
                drawText(
                    textLayoutResult = labelLayout,
                    topLeft = Offset(pos.x + dotRadius + 4f, pos.y - labelLayout.size.height - 2f),
                )

                // velocity caption
                val vText = "${"%.2f".format(m.knots)} kn"
                val vLayout = textMeasurer.measure(vText, velocityStyle)
                drawText(
                    textLayoutResult = vLayout,
                    topLeft = Offset(pos.x + dotRadius + 4f, pos.y + 2f),
                )
            }

            // North arrow in top-right corner
            val northX = canvasW - padding / 2
            val northY = padding / 2 + 24f
            val northLen = with(density) { 24.dp.toPx() }
            drawLine(
                color = onSurface,
                start = Offset(northX, northY + northLen),
                end = Offset(northX, northY),
                strokeWidth = 3f,
            )
            // arrowhead
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
            val nLabel = textMeasurer.measure("N", TextStyle(fontSize = 14.sp, color = onSurface, textAlign = TextAlign.Center))
            drawText(
                textLayoutResult = nLabel,
                topLeft = Offset(northX - nLabel.size.width / 2f, northY - nLabel.size.height - 2f),
            )

            // Scale bar in bottom-left
            val scaleBarMeters = chooseScaleBarLength(availW.toDouble() / scale)
            val scaleBarPx = (scaleBarMeters * scale).toFloat()
            val sbX = padding / 2
            val sbY = canvasH - padding / 2
            drawLine(
                color = onSurface,
                start = Offset(sbX, sbY),
                end = Offset(sbX + scaleBarPx, sbY),
                strokeWidth = 3f,
            )
            drawLine(color = onSurface, start = Offset(sbX, sbY - 6f), end = Offset(sbX, sbY + 6f), strokeWidth = 3f)
            drawLine(
                color = onSurface,
                start = Offset(sbX + scaleBarPx, sbY - 6f),
                end = Offset(sbX + scaleBarPx, sbY + 6f),
                strokeWidth = 3f,
            )
            val sbLabel = textMeasurer.measure("${formatMeters(scaleBarMeters)}", TextStyle(fontSize = 12.sp, color = onSurface))
            drawText(
                textLayoutResult = sbLabel,
                topLeft = Offset(sbX, sbY - sbLabel.size.height - 8f),
            )
        }
    }
}

private fun chooseScaleBarLength(availWidthMeters: Double): Double {
    val targetFraction = 0.25 // bar should be ~25% of canvas width
    val target = availWidthMeters * targetFraction
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
