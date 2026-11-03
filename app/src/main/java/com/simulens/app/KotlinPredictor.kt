package com.simulens.app


import android.content.Context
import org.json.JSONObject
import kotlin.math.exp

data class RiskPrediction(
    val probability: Double,
    val riskLevel: String,
    val threshold: Double
)

class KotlinPredictor(context: Context) {

    private val modelParameters: JSONObject

    private val featureNames: List<String>
    private val imputationValues: List<Double>
    private val scalerMeans: List<Double>
    private val scalerScales: List<Double>
    private val coefficients: List<Double>

    private val intercept: Double
    private val threshold: Double

    init {

        // Load exported model parameters from assets
        val jsonString = context.assets.open(
            "risk_model_parameters.json"
        ).bufferedReader().use { it.readText() }

        modelParameters = JSONObject(jsonString)

        // Read feature names in their original training order
        featureNames = modelParameters
            .getJSONArray("feature_names")
            .let { array ->
                List(array.length()) { i ->
                    array.getString(i)
                }
            }

        // Read preprocessing parameters
        imputationValues = readDoubleArray("imputation_values")
        scalerMeans = readDoubleArray("scaler_means")
        scalerScales = readDoubleArray("scaler_scales")

        // Read Logistic Regression parameters
        coefficients = readDoubleArray("coefficients")

        intercept = modelParameters.getDouble("intercept")
        threshold = modelParameters.getDouble("threshold")

        // Validate model dimensions
        require(
            featureNames.size == coefficients.size &&
                    featureNames.size == imputationValues.size &&
                    featureNames.size == scalerMeans.size &&
                    featureNames.size == scalerScales.size
        ) {
            "Model parameter dimensions do not match."
        }
    }

    private fun readDoubleArray(key: String): List<Double> {

        val array = modelParameters.getJSONArray(key)

        return List(array.length()) { i ->
            array.getDouble(i)
        }
    }

    /**
     * Predicts the user's behavioural risk level.
     *
     * Input:
     * Map containing the 29 feature names and their values.
     *
     * Missing or null values are replaced using
     * the training dataset's imputation values.
     */
    fun predict(
        features: Map<String, Double?>
    ): RiskPrediction {

        var linearOutput = intercept

        for (i in featureNames.indices) {

            val featureName = featureNames[i]

            // Retrieve the feature value
            val rawValue = features[featureName]

            // Apply the same median imputation used in Python
            val value = rawValue
                ?.takeIf { it.isFinite() }
                ?: imputationValues[i]

            // Apply StandardScaler
            val scale = scalerScales[i]

            val standardizedValue =
                (value - scalerMeans[i]) / scale

            // Logistic Regression weighted sum
            linearOutput +=
                standardizedValue * coefficients[i]
        }

        // Apply sigmoid function
        val probability = sigmoid(linearOutput)

        // Apply selected classification threshold
        val riskLevel = if (probability >= threshold) {
            "ELEVATED"
        } else {
            "NORMAL"
        }

        return RiskPrediction(
            probability = probability,
            riskLevel = riskLevel,
            threshold = threshold
        )
    }

    private fun sigmoid(value: Double): Double {

        return if (value >= 0) {
            1.0 / (1.0 + exp(-value))
        } else {
            exp(value) / (1.0 + exp(value))
        }
    }

    /**
     * Returns the feature names expected by the model.
     */
    fun getRequiredFeatures(): List<String> {
        return featureNames
    }
}
