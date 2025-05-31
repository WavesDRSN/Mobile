plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.dagger.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.gms.services)
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

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.rxjava2)
    implementation(libs.androidx.room.rxjava3)
    implementation(libs.androidx.room.guava)
    testImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.room.paging)

    implementation(libs.google.gson)

    implementation(libs.hilt.android)
    implementation(libs.androidx.material3.android) // Используйте последнюю версию
    ksp(libs.hilt.compiler) // или kapt для kapt

    // Hilt для ViewModel 
    implementation(libs.androidx.hilt.navigation.compose) // Пример для Compose Navigation
    implementation(libs.androidx.work)
    implementation(libs.androidx.hilt.work) // Пример для WorkManager
    ksp(libs.androidx.hilt.compiler) // или kapt

    implementation(libs.bcpkix.jdk18on)
    implementation(libs.androidx.security.crypto)
    implementation(libs.bitcoinj.bitcoinj.core)

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
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.timber)
    implementation (libs.androidx.lifecycle.viewmodel.ktx)
    implementation (libs.androidx.lifecycle.livedata.ktx)
    implementation (libs.androidx.activity.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //webrtc
    implementation(libs.stream.webrtc.android)

    // Firebase уведомления

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

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