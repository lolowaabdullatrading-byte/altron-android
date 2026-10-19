import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// Firebase (push) is optional: the google-services plugin is applied only when google-services.json is present.
val hasFirebase = file("google-services.json").exists()
if (hasFirebase) apply(plugin = "com.google.gms.google-services")

// Signing: keystore.properties (local) or environment variables (GitHub Actions). Debug-signed if neither exists.
val ksProps = Properties()
val ksFile = rootProject.file("keystore.properties")
if (ksFile.exists()) ksProps.load(FileInputStream(ksFile))
fun sign(k: String): String? = ksProps.getProperty(k) ?: System.getenv(k)

android {
    namespace = "qa.altron.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "qa.altron.app"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "1.0.0"
        buildConfigField("String", "BASE_URL", "\"https://demo.altron.com.qa/app\"")
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebase.toString())
    }
    signingConfigs {
        create("release") {
            val path = sign("KEYSTORE_FILE")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storePassword = sign("KEYSTORE_PASSWORD")
                keyAlias = sign("KEY_ALIAS")
                keyPassword = sign("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { buildConfig = true }
    if (hasFirebase) sourceSets.getByName("main").java.srcDir("src/firebase/java")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    if (hasFirebase) {
        implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
        implementation("com.google.firebase:firebase-messaging")
    }
}
