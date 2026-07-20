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
    compileOnly("org.ow2.asm:asm-tree:9.7.1")

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
}

dependencies {
    // hostStub's own compile-time dependency: the SpongePowered Mixin ILogger/Level types that the
    // real (and this stand-in) Logger/SimpleLogger implement.
    "hostStubCompileOnly"("net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7") {
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "com.google.guava", module = "guava")
    }
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
