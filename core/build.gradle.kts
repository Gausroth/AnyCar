import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin, no Android SDK: an android.* import must fail to compile here.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}
