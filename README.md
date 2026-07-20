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

- **One enable flag**: `-Dcherry.enable.mixin=true` (plus back-compat aliases) turns on the whole
  engine — both the Leaves mixin/widener path and Cherry's AT path.
- **One manifest**: `cherry-plugin.json` (or the legacy `leaves-plugin.json`) can declare a `mixin`
  block, an `access-transformers` list, or both.
- **AT engine is a no-op by default**: [`Cherry.applyAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/Cherry.java)
  returns the input byte array unchanged for any class with no registered AT definition, so it is
  cheap to call unconditionally on every class the transforming classloader loads.
- **Same `.at` grammar as Horizon**: the line grammar and the ASM access-flag bit-twiddling in
  [`CherryAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/at/CherryAccessTransformers.java)
  are preserved verbatim from Horizon's `TransformerContainer`, so a `.at` file authored for
  Horizon parses and applies identically under Cherry.
- **Independent AT discovery**: [`CherryPluginResolver`](src/main/java/dev/iyanz/sourbyclip/cherry/CherryPluginResolver.java)
  scans `plugins/*.jar` on its own, rather than reusing the Leaves engine's plugin list — so a
  plugin that ships *only* an access-transformer (no `mixin` block at all) still gets picked up,
  even though the Leaves pipeline itself drops mixin-less plugins.
- **Conflict resolution**: if two ATs target the same member, Cherry keeps whichever grants the
  wider visibility and always clears `final` (see `CherryAccessTransformers#lock()`), matching
  Horizon's own conflict behaviour.

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
2. If enabled, once the mixin environment is initialized but *before* the transforming
   classloader starts loading server classes, the launcher calls
   `Cherry.initAccessTransformers()`, which delegates to
   [`CherryPluginResolver.resolveAccessTransformers()`](src/main/java/dev/iyanz/sourbyclip/cherry/CherryPluginResolver.java).
   This scans every jar in `plugins/`, reads `cherry-plugin.json` (falling back to
   `leaves-plugin.json`), registers each declared `.at` file's contents, and then **locks** the
   registry (`CherryAccessTransformers#lock()`), resolving any conflicts and freezing it before a
   single server class is loaded.
3. From then on every class load runs the three-step pipeline above.

**Conditional mixins.** Leaves' `leaves-plugin-mixin-condition` lets a `@Mixin` class carry
`@MinecraftVersion` / `@ServerBuild` annotations so it only applies on matching server builds. This
is entirely on the Leaves side of the pipeline (Cherry's AT engine has no concept of conditions);
see [Limitations](#limitations) for an important caveat about how build metadata reaches this
system on SourbyClip specifically.

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
folder or add as a Maven dependency to get mixin support.** Its two integration points —
`Cherry.applyAccessTransformers` being called from inside `MixinURLClassLoader.findClass`, and
`Cherry.initAccessTransformers` being called from the launcher's startup sequence — are compiled
directly into SourbyClip's own launcher module (`sourbyclip/java25`), in the same classloader as
Leaves' `MixinURLClassLoader`, `PluginResolver`, and `logger` classes. Cherry only works as part of
that whole launcher; it cannot be pointed at a stock Paper, Leaves, or Horizon server and "just
work" by itself.

Concretely: SourbyClip vendors Cherry's exact source (the same six files under
`src/main/java/dev/iyanz/sourbyclip/cherry/` in this repository) directly into
`sourbyclip/java25/src/main/java/dev/iyanz/sourbyclip/cherry/`, compiled as part of that module
alongside SourbyClip's own fork of Leavesclip. This repository is kept as the canonical,
independently buildable and documented copy — it is **not** currently wired up as an automated
Maven dependency that SourbyClip's build pulls in; keeping the two copies in sync today is a manual
step. Cherry targets **Paper-fork servers running the Leavesclip-based launcher** (i.e. SourbyClip
today); it has no meaning outside that launcher.

### Why this repository still needs a `hostStub` source set

Two of Cherry's own classes (`CherryPluginResolver`, `CherryAccessTransformers`) import small
Leavesclip host classes: `org.leavesmc.leavesclip.logger.{Logger,SimpleLogger}` and
`org.leavesmc.leavesclip.mixin.{LeavesPluginMeta,PluginResolver}`. These are **not published to
any Maven repository** — Leavesclip is a launcher, forked directly into SourbyClip's own source
tree, not a library artifact. So this repository cannot simply add "leavesclip" as a dependency.

To still let `./gradlew build` / `./gradlew javadoc` succeed standalone, `src/hostStub/java/`
contains minimal, explicitly-labeled compile-time stand-ins for just the members Cherry's own code
actually touches (see the javadoc on each file in that directory for exactly what is and isn't
reproduced, and why). They are wired into the build **only** as a `compileOnly`-style input to
`main`'s compile classpath — they are never referenced anywhere else, and are excluded from every
published artifact (the jar, the sources jar, and the javadoc jar all contain `main` only; see
`build.gradle.kts`). At runtime inside SourbyClip, the real classes are used instead.

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
- **Not a drop-in jar.** As covered above, Cherry only runs as vendored source inside SourbyClip's
  launcher module — this repository is the canonical/documentation copy, not a jar you can add to
  an arbitrary server's classpath.

## Building this repository

Requires JDK 25 (or newer — the toolchain block will provision one if your machine doesn't have
it). A Gradle wrapper is included, pinned to Gradle 9.5.1.

```
./gradlew build     # compiles, runs the (currently empty) test task, assembles jar + sourcesJar + javadocJar
./gradlew javadoc    # generates API docs into build/docs/javadoc (gitignored — not committed)
```

Both compile `src/main/java` (the real Cherry source) against:

- `com.google.code.gson:gson` — a genuine runtime dependency (`CherryPluginResolver` deserializes
  manifests with it), matching the version SourbyClip itself bundles.
- `org.ow2.asm:asm-tree` and `net.fabricmc:sponge-mixin` — `compileOnly`: both are always present
  on the host's runtime classpath already (SourbyClip's launcher pulls in Mixin, which pulls in
  ASM), so Cherry never bundles its own copies.
- The `hostStub` source set's output — `compileOnly`, for the reasons explained above.

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

See [NOTICE.md](NOTICE.md) for the full upstream license texts and per-file provenance.

## License

Cherry is licensed under the MIT License — see [LICENSE](LICENSE). This is a direct match for its
two upstream sources (Leavesclip's base and Horizon's `TransformerContainer`), which are both
themselves MIT-licensed; nothing GPL/AGPL-licensed is incorporated into or linked by this
repository's code.
