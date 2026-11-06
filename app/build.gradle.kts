plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.goalwall"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.goalwall"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}
dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
