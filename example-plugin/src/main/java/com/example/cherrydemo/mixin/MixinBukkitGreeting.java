package com.example.cherrydemo.mixin;

import org.bukkit.Bukkit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A minimal, real {@code @Mixin} class, proving cherry-gradle-plugin's Mixin annotation-processor
 * wiring end to end: this compiles against the real {@code org.bukkit.Bukkit} class from paper-api
 * (a genuine, publicly resolvable {@code compileOnly} dependency - see {@code build.gradle.kts}), and
 * the Mixin annotation processor generates a real refmap entry for the {@code getVersion()} target
 * below as a side effect of compiling this file.
 *
 * <p>A production Cherry plugin would typically {@code @Mixin} into an internal (NMS) server class
 * instead of plain Bukkit API - that needs the author's own mapped server jar on the compile
 * classpath (e.g. via paperweight-userdev), which this repository's build does not have or fetch; see
 * the main repository README's "Authoring a Cherry plugin (Gradle)" section for exactly what this
 * example does and does not prove.
 */
@Mixin(Bukkit.class)
public class MixinBukkitGreeting {

    @Inject(method = "getVersion", at = @At("HEAD"))
    private static void cherryDemo$onGetVersion(CallbackInfoReturnable<String> cir) {
        System.out.println("[CherryDemo] Bukkit.getVersion() was called - Cherry mixin applied successfully");
    }
}
