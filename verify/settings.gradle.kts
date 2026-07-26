// A standalone Gradle build, deliberately NOT included in the root project.
//
// The root build's `plugins { }` block resolves the Android Gradle Plugin even
// with `apply false`, so any invocation there needs Google's Maven repo. This
// build exists to run the pure-logic unit tests in environments that only have
// a JDK and Maven Central — CI containers, sandboxes, a laptop with no Android
// SDK installed. It compiles the *same* source files as `:app`, source-included
// from ../app; there is no copy to drift.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}
rootProject.name = "wallbreaker-verify"
