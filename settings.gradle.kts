pluginManagement {
    // Makes the dev.iyanz.cherry plugin (cherry-gradle-plugin/) resolvable by plugin id from
    // example-plugin/ below without needing to publish it anywhere first - a normal composite-build
    // included build. This is also what makes `./gradlew :cherry-gradle-plugin:build` work directly
    // from this root (Gradle resolves qualified task paths into an included build the same way it
    // would a regular subproject - see https://docs.gradle.org/current/userguide/composite_builds.html).
    // A real external consumer instead applies the published plugin the normal way (see the README's
    // "Authoring a Cherry plugin (Gradle)" section) - this include is only for this repository's own
    // build and its example-plugin.
    includeBuild("cherry-gradle-plugin")
}

rootProject.name = "cherry"

// A minimal, real Cherry plugin (applies the dev.iyanz.cherry plugin above) that proves the whole
// toolchain end-to-end: see example-plugin/build.gradle.kts and the README's "Authoring a Cherry
// plugin (Gradle)" section.
include("example-plugin")
