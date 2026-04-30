package se.pantzare.startstop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

val DEFAULT_LABELS = listOf(
    "Gate",
    "Upwind mark",
    "Starting line",
    "Starboard layline",
    "Under startline",
)

val DEFAULT_MARK_LABELS = listOf(
    "Upwind mark",
    "Pin end",
    "Finish line",
    "Gate left",
    "Gate right",
    "Reaching mark",
    "Wing mark",
)

val DEFAULT_BOAT_LABELS = listOf(
    "Committee boat",
    "Finish boat",
    "Coach boat",
    "Mark boat",
)

@Serializable
enum class MarkKind { MARK, BOAT }

@Serializable
data class CourseMark(
    val id: String = UUID.randomUUID().toString(),
    val kind: MarkKind,
    val label: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val timestampMs: Long,
)

@Serializable
data class SavedMeasurement(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long,
    val positionLabel: String,
    val startLat: Double,
    val startLng: Double,
    val startAccuracyM: Double,
    val stopLat: Double,
    val stopLng: Double,
    val stopAccuracyM: Double,
    val distanceM: Double,
    val distanceSigmaM: Double,
    val bearingDeg: Double,
    val bearingSigmaDeg: Double,
    val mPerMin: Double,
    val mPerMinSigma: Double,
    val knots: Double,
    val knotsSigma: Double,
    val elapsedS: Double,
)

@Serializable
data class WindReading(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long,
    val directionDeg: Double,
)

@Serializable
data class AppData(
    val measurements: List<SavedMeasurement> = emptyList(),
    val customLabels: List<String> = emptyList(),
    val marks: List<CourseMark> = emptyList(),
    val customMarkLabels: List<String> = emptyList(),
    val customBoatLabels: List<String> = emptyList(),
    val windFromDeg: Double? = null,
    val windReadings: List<WindReading> = emptyList(),
)

class Repository private constructor(private val file: File) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    var data by mutableStateOf(loadInitial())
        private set

    private fun loadInitial(): AppData {
        if (!file.exists()) return AppData()
        return runCatching { json.decodeFromString<AppData>(file.readText()) }
            .getOrElse { AppData() }
    }

    private fun persist() {
        runCatching { file.writeText(json.encodeToString(data)) }
    }

    fun addMeasurement(m: SavedMeasurement) {
        data = data.copy(measurements = data.measurements + m)
        persist()
    }

    fun deleteMeasurement(id: String) {
        data = data.copy(measurements = data.measurements.filterNot { it.id == id })
        persist()
    }

    fun addCustomLabel(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        if (trimmed in DEFAULT_LABELS || trimmed in data.customLabels) return
        data = data.copy(customLabels = data.customLabels + trimmed)
        persist()
    }

    fun deleteCustomLabel(label: String) {
        data = data.copy(customLabels = data.customLabels.filterNot { it == label })
        persist()
    }

    fun addMark(mark: CourseMark) {
        data = data.copy(marks = data.marks + mark)
        persist()
    }

    fun deleteMark(id: String) {
        data = data.copy(marks = data.marks.filterNot { it.id == id })
        persist()
    }

    fun updateMarkPosition(id: String, lat: Double, lng: Double) {
        val updated = data.marks.map { m ->
            if (m.id == id) m.copy(lat = lat, lng = lng) else m
        }
        data = data.copy(marks = updated)
        persist()
    }

    fun addWindReading(directionDeg: Double, timestampMs: Long = System.currentTimeMillis()) {
        val normalized = ((directionDeg % 360) + 360) % 360
        val reading = WindReading(timestampMs = timestampMs, directionDeg = normalized)
        data = data.copy(windReadings = data.windReadings + reading)
        persist()
    }

    fun deleteWindReading(id: String) {
        data = data.copy(windReadings = data.windReadings.filterNot { it.id == id })
        persist()
    }

    fun setWindDirection(deg: Double?) {
        val normalized = deg?.let { ((it % 360) + 360) % 360 }
        data = data.copy(windFromDeg = normalized)
        persist()
    }

    fun addCustomMarkLabel(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        if (trimmed in DEFAULT_MARK_LABELS || trimmed in data.customMarkLabels) return
        data = data.copy(customMarkLabels = data.customMarkLabels + trimmed)
        persist()
    }

    fun addCustomBoatLabel(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        if (trimmed in DEFAULT_BOAT_LABELS || trimmed in data.customBoatLabels) return
        data = data.copy(customBoatLabels = data.customBoatLabels + trimmed)
        persist()
    }

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository {
            return instance ?: synchronized(this) {
                instance ?: Repository(
                    File(context.applicationContext.filesDir, "data.json")
                ).also { instance = it }
            }
        }
    }
}

fun cardinal(bearingDeg: Double): String {
    val labels = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = ((bearingDeg + 22.5) / 45).toInt().mod(8)
    return labels[idx]
}

fun formatElapsed(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "${m}m ${s}s"
}
