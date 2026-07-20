plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "dev.iyanz.cherry"
version = "1.0.0"
description = "Gradle plugin for authoring Cherry mixin plugins: wires the SpongePowered Mixin " +
    "annotation processor + refmap generation onto a Paper-plugin project and auto-generates the " +
    "cherry-plugin.json manifest + mixin config Cherry's runtime reads, from a `cherry { }` DSL."

java {
    toolchain {
        // The plugin's own code targets a lower, broadly-available language level than Cherry's
        // runtime (JDK 25, see the root build.gradle.kts) on purpose: this module runs inside an
        // arbitrary plugin author's own Gradle build, on whatever JVM invokes their Gradle daemon,
        // which is not guaranteed to be JDK 25. JDK 21 is the same baseline Leavesclip's own
        // java21 module (which Cherry vendors into) already requires.
        //
        // This is forward-, not just backward-, compatible: JDK 21 bytecode runs unmodified on a
        // JDK 25 JVM, and this whole repository (root + this included build + example-plugin, which
        // itself targets JDK 25 - see example-plugin/build.gradle.kts) has been verified to build,
        // test, and generate javadoc end to end with the Gradle daemon itself running on JDK 25
        // (`JAVA_HOME=<jdk-25> ./gradlew clean build javadoc test`), auto-provisioning this module's
        // JDK 21 compile toolchain via Gradle's toolchain detection the same way it would for any
        // author. There is no actual JDK 25 incompatibility here to fix, so this is deliberately
        // NOT bumped to 25: doing so would narrow, not widen, which Gradle daemons can apply this
        // plugin, which cuts directly against the plugin's purpose.
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Used to write the generated cherry-plugin.json / *.mixins.json in exactly the shape
    // dev.iyanz.sourbyclip.cherry.discovery.CherryDiscovery's Gson models read back - same
    // dependency (and version) the Cherry runtime itself uses to parse those files, so the two
    // sides of the format agree by construction rather than by convention.
    implementation("com.google.code.gson:gson:2.13.1")

    // Used by dev.iyanz.cherry.gradle.MixinClassScanner to find which compiled classes under the
    // author's declared mixinPackage actually carry a visible/invisible @Mixin annotation, so the
    // generated SpongePowered Mixin config's "mixins" list can be populated automatically instead
    // of requiring the author to hand-maintain it. This is purely an internal build-time tool
    // (isolated to this plugin's own classpath, never exposed to a consuming project), so it is
    // intentionally NOT pinned to the same ASM version Cherry's runtime module pins (9.8, matching
    // what net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7 itself transitively requires - see the root
    // build.gradle.kts's asm-tree comment). ASM 9.8 already supports Java 25 (class file major
    // version 69) just fine, but it is still the *oldest* release that does - it cannot parse
    // whatever comes after Java 25/26. This scanner reads the *author's own* compiled output, on
    // the *author's own* toolchain, which this repository has no control over and which may already
    // be newer than Cherry's own runtime pin by the time an author builds against it, so a
    // deliberately newer-than-strictly-necessary ASM release is used here on purpose, independent of
    // Cherry's runtime pin, and should be bumped ahead of it again whenever a newer ASM ships.
    implementation("org.ow2.asm:asm:9.10.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // For CherryGradlePluginFunctionalTest, which builds an in-memory (ProjectBuilder) project,
    // applies the plugin to it, and asserts the extension/tasks/dependencies it wires up - without
    // needing a full external Gradle build per test.
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    website.set("https://github.com/YanIanZ/Cherry")
    vcsUrl.set("https://github.com/YanIanZ/Cherry")

    plugins {
        register("cherry") {
            id = "dev.iyanz.cherry"
            implementationClass = "dev.iyanz.cherry.gradle.CherryGradlePlugin"
            displayName = "Cherry"
            description = project.description
            tags.set(listOf("minecraft", "paper", "mixin", "cherry", "sourbycraft"))
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
