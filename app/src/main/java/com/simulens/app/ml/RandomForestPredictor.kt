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
 * On-device random forest (ONNX Runtime). It loads [simulens_rf_champion.onnx] and executes
 * real-time inference over the 29 extracted behavioral features.
 */
class RandomForestPredictor(context: Context, assetName: String = MODEL_ASSET) : AutoCloseable {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "float_input"
    private var expectedFeatures: Int = FEATURE_NAMES.size

    /** Null when the model loaded successfully; otherwise the error reason. */
    var loadError: String? = null
        private set

    val isReady: Boolean get() = session != null
    val featureNames: List<String> get() = FEATURE_NAMES

    init {
        try {
            Log.d(TAG, "ONNX Init: Requesting OrtEnvironment...")
            val e = OrtEnvironment.getEnvironment()
            Log.d(TAG, "ONNX Init: Reading asset '$assetName'...")
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            Log.d(TAG, "ONNX Init: Asset '$assetName' read successfully (${bytes.size} bytes). Creating session...")
            val s = e.createSession(bytes)
            val name = s.inputNames.first()
            val shape = (s.inputInfo[name]?.info as? TensorInfo)?.shape
            val n = shape?.getOrNull(1)?.toInt() ?: -1

            if (n > 0) {
                expectedFeatures = n
            }
            env = e
            session = s
            inputName = name
            Log.i(TAG, "[ONNX INIT SUCCESS] Asset: $assetName, inputName: '$name', expectedFeatures: $expectedFeatures, inputs: ${s.inputNames}, outputs: ${s.outputNames}")
            s.outputInfo.forEach { (outName, outInfo) ->
                Log.d(TAG, "  Output '$outName': info type=${outInfo.info?.javaClass?.simpleName}, valueInfo=${outInfo.info}")
            }
        } catch (ex: Exception) {
            loadError = "Could not load $assetName: ${ex.message}"
            Log.e(TAG, "[ONNX INIT ERROR] $loadError", ex)
        }
    }

    fun predict(features: Map<String, Double?>): RiskPrediction? {
        val s = session
        val e = env
        Log.d(TAG, "predict() called. isReady=$isReady, input features size=${features.size}")
        if (s == null || e == null) {
            Log.w(TAG, "predict() aborted: Model unavailable. loadError=$loadError")
            return null
        }

        return try {
            val floatArray = FloatArray(expectedFeatures) { index ->
                val name = FEATURE_NAMES.getOrNull(index)
                val valDouble = if (name != null) features[name] else null
                val valFloat = valDouble?.toFloat() ?: 0.0f
                if (valFloat.isNaN() || valFloat.isInfinite()) 0.0f else valFloat
            }

            Log.d(TAG, "Creating ONNX input tensor for '$inputName' with ${floatArray.size} float values...")
            OnnxTensor.createTensor(e, arrayOf(floatArray)).use { tensor ->
                Log.d(TAG, "Executing ONNX session.run()...")
                s.run(mapOf(inputName to tensor)).use { results ->
                    Log.d(TAG, "ONNX session.run() finished. Result count=${results.count()}")
                    val p = readElevatedProbability(results)
                    if (p == null) {
                        Log.e(TAG, "[PREDICT ERROR] Could not read class probabilities from model outputs ${s.outputNames}")
                        null
                    } else {
                        val pred = RiskPrediction(p, if (p >= THRESHOLD) "ELEVATED" else "NORMAL")
                        Log.i(TAG, "[PREDICT SUCCESS] Probability=${p}, RiskLevel=${pred.riskLevel}")
                        pred
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "[PREDICT EXCEPTION] Inference failed", ex)
            null
        }
    }

    /**
     * Handles both ways a converted forest can return probabilities:
     * a float tensor [1, 2] (ZipMap disabled), or a list of maps keyed by class (ZipMap enabled via ONNXMap).
     */
    private fun readElevatedProbability(results: OrtSession.Result): Double? {
        var entryIndex = 0
        for (entry in results) {
            val rawValue = entry.value.value
            val valClass = rawValue?.javaClass?.name ?: "null"
            Log.d(TAG, "  Output [$entryIndex] key='${entry.key}', type=$valClass, value=$rawValue")
            entryIndex++

            when (rawValue) {
                is Array<*> -> {
                    Log.d(TAG, "    Output is Array of size ${rawValue.size}")
                    val row = rawValue.firstOrNull()
                    Log.d(TAG, "    Row first item type=${row?.javaClass?.name}")
                    if (row is FloatArray && row.size >= 2) {
                        Log.d(TAG, "    Found FloatArray in Array with values: ${row.joinToString()}")
                        return row[1].toDouble()
                    }
                    if (row is DoubleArray && row.size >= 2) {
                        Log.d(TAG, "    Found DoubleArray in Array with values: ${row.joinToString()}")
                        return row[1]
                    }
                }
                is List<*> -> {
                    Log.d(TAG, "    Output is List of size ${rawValue.size}")
                    val firstItem = rawValue.firstOrNull()
                    Log.d(TAG, "    List first item type=${firstItem?.javaClass?.name}")

                    val map: Map<*, *>? = when (firstItem) {
                        is ai.onnxruntime.OnnxMap -> firstItem.value
                        is Map<*, *> -> firstItem
                        else -> null
                    }

                    Log.d(TAG, "    Extracted Map contents: $map")
                    if (map != null) {
                        val p = (map[1L] ?: map[1] ?: map["1"]) as? Number
                        if (p != null) {
                            Log.d(TAG, "    Found probability value from Map: $p")
                            return p.toDouble()
                        }
                    }
                }
                is ai.onnxruntime.OnnxMap -> {
                    val map = rawValue.value
                    Log.d(TAG, "    Extracted direct OnnxMap contents: $map")
                    if (map != null) {
                        val p = (map[1L] ?: map[1] ?: map["1"]) as? Number
                        if (p != null) {
                            Log.d(TAG, "    Found probability value from direct OnnxMap: $p")
                            return p.toDouble()
                        }
                    }
                }
                is Map<*, *> -> {
                    val p = (rawValue[1L] ?: rawValue[1] ?: rawValue["1"]) as? Number
                    if (p != null) {
                        Log.d(TAG, "    Found probability value from direct Map: $p")
                        return p.toDouble()
                    }
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
