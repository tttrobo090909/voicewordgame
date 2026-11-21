import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 버전 규칙: v1.0_MMDD_HHmm (빌드 시점 KST 기준)
val kstStamp: String = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
    .format(DateTimeFormatter.ofPattern("MMdd_HHmm"))
val appVersionName: String = "1.0_$kstStamp"
val appVersionCode: Int = kstStamp.replace("_", "").toInt()

android {
    namespace = "com.pipi.voicewordgame"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pipi.voicewordgame"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "VERSION_DISPLAY", "\"v$appVersionName\"")
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += listOf("webp")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}

// 빌드된 버전명을 파일로 남겨 워크플로우가 APK 파일 이름에 쓸 수 있게 한다.
tasks.register("writeVersionFile") {
    val name = appVersionName
    val target = layout.buildDirectory.file("outputs/version.txt")
    doLast {
        val file = target.get().asFile
        file.parentFile?.mkdirs()
        file.writeText(name)
        println("VERSION_NAME=$name")
    }
}
