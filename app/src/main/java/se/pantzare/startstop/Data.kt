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
data class AppData(
    val measurements: List<SavedMeasurement> = emptyList(),
    val customLabels: List<String> = emptyList(),
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
