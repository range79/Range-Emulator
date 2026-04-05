
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.3.20"
}


android {
    namespace = "com.range.rangeEmulator"
    testNamespace = "com.range.rangeEmulator.test"
    compileSdk {
        version = release(36)
    }
    sourceSets {
        getByName("main") {

            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    packaging {
        resources {

            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    defaultConfig {
        applicationId = "com.range.rangeEmulator"
        testApplicationId = "com.range.rangeEmulator.test"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../range_emulator_key.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}


dependencies {
    //noinspection UseTomlInstead
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.firebase:firebase-crashlytics-buildtools:3.0.6")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.core.splashscreen)
    // Source: https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    testImplementation("org.slf4j:slf4j-simple:2.0.17")
    // Source: https://mvnrepository.com/artifact/androidx.navigation/navigation-compose
    implementation("androidx.navigation:navigation-compose:2.9.7")
// Source: https://mvnrepository.com/artifact/androidx.datastore/datastore-preferences
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("io.ktor:ktor-client-core:3.4.1")
    // build.gradle.kts (dependencies kısmına ekle)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("io.ktor:ktor-client-cio:3.4.1")
    implementation("io.ktor:ktor-client-logging:3.4.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}