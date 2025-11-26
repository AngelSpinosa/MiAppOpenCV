plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.miappopencv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.miappopencv"
        minSdk = 26
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

    // ESTA ES LA FORMA NUEVA Y CORRECTA (REEMPLAZA LO DE ARRIBA)
    androidResources {
        noCompress.add("tflite")
    }
}

dependencies {
    // La dependencia de los archivos Java de OpenCV sigue siendo necesaria
    implementation(project(":openCV"))

    // Dependencias estándar
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.gridlayout)
    implementation(libs.room.external.antlr) // Dependencia de GridLayout (de la vez pasada)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // DEPENDENCIAS DE CAMERA X (Ya las tenías)
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")

    // ---AÑADIR ESTAS LÍNEAS PARA TENSORFLOW LITE---
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    // --- NUEVAS DEPENDENCIAS PARA USB Y JSON ---
    // Librería experta en comunicación serial USB (drivers CP210x, FTDI, CDC, etc.)
    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
    // Librería de Google para convertir Objetos Java a texto JSON fácilmente
    implementation("com.google.code.gson:gson:2.10.1")
    // --- Fin de las nuevas dependencias ---
}