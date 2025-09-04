plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    // Dodaj wtyczkę Kapt tutaj
    id("org.jetbrains.kotlin.kapt")
    // Dodaj wtyczkę Hilt
    id("com.google.dagger.hilt.android")
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

    // Retrofit (jeśli jeszcze nie masz)
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("com.github.bumptech.glide:glide:5.0.4")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.media:media:1.7.1")
    implementation("com.google.dagger:hilt-android:2.57.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    kapt("com.google.dagger:hilt-android-compiler:2.57.1")

    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}