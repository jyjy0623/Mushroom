plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly("com.android.tools.lint:lint-api:31.2.2")
    compileOnly("com.android.tools.lint:lint-checks:31.2.2")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("org.jetbrains.uast:uast:1.8.22")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}
