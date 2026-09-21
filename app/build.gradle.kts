plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 산출물 파일 이름: got-you-bass-debug.apk / got-you-bass-release.apk.
// 앱 이름(런처에 보이는 이름, "가츄베이스")은 res/values/strings.xml의 app_name이다. 파일 이름은 영문으로 둔다.
// 패키지(applicationId)는 com.sproutgreen.getyourguitar 그대로 둔다 — 바꾸면 폰에서 다른 앱으로 취급돼
// 기존 설치본을 덮어쓰지 못하고 저장된 설정도 이어지지 않는다.
base {
    archivesName = "got-you-bass"
}

android {
    namespace = "com.sproutgreen.getyourguitar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sproutgreen.getyourguitar"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // 개인용 앱이라 정식 키스토어를 두지 않는다(저장소에 넣을 수 없는 비밀이 생긴다).
            // 각 환경의 디버그 키로 서명한다 → 다른 PC에서 만든 APK로 덮어 설치하려면 먼저 앱을 지워야 한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.testLogging { events("passed", "failed", "skipped") }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 지판 기하·제스처 로직은 Android 의존이 없는 순수 Kotlin이라 JVM 단위 테스트로 검증한다.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
