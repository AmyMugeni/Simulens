package com.simulens.app.ml.models

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import android.util.Log

class ModelEvaluator(private val context: Context) {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    init {
        try {
            env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("simulens_model1.onnx").use { it.readBytes() }
            session = env?.createSession(modelBytes)
            Log.i("Simulens", "ModelEvaluator: ONNX model (simulens_model1.onnx) loaded successfully.")
        } catch (t: Throwable) {
            Log.e("Simulens", "ModelEvaluator: Failed to load ONNX model (${t.message}). Falling back to RuleEngine.", t)
            env = null
            session = null
        }
    }

    /**
     * Evaluates active session telemetry against the ONNX Gradient Boosting classifier.
     *
     * @param sessionDurationMins Active session duration in minutes.
     * @param reopenIntervalMins Time elapsed since last social media app session.
     * @param bsmasScore User's BSMAS addiction score (6 to 30).
     * @param isScheduleConflict 1.0 if current session overlaps with lecture/work/sleep, else 0.0.
     * @return true if the model predicts compulsive usage requiring intervention, false otherwise.
     */
    fun evaluateSession(
        sessionDurationMins: Float,
        reopenIntervalMins: Float,
        bsmasScore: Float,
        isScheduleConflict: Float
    ): Boolean {
        val ortSession = session ?: return false
        val ortEnv = env ?: return false

        return try {
            val reopenUrgency = sessionDurationMins / (reopenIntervalMins + 1.0f)
            val compulsionDurationIndex = (bsmasScore / 30.0f) * sessionDurationMins

            val inputData = floatArrayOf(
                sessionDurationMins,
                reopenIntervalMins,
                bsmasScore,
                isScheduleConflict,
                reopenUrgency,
                compulsionDurationIndex
            )

            val shape = longArrayOf(1, 6)
            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputData), shape)

            tensor.use {
                val results = ortSession.run(mapOf("float_input" to tensor))
                val output = results[0].value

                val prediction = when (output) {
                    is LongArray -> output.firstOrNull() == 1L
                    is Array<*> -> {
                        when (val first = output.firstOrNull()) {
                            is LongArray -> first.firstOrNull() == 1L
                            is Long -> first == 1L
                            else -> false
                        }
                    }
                    else -> {
                        Log.e("Simulens", "ModelEvaluator: Unexpected ONNX output type: ${output?.javaClass?.name}")
                        false
                    }
                }

                prediction
            }
        } catch (e: Exception) {
            Log.e("Simulens", "ModelEvaluator: Error evaluating ONNX session", e)
            false
        }
    }
}