import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

// prioritize GROQ_API_KEY, then fallback to GROK_API_KEY, then OPENAI_API_KEY
val rawApiKey = localProperties.getProperty("GROQ_API_KEY")
    ?: localProperties.getProperty("GROK_API_KEY")
    ?: localProperties.getProperty("OPENAI_API_KEY") 
    ?: ""

val rawGoogleWebClientId = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""
val rawRagBaseUrl = localProperties.getProperty("RAG_BASE_URL")
    ?.trim()
    ?.removeSurrounding("\"")
    ?.removeSurrounding("'")
    ?.ifBlank { null }
    ?: "http://10.0.2.2:8000"

val rawRagAppToken = localProperties.getProperty("RAG_APP_TOKEN")
    ?: localProperties.getProperty("APP_TOKEN")
    ?: ""

// Sanitize: Remove quotes, whitespace, and potential 'Bearer ' prefixes
val sanitizedApiKey = rawApiKey.trim()
    .removeSurrounding("\"")
    .removeSurrounding("'")
    .removePrefix("Bearer ")
    .trim()

val sanitizedGoogleWebClientId = rawGoogleWebClientId.trim()
    .removeSurrounding("\"")
    .removeSurrounding("'")

val sanitizedRagBaseUrl = rawRagBaseUrl.trim()
    .removeSurrounding("\"")
    .removeSurrounding("'")

val sanitizedRagAppToken = rawRagAppToken.trim()
    .removeSurrounding("\"")
    .removeSurrounding("'")

android {
    namespace = "com.simulens.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.simulens.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // This passes the key to TimetableExtractor via BuildConfig.AI_API_KEY
        buildConfigField("String", "AI_API_KEY", "\"$sanitizedApiKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$sanitizedGoogleWebClientId\"")
        buildConfigField("String", "RAG_BASE_URL", "\"$sanitizedRagBaseUrl\"")
        buildConfigField("String", "RAG_APP_TOKEN", "\"$sanitizedRagAppToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation(libs.okhttp)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation(libs.onnxruntime.android)

    // Google Auth & Credentials
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    //render dependencies
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
