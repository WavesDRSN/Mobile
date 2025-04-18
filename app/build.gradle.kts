plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "ru.drsn.waves"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.drsn.waves"
        minSdk = 24
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    buildTypes {
        release {
            buildConfigField("boolean", "RELEASE", "true")
        }
        debug {
            buildConfigField("boolean", "RELEASE", "false")
        }
    }
}

dependencies {
    // gRPC и protobuf зависимости

    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.javax.annotation.api)

    // Корутинные адаптеры для gRPC
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.logging)
    implementation(libs.protobuf.kotlin.lite)

    // Логирование

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.timber)
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation ("androidx.lifecycle:lifecycle-livedata-ktx:2.5.1")
    implementation ("androidx.activity:activity-ktx:1.7.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //webrtc
    implementation(libs.stream.webrtc.android)
}


protobuf {
    protoc {
        artifact = "${libs.protoc.asProvider().get()}"
    }
    plugins {
        create("java") {
            artifact = libs.protoc.gen.grpc.java.get().toString()
        }
        create("grpc") {
            artifact = "${libs.protoc.gen.grpc.java.get()}"
        }
        create("grpckt") {
            artifact = "${libs.protoc.gen.grpc.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("java") {
                    option("lite")
                }
                create("grpc") {
                    option("lite")
                }
                create("grpckt") {
                    option("lite")
                }
            }
            it.builtins {
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}