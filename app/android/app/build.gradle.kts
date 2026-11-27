import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("com.google.dagger.hilt.android"); id("com.google.devtools.ksp") }

val local = Properties().apply { val f = rootProject.file("local.properties"); if (f.exists()) f.inputStream().use(::load) }
android { namespace = "com.roaddamage"; compileSdk = 35
    defaultConfig { applicationId = "com.roaddamage"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; buildConfigField("String", "API_BASE_URL", "\"${local.getProperty("API_BASE_URL", "http://10.0.2.2:3000/")}\""); resValue("string", "mapbox_access_token", local.getProperty("MAPBOX_ACCESS_TOKEN", "")) }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01")); implementation("androidx.activity:activity-compose:1.10.0"); implementation("androidx.compose.material3:material3"); implementation("androidx.compose.material:material-icons-extended"); implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview"); debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.camera:camera-camera2:1.4.1"); implementation("androidx.camera:camera-lifecycle:1.4.1"); implementation("androidx.camera:camera-view:1.4.1")
    implementation("org.tensorflow:tensorflow-lite:2.16.1"); implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1"); implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")
    implementation("com.google.android.gms:play-services-location:21.3.0"); implementation("androidx.room:room-runtime:2.6.1"); implementation("androidx.room:room-ktx:2.6.1"); ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0"); implementation("com.squareup.retrofit2:retrofit:2.11.0"); implementation("com.squareup.retrofit2:converter-moshi:2.11.0"); implementation("com.squareup.okhttp3:logging-interceptor:4.12.0"); implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.google.dagger:hilt-android:2.52"); ksp("com.google.dagger:hilt-compiler:2.52"); implementation("androidx.hilt:hilt-navigation-compose:1.2.0"); implementation("androidx.hilt:hilt-work:1.2.0"); ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.mapbox.maps:android:11.30.0")
}
