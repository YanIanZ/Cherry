# Cherry

Cherry is SourbyCraft's unified server-side mixin system: it merges the two Paper-fork mixin
launchers that existed separately — **LeavesMC's Leavesclip** and **CraftCanvasMC's Horizon** —
into one engine, so a SourbyCraft plugin author gets Fabric-style mixins *and*
Forge/Paper-style access-transformers from a single manifest, enabled with a single system
property.

This repository is the **canonical source and documentation home** for Cherry. It is not a
drop-in standalone jar — see [How Cherry is actually consumed](#how-cherry-is-actually-consumed)
before assuming you can just add it as a dependency to an arbitrary server.

## What Cherry merges

| Capability | Source | What it gives you |
|---|---|---|
| Mixin bootstrap (SpongePowered Mixin via a Fabric-Knot `IMixinService`), plugin-mixin discovery, Fabric access-**wideners**, MixinExtras, `leaves-plugin-mixin-condition` conditional mixins | **[LeavesMC/Leavesclip](https://github.com/LeavesMC/Leavesclip)** | The base skeleton. Cherry keeps this as-is; it is what actually loads and applies `@Mixin` classes. |
| Forge/Paper-style access-**transformers** (`.at` files) | **[CraftCanvasMC/Horizon](https://github.com/CraftCanvasMC/Horizon)**'s `TransformerContainer` | The capability Leaves never had. Ported into [`CherryAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/at/CherryAccessTransformers.java) and slotted into the Leaves transforming classloader as one more step in its transform chain. |
| Fabric-ecosystem `fabric.mod.json` server-side mixin/refmap/access-widener declarations | Cherry's own [`fabric`](src/main/java/dev/iyanz/sourbyclip/cherry/fabric) package | Lets a Fabric-format plugin jar's server-side mixin configs and access-widener load too, since Leaves' own discovery only understands `leaves-plugin.json`. See [Fabric support](#fabric-support) for the exact scope. |

Leavesclip's own mixin loader only understands Fabric access-*wideners* (which can only ever
*widen* access, via a separate declarative file format). It has no equivalent of Forge/NeoForge's
or Horizon's access-*transformers*, which can widen **or narrow** access and toggle `final` on a
class, field, method, or constructor by exact JVM signature. Horizon has that capability, but
Horizon is a *whole separate launcher* — its own Paperclip fork, its own dependency resolver, its
own Java-agent-based classloading — that cannot run side-by-side with SourbyClip's Leavesclip-based
launcher. So Cherry does not merge Horizon's architecture; it ports the one thing that is portable
and additive — the AT engine itself — into Leaves' existing transforming classloader. This is
stated plainly in [Limitations](#limitations) too: **only the AT capability is merged, not
Horizon's launcher design.**

## Why

- Leaves-only plugins could widen access with an access-widener, but could not narrow it, flip
  `final`, or target a member by an exact descriptor the way Forge-ecosystem `.at` files do.
- Horizon-only plugins got access-transformers, but only by running on an entirely different,
  mutually-exclusive launcher — not an option for a server already committed to SourbyClip's
  Leavesclip fork.
- SourbyCraft plugin authors want both, from one launcher, declared in one manifest, without a
  second launcher jar or an extra operator setup step.

## Features

- **A Gradle plugin to author Cherry plugins**: `dev.iyanz.cherry` (`cherry-gradle-plugin/` in this
  repository) wires the SpongePowered Mixin annotation processor + refmap generation onto your own
  Paper-plugin project and auto-generates `cherry-plugin.json` + the SpongePowered Mixin config from
  a `cherry { }` DSL block, so nothing in the schema below needs to be hand-written. See
  [Authoring a Cherry plugin (Gradle)](#authoring-a-cherry-plugin-gradle).
- **One enable flag**: `-Dcherry.enable.mixin=true` (plus back-compat aliases) turns on the whole
  engine — both the Leaves mixin/widener path and Cherry's AT path.
- **Multi-format unified discovery**: `cherry-plugin.json`, the legacy `leaves-plugin.json`,
  `fabric.mod.json`, and the standalone `*.mixins.json` files those manifests reference are all
  scanned by one pipeline ([`CherryDiscovery`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/CherryDiscovery.java)),
  merged, and de-duplicated. See [Discovery formats](#discovery-formats) and [Fabric support](#fabric-support).
- **AT engine is a no-op by default**: [`Cherry.applyAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java)
  returns the input byte array unchanged for any class with no registered AT definition, so it is
  cheap to call unconditionally on every class the transforming classloader loads.
- **Same `.at` grammar as Horizon**: the line grammar and the ASM access-flag bit-twiddling in
  [`CherryAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/at/CherryAccessTransformers.java)
  are preserved verbatim from Horizon's `TransformerContainer`, so a `.at` file authored for
  Horizon parses and applies identically under Cherry. (Horizon's grammar has no wildcard-member or
  extra descriptor-matching syntax beyond what was already ported — verified directly against
  Horizon's source; there was nothing further to port.)
- **Independent AT discovery**: [`CherryPluginResolver`](src/main/java/dev/iyanz/sourbyclip/cherry/CherryPluginResolver.java)
  scans `plugins/*.jar` on its own, rather than reusing the Leaves engine's plugin list — so a
  plugin that ships *only* an access-transformer (no `mixin` block at all) still gets picked up,
  even though the Leaves pipeline itself drops mixin-less plugins.
- **Conflict resolution**: if two ATs target the same member, Cherry keeps whichever grants the
  wider visibility and always clears `final` (see `CherryAccessTransformers#lock()`), matching
  Horizon's own conflict behaviour. The same "keep the first, warn about the rest" discipline
  applies to two plugins declaring a mixin config under the identical resource name (see
  [`CherryDiscovery`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/CherryDiscovery.java)).
- **Priority-ordered mixin configs**: every discovered mixin config's SpongePowered Mixin `priority`
  field is read and used to sort [`DiscoveryReport.mixinConfigs()`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/DiscoveryReport.java)
  deterministically (ascending, default `1000` when omitted, matching Mixin's own default) — see
  [Discovery formats](#discovery-formats).
- **Per-plugin kill-switch**: `-Dcherry.disable.plugins=SomePlugin,AnotherPlugin` disables just those
  plugins' Cherry-owned declarations (access-transformers, Fabric-format mixins/wideners) without
  disabling Cherry entirely and without repackaging the plugin — alongside the existing global
  `-Dcherry.enable.mixin` switch. See [Reliability & diagnostics](#reliability--diagnostics).
- **Dry-run and status API**: [`Cherry.dryRun()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java)
  reports what would load without registering/extracting/locking anything, and
  [`Cherry.status()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java) layers that with what
  is actually active — both usable whether or not Cherry is enabled. See
  [Reliability & diagnostics](#reliability--diagnostics).
- **Non-fatal, isolated failures everywhere**: a malformed manifest, an unparseable `.at`/mixin-config
  line, a missing declared file, or one broken plugin jar is logged with the offending plugin and
  reason and skipped — never fatal, never affecting any other plugin. See
  [Reliability & diagnostics](#reliability--diagnostics).

## How it works

Cherry hooks into the Leaves transforming classloader (`MixinURLClassLoader`) as one more
transform step, positioned between the SpongePowered Mixin transform and the Fabric
access-widener pass:

```
class bytes → [1] Mixin transform → [2] Cherry AT engine → [3] access-widener → defineClass
```

1. **Mixin transform** — SpongePowered Mixin's `IMixinTransformer` applies any registered
   `@Mixin` classes to the class being defined. This step is entirely Leaves'; Cherry does not
   touch it.
2. **Cherry AT engine** — [`Cherry.applyAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java)
   is called on the (possibly mixin-transformed) bytes. It looks up the class's internal name in
   [`CherryAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/at/CherryAccessTransformers.java)'s
   registry; if there is no entry, the bytes pass through unchanged. If there is one, the class is
   parsed into an ASM `ClassNode`, each matching `AtDefinition` rewrites the access flags of the
   target class/field/method, and the node is re-serialized.
3. **Access-widener** — Leaves' own `AccessWidenerManager` applies any Fabric access-wideners.
4. The final byte array is handed to `defineClass`.

**Startup sequence**, driven by the host launcher (not by Cherry itself):

1. The launcher checks [`Cherry.enabled()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java).
   If disabled, none of the above runs and classes load unmodified.
2. If enabled, the launcher drives Cherry's init entry points. There are two timing-distinct calls
   (see [`Cherry`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java)'s class javadoc for the
   full "three tiers" breakdown):
   - **Before constructing the transforming classloader** — `Cherry.initFabricMixins()` (or the
     combined `Cherry.init(pluginsDir, cacheDir)`) must run here, because
     [`Cherry.fabricJarUrls()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java) needs to be
     merged into the classloader's classpath at construction time — see
     [Fabric support](#fabric-support) for exactly why.
   - **After the mixin environment is initialized, before any server class loads** —
     `Cherry.initAccessTransformers()` (the original, already-integrated call) discovers and commits
     access-transformers via
     [`CherryPluginResolver.resolveAccessTransformers()`](src/main/java/dev/iyanz/sourbyclip/cherry/CherryPluginResolver.java),
     then **locks** the registry (`CherryAccessTransformers#lock()`), resolving any conflicts and
     freezing it before a single server class is loaded.

   Both paths start from the same [`CherryDiscovery.scan()`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/CherryDiscovery.java)
   pipeline described in [Discovery formats](#discovery-formats). `Cherry.init()` runs both in one
   call and logs the one combined `Cherry ready: N mixin plugin(s), M access-transformer(s), K
   widener(s)` summary line.
3. From then on every class load runs the three-step pipeline above.

**Conditional mixins.** Leaves' `leaves-plugin-mixin-condition` lets a `@Mixin` class carry
`@MinecraftVersion` / `@ServerBuild` annotations so it only applies on matching server builds. This
is entirely on the Leaves side of the pipeline (Cherry's AT engine has no concept of conditions);
see [Limitations](#limitations) for an important caveat about how build metadata reaches this
system on SourbyClip specifically.

## Discovery formats

[`CherryDiscovery`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/CherryDiscovery.java) is the
one pipeline behind every entry point above: it scans every `.jar` under `plugins/` and reads
whichever of these manifest formats a jar declares, merging the results and de-duplicating across
plugins.

| Format | Declares | Read by | Who registers it |
|---|---|---|---|
| `cherry-plugin.json` (preferred) | `mixin` block (Leaves engine), `access-transformers` list (Cherry AT engine) | Cherry (own scan, independent of Leaves') | Leaves engine (mixin block) + Cherry (AT list) |
| `leaves-plugin.json` (legacy back-compat; ignored if `cherry-plugin.json` is also present) | same `mixin`/`access-transformers` shape | same as above | same as above |
| `fabric.mod.json` | `mixins` array (config file names or `{config, environment}` objects), `accessWidener` field | Cherry only — Leaves has no notion of this format | [`CherryFabricBridge`](src/main/java/dev/iyanz/sourbyclip/cherry/fabric/CherryFabricBridge.java) (see [Fabric support](#fabric-support)) |
| standalone `*.mixins.json` (the SpongePowered Mixin config files the two formats above reference) | `package`, `refmap`, `priority`, and the actual `mixins`/`client`/`server` class lists | Cherry, to read `package`/`refmap`/`priority` ([`MixinConfigMeta`](src/main/java/dev/iyanz/sourbyclip/cherry/manifest/MixinConfigMeta.java)); Mixin itself, to actually apply the config | Whoever registered the containing manifest's config reference |

Every declaration discovered this way becomes a
[`DiscoveredMixinConfig`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/DiscoveredMixinConfig.java)/
[`DiscoveredAccessTransformer`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/DiscoveredAccessTransformer.java)/
[`DiscoveredAccessWidener`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/DiscoveredAccessWidener.java)
in one [`DiscoveryReport`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/DiscoveryReport.java),
with mixin configs sorted by their SpongePowered Mixin `priority` field (ascending, default `1000`
when a config omits it). Anything found but not included — a missing declared file, a duplicate
resource name claimed by two different plugins, a disabled plugin, a Fabric config missing its
required `package` field, a corrupt jar — is recorded in `DiscoveryReport.skipped()` with a reason,
never silently dropped and never fatal to the rest of the scan.

## Fabric support

Cherry's discovery additionally understands the Fabric-ecosystem manifest format, so a plugin jar
built the Fabric way (a `fabric.mod.json` alongside SpongePowered Mixin config files) can still load
its **server-side** mixins, refmap, and access-widener under Cherry — without shipping a
`cherry-plugin.json` at all.

### What's supported

- **`fabric.mod.json`'s `mixins` array.** Each entry is either a bare config-file-name string
  (applies universally) or an object `{"config": "...", "environment": "client"|"server"|"*"}`.
  Cherry honors `environment`: only `server` and `*` (universal) entries are loaded; `client`-only
  entries are recorded in [`DiscoveryReport.skipped()`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/DiscoveryReport.java)
  with a clear reason and never touched — Cherry runs on a dedicated server, which never has a
  client side to load one for.
- **Refmaps.** A referenced `*.mixins.json`'s own `refmap` field (the standard SpongePowered Mixin
  config field Fabric mods rely on for obfuscation-mapped mixins) is read via
  [`MixinConfigMeta`](src/main/java/dev/iyanz/sourbyclip/cherry/manifest/MixinConfigMeta.java) and,
  when the referenced refmap file actually exists in the jar, staged alongside the config and its
  mixin classes by [`CherryFabricBridge`](src/main/java/dev/iyanz/sourbyclip/cherry/fabric/CherryFabricBridge.java)
  so Mixin can find it on the classpath. A refmap declared but missing from the jar degrades
  gracefully — Mixin itself falls back to an identity reference map and Cherry logs a clear warning
  — it is never treated as fatal.
- **Fabric access-wideners.** `fabric.mod.json`'s top-level `accessWidener` field is extracted the
  same way and its resource name exposed via
  [`Cherry.fabricAccessWidenerConfigs()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java), to
  be merged into the same Leaves `AccessWidenerManager` pipeline that a `cherry-plugin.json`'s
  `mixin.access-widener` field already feeds.
- **Extraction + caching.** Because Leaves' own plugin pipeline has no notion of `fabric.mod.json`,
  Cherry runs its own independent extraction
  ([`CherryFabricBridge`](src/main/java/dev/iyanz/sourbyclip/cherry/fabric/CherryFabricBridge.java)):
  for each contributing plugin, it copies the config, its refmap (if present), the widener (if
  present), and every class file under the config's declared `package` prefix into one cached jar
  under `plugins/.cherry-mixins/`, keyed by an MD5 hash of the source plugin jar (the same
  hash-and-skip-if-unchanged scheme Leaves' own `PluginResolver` uses for its `.mixins.jar` cache),
  and removes cache jars for plugins no longer discovered.

### Integration contract

`Cherry.initFabricMixins()` populates three accessors the host launcher merges into the same places
Leaves' own `MixinJarResolver` fields already feed:

| Accessor | Merge into | Timing |
|---|---|---|
| [`Cherry.fabricJarUrls()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java) | the URL array passed to `MixinURLClassLoader`'s constructor | **before** that classloader is constructed |
| [`Cherry.fabricMixinConfigNames()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java) | `Mixins.addConfiguration(String)`, one call per name, after the jar URLs above are on the classpath | after `MixinBootstrap.init()` |
| [`Cherry.fabricAccessWidenerConfigs()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java) | whatever list feeds `AccessWidenerManager.initAccessWidener` | before that call |

This mirrors, on purpose, exactly how `Leavesclip.main` already consumes
`MixinJarResolver.jarUrls`/`mixinConfigs`/`accessWidenerConfigs` — a host integration that already
wires up that pattern for Leaves' own pipeline has minimal new glue to write for Cherry's Fabric
bridge.

### Honest scope

**This loads Fabric-format server-side mixin/access-widener/config declarations into the server's
existing SpongePowered Mixin environment. It does not run a Fabric mod loader.** Concretely, Cherry
does **not**:

- resolve Fabric mod dependencies, run mod entrypoints, or do anything with `fabric.mod.json` fields
  other than `mixins` and `accessWidener`;
- run any client-side code or client-only mixin config (filtered out at discovery, see above);
- provide the Fabric API, Fabric's item/block/registry systems, or any other part of a real Fabric
  mod's runtime environment — a jar built as a genuine Fabric mod will not run as a mod under Cherry,
  only the server-side mixin/AT/AW declarations it happens to also carry will be loaded.

If a plugin needs actual Fabric mod functionality (not just mixins), Cherry is not a substitute for
a real Fabric mod loader.

## Reliability & diagnostics

- **Nothing here can crash boot.** A malformed `cherry-plugin.json`/`leaves-plugin.json`/
  `fabric.mod.json`, an unparseable `.at` line or mixin-config JSON, a declared file missing from
  the jar, a corrupt jar, or one plugin's Fabric extraction failing are all caught, logged with the
  offending plugin/jar and a specific reason, and skipped — every other plugin's declarations still
  load. See [`CherryDiscovery`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/CherryDiscovery.java)'s
  and [`CherryFabricBridge`](src/main/java/dev/iyanz/sourbyclip/cherry/fabric/CherryFabricBridge.java)'s
  class javadoc for the exhaustive list of what is isolated and how.
- **Thread-safe AT registry.** [`CherryAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/at/CherryAccessTransformers.java)
  builds definitions into a private staging map during the single-threaded discovery phase, then
  `lock()` publishes one fully immutable snapshot through a single `volatile` reference. Every
  `applyToBytes` call (which runs on whatever thread is loading a class, potentially many
  concurrently) takes one volatile read and then only ever sees a complete, unmodifiable registry —
  there is no shared mutable state, and no locking, on that hot path.
- **Logging discipline.** One concise summary at ready
  (`Cherry ready: N mixin plugin(s), M access-transformer(s), K widener(s)`, from
  [`DiscoveryReport.summaryLine()`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/DiscoveryReport.java)),
  per-item detail at DEBUG (e.g. every individual AT application), and precise WARN/ERROR lines that
  always name the offending plugin and the reason.
- **Global kill-switch**: `-Dcherry.enable.mixin=true` (plus aliases) — unchanged, existing.
- **Per-plugin kill-switch**: `-Dcherry.disable.plugins=SomePlugin,AnotherPlugin`
  ([`CherryPluginFilter`](src/main/java/dev/iyanz/sourbyclip/cherry/discovery/CherryPluginFilter.java))
  — exact, case-sensitive name match, disables only that plugin's Cherry-owned declarations
  (access-transformers, Fabric-format mixins/wideners). It does not affect a Leaves-handled `mixin`
  block, which is outside Cherry's control — see [Limitations](#limitations).
- **Dry-run**: [`Cherry.dryRun()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java) /
  `Cherry.dryRun(File)` runs the exact same discovery as a real boot, without registering,
  extracting, or locking anything — safe to call whether or not Cherry is enabled, for previewing
  what a new plugin (or flipping the enable flag) would load.
- **Status API**: [`Cherry.status()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java) /
  `Cherry.status(File)` returns a [`CherryStatus`](src/main/java/dev/iyanz/sourbyclip/cherry/CherryStatus.java)
  combining a fresh dry-run with what the AT and Fabric engines actually have active right now
  (locked/registered counts, extracted configs/wideners). This is the data source intended for a
  future `/cherry` operator command — this repository adds the API only, not the command.

## Authoring a Cherry plugin (Gradle)

Before this module existed, authoring a Cherry plugin meant hand-wiring SpongePowered Mixin's
annotation processor onto your own source set, hand-writing `cherry-plugin.json`, hand-writing a
SpongePowered Mixin config JSON, and hoping the refmap the annotation processor generated actually
lined up with all of the above — the single biggest adoption blocker for Cherry. **`cherry-gradle-plugin/`**
is a Gradle plugin, applied to *your own* Paper-plugin project, that does all of that for you: it
wires the Mixin annotation processor + refmap generation onto your `main` source set, and
auto-generates `cherry-plugin.json` and the SpongePowered Mixin config from a small `cherry { }`
DSL block. **This replaces the manual annotation-processor + hand-written-JSON path** described in
[Plugin author guide](#plugin-author-guide) below for any project willing to build with Gradle — that
section is still accurate and still the right reference for the exact schema this plugin's output
targets, and for Fabric-format or non-Gradle authors.

This repository is not just documentation for that plugin — it also *builds* it, and proves it works
against a real example. See [Building this repository](#building-this-repository) for how the three
Gradle projects here (`cherry` the root/library, `cherry-gradle-plugin/`, `example-plugin/`) fit
together.

### Applying the plugin

```kotlin
// build.gradle.kts, in YOUR OWN Paper-plugin project (not this repository)
plugins {
    java
    id("dev.iyanz.cherry") version "1.0.0"
}
```

`dev.iyanz.cherry` is published the same way this repository's own root artifact is documented under
[How Cherry is actually consumed](#how-cherry-is-actually-consumed) — as a real, independently buildable
Gradle module, not (yet) pushed to Gradle's Plugin Portal. Resolve it either via
[JitPack](https://jitpack.io) (see this repository's `jitpack.yml`, which builds
`cherry-gradle-plugin/` specifically — see the comment in that file for why the root project alone
isn't enough) by adding `maven("https://jitpack.io")` to your `pluginManagement.repositories` and
depending on `com.github.YanIanZ:Cherry:<commit-or-tag>` as the artifact backing the `dev.iyanz.cherry`
plugin ID, or by publishing this repository's `cherry-gradle-plugin/` to your own Maven repository
with `./gradlew -p cherry-gradle-plugin publishToMavenLocal` (or `publish`, once you configure a real
`publishing.repositories` target in that module) and adding `mavenLocal()` (or your repository) to
`pluginManagement.repositories`.

### The `cherry { }` DSL

```kotlin
cherry {
    // Written into cherry-plugin.json's "name" field. Defaults to the Gradle project's name.
    pluginName = "MyPlugin"

    // The dot-separated package (under this project's own compiled classes) holding your @Mixin
    // classes. Required for a mixin block at all - a plugin with only accessTransformers below may
    // leave this unset. Setting it is what turns on the Mixin annotation-processor/refmap wiring,
    // the compileOnly/annotationProcessor Mixin+MixinExtras dependencies, and the
    // generateCherryMixinConfig task - see "What gets generated" below.
    mixinPackage = "com.me.myplugin.mixin"

    // Optional. Extra, already-existing SpongePowered Mixin config file names (e.g. hand-authored,
    // shipped as a resource) to reference in cherry-plugin.json's mixin.mixins list ALONGSIDE the
    // one this plugin generates from mixinPackage above. Rarely needed.
    mixinConfigs.set(listOf("legacy.mixins.json"))

    // Optional SpongePowered Mixin config fields, written straight into the generated config -
    // Cherry itself only reads package/refmap/priority (see MixinConfigMeta), but real Mixin also
    // reads minVersion/compatibilityLevel/required once the config is registered.
    priority = 900
    minVersion = "0.8"
    compatibilityLevel = "JAVA_21"
    required = true // default; set false to make every mixin in this config optional

    // Access-transformer (.at) files, in Cherry/Horizon's grammar (see "Access-transformer (.at)
    // file format" below) - validated against that exact grammar at build time (the build fails
    // with the offending file/line on a syntax error, rather than Cherry's runtime "log and skip").
    accessTransformers.from("src/main/cherry/myplugin.at")

    // Optional Fabric access-widener file(s) - Cherry's manifest only has room for one
    // (mixin.access-widener), so more than one input file here is merged (see AccessWidenerMerger);
    // requires mixinPackage to be set (access-widener only ever lives inside the mixin block).
    accessWideners.from("src/main/cherry/myplugin.accesswidener")
}

dependencies {
    // Whatever your @Mixin classes actually target - cherry-gradle-plugin does not add this for
    // you (see cherry.serverApi below for the equivalent helper), since it varies per plugin.
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
}
```

`cherry.serverApi("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")` is an equivalent convenience
method on the extension for the dependency block above — a thin `compileOnly(...)` wrapper, added
because there is no separate published "Cherry API" artifact to add alongside it (see
[How Cherry is actually consumed](#how-cherry-is-actually-consumed) — Cherry is vendored source, not
a library your mixins call into).

### What gets generated

Running `./gradlew build` on a project with the plugin applied produces, inside the built jar, all
of the following **without you writing any of them by hand**:

| Generated file | From | Jar path |
|---|---|---|
| `cherry-plugin.json` | the whole `cherry { }` block | jar root |
| the SpongePowered Mixin config (e.g. `mixins.myplugin.json`) | `mixinPackage` + every compiled `@Mixin` class found under it (via bytecode scan — see `MixinClassScanner`) + `priority`/`minVersion`/`compatibilityLevel`/`required` | jar root |
| the refmap (e.g. `mixins.myplugin.refmap.json`) | the real SpongePowered Mixin annotation processor, run as part of `compileJava` via `-AoutRefMapFile` | jar root |
| your `.at` file(s) | `accessTransformers`, validated then copied through unchanged | jar root, own file name |
| your access-widener | `accessWideners`, copied through (or merged, if more than one) | jar root, `accessWidenerName` (defaults to `<project-name>.accesswidener`) |

Your compiled `@Mixin` classes and the rest of your plugin, plus your own `paper-plugin.yml`, are in
the jar exactly as they always would be — the plugin only *adds* the five items above; it never
changes how you write the rest of your plugin. Every one of the generated files targets exactly the
schema documented in [Plugin author guide](#plugin-author-guide) below, byte for byte — this module
exists specifically so nothing about that schema needs to be memorized or copy-pasted by hand.

### Pinned versions

`compileOnly` + `annotationProcessor` get `net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7` added
automatically (only when `mixinPackage` is set), and `compileOnly` gets
`io.github.llamalad7:mixinextras-common:0.5.0`. Both are pinned to match what Cherry's runtime
actually bundles: the Mixin version matches this repository's own root `build.gradle.kts`
dependency exactly (the same build Cherry's runtime — vendored into SourbyClip's launcher — compiles
and runs against), and the MixinExtras version matches LeavesMC/Leavesclip's own `java21` module
(Cherry's mixin base), which vendors `io.github.llamalad7:mixinextras-common:0.5.0` directly rather
than a Fabric-loader-specific bootstrap variant. Compiling your mixins against any other version
risks refmap/annotation drift against what the server actually loads at runtime — see the task this
module was built from, and `CherryGradlePlugin.MIXIN_VERSION`/`MIXIN_EXTRAS_VERSION` for the single
source of truth.

### A note on the annotation processor's obfuscation checks

The Mixin annotation processor ships with a Forge/MCP-era `ObfuscationServiceMCP` that always
registers a "notch" obfuscation environment, regardless of any option — there is no way to opt out
of the environment itself. Since Cherry/Leavesclip never remaps mixins (targets are plain
Mojang-mapped Paper/NMS classes, not Forge/MCP-obfuscated ones — see [How it works](#how-it-works)),
every `@Inject`/`@Shadow`/etc. target is reported as missing obfuscation data for that irrelevant
environment on every single compile. javac's own default severity for that diagnostic is **ERROR**
unless the annotation processor happens to detect an IDE compiler environment (which was observed,
while building this module, to be non-deterministic across otherwise-identical Gradle invocations —
passing once, then failing on every subsequent clean build in the same checkout). `cherry-gradle-plugin`
therefore unconditionally downgrades every `NO_OBFDATA_FOR_*` message to a non-fatal note via the
annotation processor's own `-AMSG_<MessageType>=note` mechanism, so this never surfaces to a plugin
author at all. You do not need to do anything about this — it is documented here only so the refmap
`Note:` lines you may still see in build output make sense (they are expected and harmless).

### Your environment must still provide

- **The actual API/server jar your `@Mixin` classes target**, via your own `compileOnly` dependency
  (or `cherry.serverApi(...)`) — this repository's `example-plugin/` targets plain `org.bukkit.Bukkit`
  from a publicly resolvable `paper-api` dependency specifically so it stays buildable without extra
  setup; a real Cherry plugin targeting internal (NMS) server classes instead needs your own mapped
  server jar on that same classpath (e.g. via `paperweight-userdev` or an equivalent dev bundle) —
  `cherry-gradle-plugin` does not fetch, patch, or remap a server jar for you.
- **A JDK new enough for whatever server version you target** — this repository's own `example-plugin`
  needs JDK 25 purely because the `paper-api` version it depends on does; `cherry-gradle-plugin`
  itself only requires JDK 21 to run.

### Verifying it end to end

`./gradlew :example-plugin:build` in this repository builds a minimal, real Cherry plugin
(`example-plugin/`) that applies this plugin, contains one real `@Mixin` class
(`MixinBukkitGreeting`, injecting into `org.bukkit.Bukkit#getVersion()`) and one `.at` file, and a
normal `paper-plugin.yml`. Unzipping the result (`example-plugin/build/libs/example-plugin-1.0.0.jar`)
shows exactly the five generated/staged files from the table above sitting alongside the compiled
plugin — this is the same thing `./gradlew build` at this repository's root verifies as part of its
own build.

## Plugin author guide

A Cherry-enabled plugin is, first, a **normal Paper plugin** — it still ships a `paper-plugin.yml`
(or legacy `plugin.yml`) like any other plugin. Cherry only adds one more file to the jar:
`cherry-plugin.json` at the jar root (or `leaves-plugin.json`, kept for back-compat with
Leaves-only plugins that predate Cherry).

### Manifest schema

```jsonc
// cherry-plugin.json  — at the root of the plugin jar, alongside paper-plugin.yml
{
  // Plugin name. Used for logging and to namespace registered AT definitions
  // (e.g. "MyPlugin:widener.at").
  "name": "MyPlugin",

  // Optional. Consumed by the Leaves mixin engine — untouched by Cherry's own code.
  "mixin": {
    // Java package (dot-separated) inside this jar that holds your @Mixin classes.
    // Leaves extracts everything under this package prefix into a cached .mixins.jar.
    "package-name": "com.me.myplugin.mixin",

    // One or more SpongePowered Mixin config JSON files (the usual Mixin config format:
    // package, refmap, mixins list, etc.), resolved relative to the jar root.
    "mixins": ["mixins.myplugin.json"],

    // Optional. A Fabric access-widener file (.accesswidener), resolved relative to the jar root.
    "access-widener": "myplugin.accesswidener"
  },

  // Optional. Cherry's own addition — one or more access-transformer (.at) files, resolved
  // relative to the jar root. Present with or without a "mixin" block above.
  "access-transformers": ["myplugin.at"]
}
```

Both `mixin` and `access-transformers` are optional and independent: a plugin can declare only
`access-transformers` (pure AT, no mixins at all — this is exactly the case
`CherryPluginResolver`'s independent jar scan exists to support), only `mixin`, or both.

### Fabric manifest schema (alternative to `cherry-plugin.json`)

A plugin built the Fabric-ecosystem way can skip `cherry-plugin.json` entirely and declare its
server-side mixins/access-widener in `fabric.mod.json` instead — see [Fabric support](#fabric-support)
for the full scope and the honest limitations (this loads mixins/AW only, not a Fabric mod runtime).
A plugin may ship both a `cherry-plugin.json` *and* a `fabric.mod.json`; Cherry reads both.

```jsonc
// fabric.mod.json — at the jar root, the standard Fabric mod-metadata file
{
  "id": "myplugin",

  // Each entry is either a bare config-file name (applies to every environment) or an object with
  // an explicit "environment". Cherry loads "server" and "*" entries; "client"-only entries are
  // skipped (recorded in DiscoveryReport.skipped() with a reason, never an error).
  "mixins": [
    "myplugin.common.mixins.json",
    {"config": "myplugin.server.mixins.json", "environment": "server"},
    {"config": "myplugin.client.mixins.json", "environment": "client"}
  ],

  // Optional. Same Fabric access-widener format Leaves already supports via mixin.access-widener,
  // just declared the Fabric way.
  "accessWidener": "myplugin.accesswidener"
}
```

```jsonc
// myplugin.server.mixins.json — a standard SpongePowered Mixin config, referenced above
{
  "package": "com.me.myplugin.mixin",  // required - Cherry extracts every class under this package
  "refmap": "myplugin.refmap.json",    // optional - staged alongside the config if present in the jar
  "priority": 1000,                     // optional - defaults to 1000 (SpongePowered Mixin's own default)
  "mixins": ["MixinSomeMob"]
}
```

### Access-transformer (`.at`) file format

One definition per line; `#` starts a trailing comment; blank lines are ignored. A definition is
`<modifier>[+f|-f] <fully.qualified.ClassName> [<member>]`:

| Target | Line shape | Example |
|---|---|---|
| Class | `<modifier>[+f|-f] <fqcn>` | `public com.example.SomeInternalClass` |
| Field | `<modifier>[+f|-f] <fqcn> <fieldName>` | `public net.minecraft.world.entity.Mob goalSelector` |
| Constructor | `<modifier>[+f|-f] <fqcn> <init>(<params>)V` | `public com.example.Foo <init>(Ljava/lang/String;)V` |
| Method | `<modifier>[+f|-f] <fqcn> <name>(<params>)<ret>` | `public-f net.minecraft.world.entity.Mob tickLeash()V` |

`<modifier>` is one of `public`, `protected`, `private`, `default` (package-private). The optional
`+f` / `-f` suffix (directly appended, no space — e.g. `public-f`) additionally *adds* or *removes*
the `final` modifier. Method and constructor targets use the member's JVM descriptor (not a Java
source signature), matched against ASM's `MethodNode.name` + `MethodNode.desc`.

Example `myplugin.at`:

```
# widen a package-private field to public
public net.minecraft.world.entity.Mob goalSelector

# widen a method to public and drop `final` so it can be overridden
public-f net.minecraft.world.entity.SomeMob tickLeash()V

# narrow an accidentally-public internal class back to package-private
default com.example.internal.Helper
```

### Where files go in the jar

```
myplugin.jar
├── paper-plugin.yml                 # normal Paper plugin descriptor — required regardless of Cherry
├── cherry-plugin.json               # Cherry's unified manifest (this repo's contribution)
├── myplugin.at                      # referenced by "access-transformers" above
├── mixins.myplugin.json             # SpongePowered Mixin config, referenced by "mixin.mixins"
├── myplugin.accesswidener           # referenced by "mixin.access-widener"
└── com/me/myplugin/
    ├── MyPlugin.class                # your normal plugin code
    └── mixin/                        # the "mixin.package-name" package — your @Mixin classes
        └── MixinSomeMob.class
```

A Fabric-format plugin looks the same, with `fabric.mod.json` (and its referenced `*.mixins.json`
files) instead of/alongside `cherry-plugin.json`:

```
myplugin.jar
├── paper-plugin.yml                 # still a normal Paper plugin, regardless of manifest format
├── fabric.mod.json                  # Fabric-ecosystem manifest (see Fabric support)
├── myplugin.server.mixins.json      # referenced by fabric.mod.json's "mixins"
├── myplugin.refmap.json             # referenced by the config's own "refmap" field, if present
├── myplugin.accesswidener           # referenced by fabric.mod.json's "accessWidener"
└── com/me/myplugin/
    ├── MyPlugin.class
    └── mixin/                        # the config's "package" field - your @Mixin classes
        └── MixinSomeMob.class
```

### Enabling the engine

Nothing runs unless the server is started with the JVM property that gates
[`Cherry.enabled()`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java):

```
-Dcherry.enable.mixin=true
```

Back-compat aliases (any one of these also enables it, checked in this order):

```
-Dcherry.enable.mixin=true
-Dsourbyclip.enable.mixin=true
-Dleavesclip.enable.mixin=true
```

## How Cherry is actually consumed

Cherry is **not published as a standalone runtime jar that any server can drop into a plugins
folder or add as a Maven dependency to get mixin support.** Its integration points —
`Cherry.applyAccessTransformers` being called from inside `MixinURLClassLoader.findClass`, and
`Cherry.initAccessTransformers` being called from the launcher's startup sequence — are compiled
directly into SourbyClip's own launcher module (`sourbyclip/java25`), in the same classloader as
Leaves' `MixinURLClassLoader`, `PluginResolver`, and `logger` classes. Cherry only works as part of
that whole launcher; it cannot be pointed at a stock Paper, Leaves, or Horizon server and "just
work" by itself.

At the time of writing, only those two integration points are actually wired into SourbyClip's
launcher. The Fabric bridge and diagnostics API this repository adds (`Cherry.initFabricMixins()`/
`Cherry.init()`/`Cherry.status()`/`Cherry.dryRun()` and their accessors) exist in Cherry's source and
are fully covered by this repository's own tests, but are not yet called from SourbyClip's launcher
— wiring them in (per the [Fabric support](#fabric-support) integration contract) is a separate,
future sync step, tracked outside this repository.

Concretely: SourbyClip vendors Cherry's exact source (every file under
`src/main/java/dev/iyanz/sourbyclip/cherry/` in this repository — the root package plus the
`at`/`discovery`/`fabric`/`manifest` sub-packages) directly into
`sourbyclip/java25/src/main/java/dev/iyanz/sourbyclip/cherry/`, compiled as part of that module
alongside SourbyClip's own fork of Leavesclip. This repository is kept as the canonical,
independently buildable and documented copy — it is **not** currently wired up as an automated
Maven dependency that SourbyClip's build pulls in; keeping the two copies in sync today is a manual
step. Cherry targets **Paper-fork servers running the Leavesclip-based launcher** (i.e. SourbyClip
today); it has no meaning outside that launcher.

### Why this repository still needs a `hostStub` source set

Several of Cherry's own classes (`CherryPluginResolver`, `CherryAccessTransformers`,
`CherryDiscovery`) import small Leavesclip host classes: `org.leavesmc.leavesclip.logger.{Logger,SimpleLogger}`
and `org.leavesmc.leavesclip.mixin.{LeavesPluginMeta,PluginResolver}`. These are **not published to
any Maven repository** — Leavesclip is a launcher, forked directly into SourbyClip's own source
tree, not a library artifact. So this repository cannot simply add "leavesclip" as a dependency.

To still let `./gradlew build` / `./gradlew javadoc` succeed standalone, `src/hostStub/java/`
contains minimal, explicitly-labeled compile-time stand-ins for just the members Cherry's own code
actually touches (see the javadoc on each file in that directory for exactly what is and isn't
reproduced, and why). They are wired into the build **only** as a `compileOnly`-style input to
`main`'s compile classpath (plus `test`'s classpath, so this repository's own unit tests can
actually execute the code they exercise — see [Building this repository](#building-this-repository))
— they are never referenced anywhere else, and are excluded from every published artifact (the jar,
the sources jar, and the javadoc jar all contain `main` only; see `build.gradle.kts`). At runtime
inside SourbyClip, the real classes are used instead.

**Sync note.** `LeavesPluginMeta`'s `accessTransformers` field now carries an explicit
`@SerializedName("access-transformers")`. Without it, Gson's default (literal, no kebab-case
conversion) field-naming silently never binds the documented `"access-transformers"` manifest key —
`getAccessTransformers()` always returns `null` even for a correct manifest. This was an actual bug
in this stub, caught by this repository's own tests once they started asserting on parsed
access-transformer lists; if SourbyClip's real, vendored `LeavesPluginMeta` is missing the same
annotation, it has the identical bug and needs the same one-line fix when this repository's changes
are synced in. The stub's `LeavesPluginMeta.MixinConfig` nested class (added for
`CherryDiscovery`'s reporting of Leaves-declared mixin configs/wideners) mirrors upstream
Leavesclip's shape exactly and needs no corresponding SourbyClip-side change — see that class's
javadoc.

## Limitations

- **Only the AT capability is merged, not Horizon's architecture.** Horizon is a separate launcher
  with its own Paperclip fork and Java-agent-based classloading; none of that runs inside
  SourbyClip. Only the portable, additive part — the access-transformer engine itself — was ported.
- **Lazy, non-fatal target validation.** Horizon's original validated AT targets against its own
  mixin service during `lock()`. Cherry cannot do that (SourbyClip's `MixinServiceKnot` is not
  Horizon's `BootstrapMixinService`), so a missing field/method target is only discovered — and
  only logged, never fatal — the first time that class is actually loaded and the AT is applied.
  A typo'd AT line will not fail the server at startup; it will silently do nothing at the point
  the target class loads.
- **No `it.unimi.dsi.fastutil` maps.** Horizon's original registry used fastutil collections;
  Cherry uses plain JDK `HashMap`/`HashSet` since fastutil isn't on the clip classpath. This has no
  behavioural effect, only a (negligible, class-loading-time-only) memory/performance one.
- **Conditional-mixin build metadata is best-effort on SourbyCraft.** Leaves' conditional-mixin
  system (`@MinecraftVersion` / `@ServerBuild`) reads build metadata that SourbyClip's launcher
  (`BuildInfoInjector`, outside this repository) resolves through three fallback tiers: Leaves'
  own `/META-INF/build-info`, then a synthesized `sourbycraft-build.properties`, then the
  Paperclip `version.json` (Minecraft version only). If none are present, it falls back to a
  placeholder with **build number 0**. A mixin gated on `@ServerBuild` may therefore never match on
  a SourbyCraft build that hasn't stamped its own build-info file, even though the mixin itself is
  loaded correctly.
- **Process-wide, single-registry AT engine.** `CherryAccessTransformers.INSTANCE` is one static
  singleton locked once at startup; there is no per-plugin or per-classloader isolation, and no
  way to register new ATs after the initial scan.
- **Fabric support loads mixins/AW only, not a Fabric mod runtime.** No mod loader, no entrypoints,
  no dependency resolution, nothing client-side — see [Fabric support](#fabric-support)'s "Honest
  scope" for the full statement. A jar built as a genuine Fabric mod will not run as one under
  Cherry.
- **Duplicate mixin-config resource names are resolved by scan order, not negotiation.** If two
  different plugins declare a config under the identical file name, the one from the
  alphabetically-first jar wins and the other is skipped with a logged reason
  (`DiscoveryReport.skipped()`) — this is deterministic but arbitrary from a plugin author's
  perspective; avoid generic config file names to prevent collisions with other plugins entirely.
- **Cherry's priority ordering governs its own bookkeeping and registration call order, not Mixin's
  transform ordering.** SpongePowered Mixin already sorts registered configs by their own `priority`
  field when it *applies* mixins, regardless of what order `Mixins.addConfiguration` was called in.
  Cherry additionally reads and sorts by the same field so its own discovery report, status output,
  and Fabric-config registration order are deterministic and human-readable — this is a genuinely
  useful ordering guarantee for Cherry's own output, not a mechanism that overrides or duplicates
  Mixin's own priority handling.
- **Not a drop-in jar.** As covered above, Cherry only runs as vendored source inside SourbyClip's
  launcher module — this repository is the canonical/documentation copy, not a jar you can add to
  an arbitrary server's classpath.

## Building this repository

Requires JDK 25 (or newer — the toolchain block will provision one if your machine doesn't have
it). A Gradle wrapper is included, pinned to Gradle 9.5.1.

```
./gradlew build     # compiles, runs the JUnit test suite, assembles jar + sourcesJar + javadocJar
./gradlew test       # just the test suite (src/test/java)
./gradlew javadoc    # generates API docs into build/docs/javadoc (gitignored — not committed)
```

`src/main/java` (the real Cherry source) compiles against:

- `com.google.code.gson:gson` — a genuine runtime dependency (`CherryPluginResolver`/`CherryDiscovery`
  deserialize manifests with it), matching the version SourbyClip itself bundles.
- `org.ow2.asm:asm-tree` and `net.fabricmc:sponge-mixin` — `compileOnly`: both are always present
  on the host's runtime classpath already (SourbyClip's launcher pulls in Mixin, which pulls in
  ASM), so Cherry never bundles its own copies.
- The `hostStub` source set's output — `compileOnly`, for the reasons explained above.

`src/test/java` holds real JUnit 5 tests for every pure-logic piece: AT DSL parsing and conflict
resolution (including a real-bytecode round trip through ASM and a concurrent-access smoke test),
`fabric.mod.json`/`*.mixins.json` parsing, multi-format discovery de-duplication and priority
ordering, the per-plugin kill-switch, and the Fabric mixin/widener extraction-and-caching mechanics
(built against real, temporary fixture jars — see `src/test/java/.../testutil/JarBuilder.java`).
This is appropriate here even though SourbyCraft server sub-specs avoid JUnit in favor of booting a
real server: this is a standalone library repository, verified by `./gradlew test`, not a
SourbyCraft server sub-spec. What full runtime mixin-loading inside SourbyClip's actual launcher
still cannot be exercised standalone (see [Limitations](#limitations) and [How Cherry is actually
consumed](#how-cherry-is-actually-consumed)) is verified manually, in SourbyClip itself, after a
sync.

Tests additionally depend on `org.junit.jupiter:junit-jupiter`, and re-add `org.ow2.asm:asm-tree` /
`net.fabricmc:sponge-mixin` / the `hostStub` output as test-scoped dependencies, since this
repository's own test execution has no host launcher supplying them at runtime the way SourbyClip
does in production — see the comments in `build.gradle.kts` for exactly why each one is there. None
of this affects the published `main`/sources/javadoc jars.

### Java 25 compatibility

SourbyCraft runs its servers on Java 25, so this repository (and specifically the `cherry` root/
library module vendored into SourbyClip) is verified against it end to end. For a mixin project, the
real risk of "Java 25 compatibility" is not the `javac` target — it is whether the ASM library that
SpongePowered Mixin's bytecode transform is built on actually understands Java 25's class file format
(major version **69**). An ASM release too old for it doesn't warn or degrade gracefully: it throws
`IllegalArgumentException: Unsupported class file major version 69` the instant it tries to read a
Java-25-compiled class, which would break every mixin applied to a Java 25 server, independent of what
JDK compiled Cherry's own classes.

- **Verified, not assumed**: `net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7` — the version this
  repository pins, and the newest build currently published on `repo.spongepowered.org` (there is no
  newer one to bump to) — itself transitively depends on `org.ow2.asm:asm` / `asm-tree` /
  `asm-commons` / `asm-util`, all at **9.8** (`./gradlew dependencies --configuration
  compileClasspath`). ASM 9.8 is the first ASM release that supports Java 25: its shipped
  `Opcodes.class` bakes in `V25 = 69`, and — confirmed directly here, not just cited — an ASM 9.8
  `ClassReader` parses one of this repository's own Java-25-compiled (`major version: 69`) class
  files with no exception, while the same input thrown at the older ASM 9.7.1 (see below) throws
  exactly the `Unsupported class file major version 69` error described above.
- This repository's root `build.gradle.kts` used to pin `org.ow2.asm:asm-tree:9.7.1` directly (for
  `CherryAccessTransformers`' own compile-time use of ASM's tree API). Gradle's normal
  highest-version-wins conflict resolution was already silently bumping that up to 9.8 wherever
  `sponge-mixin` was also on the same classpath (i.e. everywhere it mattered for actually reading
  Java 25 bytecode at runtime/test-runtime) — but the stale explicit `9.7.1` was still misleading to
  read and could diverge on classpaths that don't also pull in `sponge-mixin` (e.g.
  `testCompileClasspath`, compile-time only, never affects behavior). It is now pinned explicitly to
  `9.8`, matching what is actually resolved and used.
- `cherry-gradle-plugin/`'s own `MixinClassScanner` build-time tool separately, and intentionally,
  pins a newer `org.ow2.asm:asm:9.10.1` — it has to read whatever bytecode version the *plugin
  author's own* project toolchain produces (this repository's own `example-plugin` already targets
  JDK 25), independent of whatever Cherry's own runtime pin happens to be.
- **Gradle-plugin toolchain**: `cherry-gradle-plugin/` itself still targets JDK 21 — deliberately
  lower than Cherry's own JDK 25 runtime target, since a Gradle plugin should run inside *any*
  consumer's Gradle daemon rather than force one specific JDK. This is forward-, not just backward-,
  compatible: the whole repository (root project, the `cherry-gradle-plugin` included build, and
  `example-plugin`, which itself targets JDK 25) was rebuilt end to end —
  `clean`/`build`/`javadoc`/`test` — with the Gradle daemon itself running on JDK 25, with Gradle
  auto-provisioning the JDK 21 compile toolchain the plugin module asks for from that same daemon. No
  actual incompatibility was found, so it was deliberately left at 21 rather than bumped to 25 (bumping
  it would narrow, not widen, which Gradle daemons can apply the plugin).
- One real (unrelated to Java 25 itself) bug surfaced while running `javadoc` on JDK 25: a stray `|`
  character in place of a closing `}` inside a `{@code annotationProcessor}` inline tag in
  `CherryGradlePlugin.java` made `cherry-gradle-plugin`'s `javadoc` task fail outright (`unterminated
  inline tag` / `element not closed: ol`). Fixed.
- Net result: `./gradlew clean build javadoc test` is green on both Gradle builds in this repository
  (47 tests in the root project, 40 in `cherry-gradle-plugin`, 0 failures), with the Gradle daemon and
  every toolchain involved running on JDK 25.

### The Gradle-plugin and example-plugin modules

Two more Gradle projects live in this repository, alongside the `cherry` root/library project
covered above — see [Authoring a Cherry plugin (Gradle)](#authoring-a-cherry-plugin-gradle) for what
they are and how a plugin author uses the first one:

- **`cherry-gradle-plugin/`** — the `dev.iyanz.cherry` Gradle plugin itself. It is a *separate*
  Gradle build (its own `settings.gradle.kts`), wired into this repository's root
  `settings.gradle.kts` via `pluginManagement { includeBuild("cherry-gradle-plugin") }` — a normal
  composite-build "plugin included build", the same pattern Gradle's own documentation uses for a
  plugin developed alongside a project that consumes it. This is also what makes qualified task paths
  like `./gradlew :cherry-gradle-plugin:build` work directly from this repository's root.
  - `./gradlew -p cherry-gradle-plugin build` (or `./gradlew :cherry-gradle-plugin:build` from the
    root) compiles it, runs its JUnit 5 test suite (pure-logic tests for manifest/mixin-config JSON
    generation, `.at` grammar validation, access-widener merging, and the `@Mixin` bytecode scanner —
    see `src/test/java` in that module — plus a `ProjectBuilder`-based functional test asserting the
    extension/task/dependency wiring `CherryGradlePlugin.apply` performs), and runs Gradle's own
    `validatePlugins` check (`java-gradle-plugin`'s built-in sanity check on task
    caching/input-output annotations).
  - `./gradlew -p cherry-gradle-plugin publishToMavenLocal` publishes it (plus its plugin-marker
    artifact) to `~/.m2/repository/dev/iyanz/cherry/` — verified while building this module. See
    [Applying the plugin](#applying-the-plugin) for how an external consumer resolves it from there
    (or from JitPack — see this repository's `jitpack.yml`).
- **`example-plugin/`** — a minimal, real Cherry plugin (applies `dev.iyanz.cherry`, one real
  `@Mixin` class, one `.at` file, a normal `paper-plugin.yml`) that exists purely to prove the whole
  toolchain end to end. `./gradlew :example-plugin:build` builds it; unzip
  `example-plugin/build/libs/example-plugin-1.0.0.jar` to see the generated
  `cherry-plugin.json`/mixin config/refmap sitting alongside the compiled plugin — see
  [Verifying it end to end](#verifying-it-end-to-end) above.

`./gradlew build` at this repository's root builds all three projects together (the `cherry` root
project, `example-plugin` as a regular subproject, and `cherry-gradle-plugin` transitively, since
`example-plugin` applying `id("dev.iyanz.cherry")` requires resolving and compiling it).

## Credits / attribution

Cherry is a derivative work combining code from two upstream projects, both MIT-licensed:

- **[LeavesMC/Leavesclip](https://github.com/LeavesMC/Leavesclip)** — the mixin-loader base
  (`org.leavesmc.leavesclip.mixin.*` / `org.leavesmc.leavesclip.logger.*`) Cherry's classes
  integrate with and this repository's `hostStub` reproduces the compile-time surface of.
  Licensed MIT — `Copyright (c) 2021 Kyle Wood (DenWav)`, `Copyright (c) 2023 LeavesMC` (per
  [`licenses/license.txt`](https://github.com/LeavesMC/Leavesclip/blob/master/licenses/license.txt)
  in that repository; Leavesclip itself is a fork of
  [PaperMC/Paperclip](https://github.com/PaperMC/Paperclip)). Leavesclip's Mixin support also
  incorporates code from [FabricMC/fabric-loader](https://github.com/FabricMC/fabric-loader)
  (Apache License 2.0) and [LlamaLad7/MixinExtras](https://github.com/LlamaLad7/MixinExtras) (MIT).
- **[CraftCanvasMC/Horizon](https://github.com/CraftCanvasMC/Horizon)** — `TransformerContainer`,
  ported into [`CherryAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/at/CherryAccessTransformers.java).
  Licensed MIT — `Copyright (c) 2025 CanvasMC`.
- **[SpongePowered/Mixin](https://github.com/SpongePowered/Mixin)** (via its Fabric fork,
  `net.fabricmc:sponge-mixin`) — MIT licensed — provides the `ILogger`/`Level` types the Leavesclip
  logger classes (and this repository's `hostStub` reproduction of them) build on.

Cherry's [Fabric support](#fabric-support) (the `manifest`, `discovery`, and `fabric` packages) is
an original, independent implementation of the publicly documented `fabric.mod.json` and
SpongePowered Mixin config JSON **schemas** — no code is copied from FabricMC/fabric-loader or any
other Fabric-ecosystem project; only the openly published file-format shapes are read, with Gson
models Cherry wrote itself.

See [NOTICE.md](NOTICE.md) for the full upstream license texts and per-file provenance.

## License

Cherry is licensed under the MIT License — see [LICENSE](LICENSE). This is a direct match for its
two upstream sources (Leavesclip's base and Horizon's `TransformerContainer`), which are both
themselves MIT-licensed; nothing GPL/AGPL-licensed is incorporated into or linked by this
repository's code.
