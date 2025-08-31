plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.mycarapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mycarapp"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(project(":shared"))

    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp3 Logging Interceptor
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle components, aby używać lifecycleScope
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Glide do ładowania obrazków
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // CardView
    implementation("androidx.cardview:cardview:1.0.0")

    // Retrofit (jeśli jeszcze nie masz)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp3 Logging Interceptor (jeśli jeszcze nie masz)
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")

    // Kotlin Coroutines (jeśli jeszcze nie masz)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle components, aby używać lifecycleScope
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    implementation("io.coil-kt:coil:2.6.0")

//    implementation("androidx.activity:activity-compose:1.4.0")
//    implementation("com.squareup.okhttp3.okhttp:okhttp:4.10.0")
//    implementation("com.squareup.okhttp3.logging:logging-interceptor:4.10.0")
//    implementation("io.coil-kt.coil-compose:1.4.0")
//    implementation("androidx.compose.material.material-icons-extended:1.5.0")
//    implementation("androidx.media3.media3:media3-exoplayer:1.1.0")
//    implementation("androidx.media3.media3:media3-session:1.1.0")
//    implementation("androidx.media3.media3:media3-common:1.1.0")
//    implementation("androidx.media3.media3:media3-ui:1.1.0")
//    implementation("androidx.media.media.media:media:1.1.0")

    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)



    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}