plugins {
    java
}

group = "dev.iyanz.cherry"
version = "1.0.0"
description = "Cherry - SourbyCraft's unified server-side mixin system " +
    "(LeavesMC/Leavesclip mixin base + CraftCanvasMC/Horizon access-transformer engine)"

java {
    toolchain {
        // Matches the source level of Cherry as vendored in SourbyClip's java25 module: the AT
        // engine uses pattern-matching switch over AtDefinition.Data (JEP 441, stable since 21).
        languageVersion.set(JavaLanguageVersion.of(25))
    }

    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

repositories {
    mavenCentral()
    // Hosts net.fabricmc:sponge-mixin, the Fabric fork of SpongePowered Mixin that Leavesclip (and
    // therefore SourbyClip/Cherry) builds on. Same repository SourbyClip itself resolves it from.
    maven("https://repo.spongepowered.org/maven/")
}

sourceSets {
    // Cherry's actual source: a byte-for-byte copy of what SourbyClip vendors at
    // sourbyclip/java25/src/main/java/dev/iyanz/sourbyclip/cherry/.
    main {
    }

    // Compile-time-only stand-ins for the small slice of SourbyClip's vendored Leavesclip fork
    // (org.leavesmc.leavesclip.logger/.mixin) that Cherry's classes import. These are NOT Cherry,
    // are NOT published in any produced artifact, and are only ever added to `main`'s
    // *compile* classpath below - see src/hostStub/java for the full explanation of why this
    // repository cannot depend on a real published "leavesclip" library (there isn't one: it's a
    // launcher, forked directly into SourbyClip's own source tree, not a Maven artifact).
    create("hostStub") {
        java.srcDir("src/hostStub/java")
    }
}

dependencies {
    // --- Real dependencies Cherry's own source (src/main/java) imports ---

    // CherryPluginResolver deserializes cherry-plugin.json / leaves-plugin.json manifests with
    // Gson at runtime, so this is a genuine (not host-provided) runtime dependency, same version
    // SourbyClip itself bundles.
    implementation("com.google.code.gson:gson:2.13.1")

    // CherryAccessTransformers rewrites class/field/method access flags with ASM's tree API. This
    // is compileOnly because the host (SourbyClip's Leavesclip-based launcher) always has ASM on
    // its runtime classpath already (pulled in transitively by SpongePowered Mixin) - Cherry never
    // needs to bundle its own copy.
    //
    // Pinned to 9.8 (not just "whatever resolves") because that is exactly the ASM version
    // net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7 itself transitively requires (verified via
    // `./gradlew dependencies` - sponge-mixin 0.17.3 depends on asm/asm-tree/asm-commons/asm-util
    // all at 9.8), i.e. what actually ships on the runtime classpath inside SourbyClip. ASM 9.8 is
    // the first ASM release that understands class file major version 69 (Java 25) - see the
    // sibling note on cherry-gradle-plugin's own (newer, 9.10.1) ASM pin for why that module
    // intentionally uses a different version than this one. Before 9.8, ASM (and therefore any
    // Mixin/AT bytecode transform built on it) would throw "Unsupported class file major version
    // 69" the moment it tried to parse a class compiled with `--release 25`.
    compileOnly("org.ow2.asm:asm-tree:9.8")

    // Cherry's Logger/SimpleLogger usage (via hostStub, see below) resolves down to
    // org.spongepowered.asm.logging.ILogger/Level, so javac needs this on `main`'s compile
    // classpath too, even though Cherry's own classes never reference it by name. Exactly the same
    // is true at runtime inside SourbyClip: Mixin is already on the classpath there.
    compileOnly("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7") {
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "com.google.guava", module = "guava")
    }

    // See the hostStub sourceSet comment above: compiled in for `main` at compile time only.
    compileOnly(sourceSets["hostStub"].output)

    // --- Test-only dependencies ---

    // JUnit 5 for the pure-logic unit tests under src/test/java: AT DSL parsing, fabric.mod.json /
    // standalone *.mixins.json parsing, multi-format discovery de-dup + priority ordering, and the
    // Fabric mixin/widener extraction+caching mechanics. Appropriate here even though SourbyCraft
    // server sub-specs avoid JUnit: this is a standalone library repository verified by
    // `./gradlew test`, not a SourbyCraft server sub-spec verified by booting a real server.
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // main's own sources are compiled against these as `compileOnly` because the host (SourbyClip)
    // always supplies them at runtime in production - see the dependency block above. This
    // repository's own test execution has no such host, so the same libraries are added back here
    // purely so `./gradlew test` can actually exercise code that touches ASM
    // (CherryAccessTransformers, CherryFabricBridge) and the Logger/SimpleLogger hostStub
    // reproduction. None of this affects the published main/sources/javadoc jars. asm-tree is
    // `testImplementation` (not testRuntimeOnly) because CherryAccessTransformersTest inspects the
    // transformed bytecode directly with ASM's tree API to assert access flags actually changed.
    // Same 9.8 pin as the compileOnly declaration above, for the same reason (matches what
    // sponge-mixin 0.17.3+mixin.0.8.7 actually provides at runtime and supports Java 25 class
    // files).
    testImplementation("org.ow2.asm:asm-tree:9.8")
    testRuntimeOnly("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7") {
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "com.google.guava", module = "guava")
    }
    testCompileOnly(sourceSets["hostStub"].output)
    testRuntimeOnly(sourceSets["hostStub"].output)
}

dependencies {
    // hostStub's own compile-time dependencies: the SpongePowered Mixin ILogger/Level types that the
    // real (and this stand-in) Logger/SimpleLogger implement, and Gson's @SerializedName, used by
    // the LeavesPluginMeta stand-in's nested MixinConfig to mirror upstream Leavesclip's JSON field
    // names (package-name/access-widener) exactly.
    "hostStubCompileOnly"("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7") {
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "com.google.guava", module = "guava")
    }
    "hostStubCompileOnly"("com.google.code.gson:gson:2.13.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "Cherry",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.javadoc {
    // Only document `main` (the real Cherry source) - hostStub is deliberately excluded, both from
    // this task's source and from every other published artifact.
    setSource(sourceSets.main.get().allJava)

    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        docEncoding = "UTF-8"
        // Cherry's javadoc is written in full prose with @param/@return on every public member;
        // this only relaxes the "missing tag" class of doclint warnings some JDKs are stricter
        // about for record components and enum constants, without disabling checks for genuinely
        // broken HTML or unresolvable @link references.
        addStringOption("Xdoclint:all,-missing", "-quiet")
    }
}
