// 根目录 build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    id("jacoco")   // 让根项目本身可以声明 JacocoReport 任务
}

// -----------------------------------------------------------------------
// 聚合 JaCoCo 覆盖率报告
// -----------------------------------------------------------------------

val coveredModules = listOf(
    ":core:core-domain",
    ":core:core-logging",
    ":core:core-data",
    ":feature:feature-task",
    ":feature:feature-checkin",
    ":feature:feature-mushroom",
    ":feature:feature-reward",
    ":feature:feature-milestone",
    ":feature:feature-statistics"
)

// 各模块的测试任务路径
val testTaskPaths = coveredModules.map { path ->
    // core-domain 是 java-library，测试任务叫 :test；Android 模块叫 :testDebugUnitTest
    if (path == ":core:core-domain") "$path:test" else "$path:testDebugUnitTest"
}

tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "Aggregated JaCoCo coverage report across all tested modules"

    // 显式声明对所有测试任务的依赖（包括 core-domain:jacocoTestReport 避免隐式依赖报错）
    dependsOn(testTaskPaths)
    dependsOn(":core:core-domain:jacocoTestReport")

    // .exec 文件：Android 模块在 jacoco/ 下，core-domain 也在 jacoco/
    executionData.setFrom(
        fileTree(rootDir) {
            include(
                "**/build/jacoco/test.exec",
                "**/build/jacoco/testDebugUnitTest.exec",
                "**/build/outputs/unit_test_code_coverage/**/testDebugUnitTest.exec"
            )
        }
    )

    // 编译产出的 class 文件（只取 debug 变体，排除 release 和生成代码）
    classDirectories.setFrom(
        files(coveredModules.mapNotNull { path ->
            val proj = project(path)
            fileTree(proj.layout.buildDirectory.asFile.get()) {
                include(
                    // JVM 模块（core-domain）
                    "**/classes/kotlin/main/**/*.class",
                    // Android debug 变体 kotlin class
                    "**/tmp/kotlin-classes/debug/**/*.class"
                )
                exclude(
                    "**/R.class", "**/R$*.class",
                    "**/BuildConfig.class",
                    "**/*_HiltModules*",
                    "**/*_Factory*",
                    "**/*_MembersInjector*",
                    "**/*Hilt_*",
                    "**/hilt_aggregated_deps/**",
                    "**/dagger/**",
                    "**/*Dao_Impl*",
                    "**/MushroomDatabase_Impl*",
                    "**/com/mushroom/core/data/db/**"
                )
            }
        })
    )

    sourceDirectories.setFrom(
        files(coveredModules.map { path ->
            "${project(path).projectDir}/src/main/kotlin"
        })
    )

    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/coverage.xml"))
        csv.required.set(false)
    }
}


