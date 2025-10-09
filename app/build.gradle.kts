// Esta es la configuración final para el módulo de tu aplicación.

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.miappopencv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.miappopencv"
        minSdk = 24
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

    // Asegurándose de que las opciones de Java coincidan con las de la librería.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // ¡Esta es la línea MÁS IMPORTANTE!
    // Le indica a tu app que use el módulo :openCV como una dependencia.
    // El nombre ":openCV" debe coincidir EXACTAMENTE con el nombre de la carpeta del módulo.
    implementation(project(":openCV"))

    // Dependencias estándar de Android
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

