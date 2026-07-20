plugins {
    java
    id("dev.iyanz.cherry")
}

group = "com.example"
version = "1.0.0"
description = "Example Cherry plugin - proves cherry-gradle-plugin end to end (see the main " +
    "repository README's 'Authoring a Cherry plugin (Gradle)' section). Not published; lives in " +
    "this repository purely to be built and inspected."

java {
    toolchain {
        // Matches paper-api 26.2.build.62-beta's own required JVM target (Paper 26.2 requires JDK 25).
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // The actual server API this example's @Mixin class targets (org.bukkit.Bukkit, see
    // src/main/java/com/example/cherrydemo/mixin/MixinBukkitGreeting.java) - a real, publicly
    // resolvable dependency, so the Mixin annotation processor's target validation runs against real
    // bytecode, not a stub. A production Cherry plugin that mixes into actual NMS/internal server
    // classes instead of plain Bukkit API needs the author's own mapped server jar on this same
    // configuration (e.g. via paperweight-userdev) - see the README section this module demonstrates
    // for exactly what is and is not proven by this example.
    compileOnly("io.papermc.paper:paper-api:26.2.build.62-beta")
}

cherry {
    pluginName = "CherryDemo"
    mixinPackage = "com.example.cherrydemo.mixin"
    priority = 900
    accessTransformers.from("src/main/cherry/cherrydemo.at")
}
