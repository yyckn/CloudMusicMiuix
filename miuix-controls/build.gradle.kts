plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hypermusic.miuix.controls"
    compileSdk = 37
    defaultConfig { minSdk = 33 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
    api(platform(libs.androidx.compose.bom))
    api("androidx.compose.foundation:foundation")
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    api("top.yukonga.miuix.kmp:miuix-core-android:0.9.3")
    api("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.3")
    api(libs.miuix.icons)
    api(libs.miuix.blur)
    implementation("com.materialkolor:material-color-utilities-android:4.1.1")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
}

configurations.configureEach {
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui")
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui-android")
}
