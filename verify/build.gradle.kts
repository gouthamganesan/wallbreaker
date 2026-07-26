import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
}

/**
 * The Android-free half of the app: pure Kotlin over `java.*` and
 * `javax.crypto` only. These are the files the unit tests actually exercise, so
 * compiling exactly this set both runs the suite and *enforces* the boundary —
 * if someone adds an `import android.*` to one of them, this build stops
 * compiling and the CI job fails.
 */
val androidFreeSources = listOf(
    "dev/goutham/wallbreaker/AppSettings.kt",
    "dev/goutham/wallbreaker/Freedium.kt",
    "dev/goutham/wallbreaker/HtmlMeta.kt",
    "dev/goutham/wallbreaker/UrlExtractor.kt",
    "dev/goutham/wallbreaker/oauth/OAuthSigner.kt",
)

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }

    sourceSets {
        named("main") {
            kotlin.setSrcDirs(listOf("../app/src/main/java"))
            kotlin.setIncludes(androidFreeSources)
        }
        named("test") {
            kotlin.setSrcDirs(listOf("../app/src/test/java"))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
    afterSuite(
        KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
            if (desc.parent == null) {
                println(
                    "\n${result.resultType}: ${result.testCount} tests — " +
                        "${result.successfulTestCount} passed, " +
                        "${result.failedTestCount} failed, " +
                        "${result.skippedTestCount} skipped",
                )
            }
        }),
    )
}
