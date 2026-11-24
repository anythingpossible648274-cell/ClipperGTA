plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.clipper.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "io.clipper.app"; minSdk = 29; targetSdk = 34
        versionCode = 1; versionName = "1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.4")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.4")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
}
