import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin module: the single rules engine that decides when an app is blocked.
// It has no Android dependency, so its logic is unit-tested on the JVM.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test-junit"))
}
