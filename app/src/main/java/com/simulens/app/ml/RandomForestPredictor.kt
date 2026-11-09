package com.simulens.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log

/** The model's score for the ELEVATED class and the class that results from the cut-off. */
data class RiskPrediction(
    val probability: Double,
    val riskLevel: String,
    val threshold: Double = RandomForestPredictor.THRESHOLD
)

/**
 * On-device random forest (ONNX Runtime). It never invents a result: if the model cannot load,
 * a feature is missing, or the output cannot be read, [predict] returns null and says why in logcat
 * (tag SimulensML), so the UI can show "not available" instead of a made-up number.
 */
class RandomForestPredictor(context: Context, assetName: String = MODEL_ASSET) : AutoCloseable {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "float_input"

    /** Null when the model loaded and matches the feature list; otherwise the reason it did not. */
    var loadError: String? = null
        private set

    val isReady: Boolean get() = session != null
    val featureNames: List<String> get() = FEATURE_NAMES

    init {
        try {
            val e = OrtEnvironment.getEnvironment()
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            val s = e.createSession(bytes)
            val name = s.inputNames.first()
            val shape = (s.inputInfo[name]?.info as? TensorInfo)?.shape
            val n = shape?.getOrNull(1)?.toInt() ?: -1
            if (n > 0 && n != FEATURE_NAMES.size) {
                loadError = "Model expects $n features but the app defines ${FEATURE_NAMES.size}"
                s.close()
            } else {
                env = e
                session = s
                inputName = name
            }
            Log.i(TAG, "Loaded $assetName: input '$name', shape ${shape?.toList()}, outputs ${s.outputNames}")
        } catch (ex: Exception) {
            loadError = "Could not load $assetName: ${ex.message}"
        }
        loadError?.let { Log.e(TAG, it) }
    }

    fun predict(features: Map<String, Double?>): RiskPrediction? {
        val s = session
        val e = env
        if (s == null || e == null) {
            Log.w(TAG, "Model unavailable: $loadError")
            return null
        }

        val bad = FEATURE_NAMES.filter { n -> features[n]?.let { it.isNaN() || it.isInfinite() } ?: true }
        if (bad.isNotEmpty()) {
            Log.w(TAG, "Missing or invalid features, no prediction made: $bad")
            return null
        }
        val row = FloatArray(FEATURE_NAMES.size) { features.getValue(FEATURE_NAMES[it])!!.toFloat() }

        return try {
            OnnxTensor.createTensor(e, arrayOf(row)).use { tensor ->
                s.run(mapOf(inputName to tensor)).use { results ->
                    val p = readElevatedProbability(results)
                    if (p == null) {
                        Log.e(TAG, "Could not read class probabilities from outputs ${s.outputNames}")
                        null
                    } else {
                        RiskPrediction(p, if (p >= THRESHOLD) "ELEVATED" else "NORMAL")
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Inference failed", ex)
            null
        }
    }

    /**
     * Handles both ways a converted forest can return probabilities:
     * a float tensor [1, 2] (ZipMap disabled), or a list of maps keyed by class (ZipMap enabled).
     */
    private fun readElevatedProbability(results: OrtSession.Result): Double? {
        for (entry in results) {
            when (val v = entry.value.value) {
                is Array<*> -> {
                    val row = v.firstOrNull()
                    if (row is FloatArray && row.size == 2) return row[1].toDouble()
                    if (row is DoubleArray && row.size == 2) return row[1]
                }
                is List<*> -> {
                    val m = v.firstOrNull() as? Map<*, *>
                    val p = (m?.get(1L) ?: m?.get(1) ?: m?.get("1")) as? Number
                    if (p != null) return p.toDouble()
                }
            }
        }
        return null
    }

    override fun close() {
        session?.close()
        session = null
    }

    companion object {
        private const val TAG = "SimulensML"
        const val MODEL_ASSET = "simulens_rf_champion.onnx"

        /** Must equal the cut-off used when the model was evaluated in the notebook. */
        const val THRESHOLD = 0.50

        /** Order must equal the training columns exactly. Print X.columns in the notebook to confirm. */
        val FEATURE_NAMES = listOf(
            "avg_daily_duration", "max_daily_duration", "std_daily_duration",
            "avg_daily_sessions", "max_daily_sessions", "std_daily_sessions",
            "active_days", "total_sessions", "avg_session_duration",
            "median_session_duration", "std_session_duration", "avg_session_gap",
            "avg_sessions_recorded", "schedule_conflict_rate", "morning_usage_rate",
            "bedtime_usage_rate", "avg_minutes_since_wake", "avg_minutes_until_sleep",
            "avg_baseline_duration_ratio", "avg_baseline_frequency_ratio", "unique_apps",
            "early_avg_duration", "late_avg_duration", "early_avg_sessions",
            "late_avg_sessions", "duration_change", "session_change",
            "weekly_duration_variability", "weekly_session_variability"
        )
    }
}