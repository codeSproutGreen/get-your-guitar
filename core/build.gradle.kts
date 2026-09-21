plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // 음색 시연 WAV 렌더(ToneDemoRender)를 켜는 스위치를 테스트 JVM으로 넘긴다. 평소에는 꺼져 있다.
    for (key in listOf("gyg.renderWav", "gyg.renderLabel")) {
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    testLogging {
        events("passed", "failed", "skipped")
    }
}
