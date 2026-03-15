import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Read signing credentials from local.properties (never committed to git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// Read signing credentials: CI uses env vars, local dev uses local.properties
fun prop(envKey: String, localKey: String = envKey): String =
    System.getenv(envKey) ?: localProps.getProperty(localKey, "")

android {
    namespace = "com.mushroom.adventure"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Server configuration (shared across flavors)
        buildConfigField("String", "SERVER_URL", "\"http://192.168.31.174:8080\"")
    }

    flavorDimensions += "brand"
    productFlavors {
        create("mushroom") {
            dimension = "brand"
            applicationId = "com.mushroom.adventure"
            buildConfigField("String", "UPDATE_CHECK_OWNER", "\"jyjy0623\"")
            buildConfigField("String", "UPDATE_CHECK_REPO", "\"Mushroom\"")
            buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "true")
            buildConfigField("String", "UPDATE_CHECK_TAG_PREFIX", "\"v\"")
        }
        create("ukdream") {
            dimension = "brand"
            applicationId = "com.ukdream.adventure"
            buildConfigField("String", "UPDATE_CHECK_OWNER", "\"jyjy0623\"")
            buildConfigField("String", "UPDATE_CHECK_REPO", "\"Mushroom\"")
            buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "true")
            buildConfigField("String", "UPDATE_CHECK_TAG_PREFIX", "\"uk-v\"")
        }
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_STORE_FILE")
                ?: localProps.getProperty("RELEASE_STORE_FILE", "mushroom-release.jks")
            storeFile     = file(storePath)
            storePassword = prop("RELEASE_STORE_PASSWORD")
            keyAlias      = prop("RELEASE_KEY_ALIAS")
            keyPassword   = prop("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:core-logging"))
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-network"))
    implementation(project(":feature:feature-task"))
    implementation(project(":feature:feature-checkin"))
    implementation(project(":feature:feature-mushroom"))
    implementation(project(":feature:feature-reward"))
    implementation(project(":feature:feature-milestone"))
    implementation(project(":feature:feature-statistics"))
    implementation(project(":feature:feature-game"))
    implementation(project(":feature:feature-account"))
    implementation(project(":service:service-task-generator"))
    implementation(project(":service:service-notification"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    ksp(libs.hilt.android.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.core)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
