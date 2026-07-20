# Notice

Cherry is a derivative work combining original SourbyCraft code with code ported/adapted from two
upstream projects. All three are MIT-licensed; this file records the per-file provenance and
reproduces the relevant upstream license texts in full, in addition to the stacked copyright
notices in [LICENSE](LICENSE).

## LeavesMC / Leavesclip

- Repository: <https://github.com/LeavesMC/Leavesclip>
- License: MIT (`licenses/license.txt` in that repository)
- Copyright: `(c) 2021 Kyle Wood (DenWav)`, `(c) 2023 LeavesMC` (Leavesclip is itself a fork of
  [PaperMC/Paperclip](https://github.com/PaperMC/Paperclip), hence the DenWav copyright line)

Cherry's own classes (`CherryPluginResolver`, `CherryAccessTransformers`) import and integrate with
Leavesclip's `org.leavesmc.leavesclip.logger.{Logger,SimpleLogger}` and
`org.leavesmc.leavesclip.mixin.{LeavesPluginMeta,PluginResolver}`, which are part of SourbyClip's
vendored copy of Leavesclip and are **not part of this repository**. Because Leavesclip is a
launcher (not a published library), this repository's `src/hostStub/java` directory reproduces a
minimal, explicitly-labeled compile-time-only subset of those classes' API surface so that Cherry's
own source can be compiled and documented standalone — see the javadoc on each file in that
directory, and the "How Cherry is actually consumed" section of [README.md](README.md), for exactly
what is and is not reproduced and why. None of `src/hostStub` is packaged into any artifact this
repository publishes.

Leavesclip's own Mixin support additionally incorporates code from:

- **[FabricMC/fabric-loader](https://github.com/FabricMC/fabric-loader)** — Apache License 2.0.
  Cherry's own six source files do not import Fabric Loader directly; it is part of the
  surrounding Leavesclip mixin bootstrap.
- **[LlamaLad7/MixinExtras](https://github.com/LlamaLad7/MixinExtras)** — MIT License. Same as
  above: part of the surrounding Leaves mixin engine, not imported by Cherry's own classes.

```
The MIT License (MIT)

Copyright (c) 2021 Kyle Wood (DenWav)
Copyright (c) 2023 LeavesMC

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## CraftCanvasMC / Horizon

- Repository: <https://github.com/CraftCanvasMC/Horizon>
- License: MIT (`LICENSE` in that repository)
- Copyright: `(c) 2025 CanvasMC`

[`CherryAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/at/CherryAccessTransformers.java),
[`AtDefinition`](src/main/java/dev/iyanz/sourbyclip/cherry/at/AtDefinition.java), and
[`AccessChange`](src/main/java/dev/iyanz/sourbyclip/cherry/at/AccessChange.java) are ported from
Horizon's `io.canvasmc.horizon.transformer.widener` package (`TransformerContainer`,
`Definition`, `TransformOperation`), de-branded into Cherry. The `.at` line grammar and the ASM
access-flag bit-twiddling are preserved verbatim. Differences from the original, made to run
standalone inside SourbyClip's Leaves-based launcher instead of Horizon's own architecture, are
documented in each class's javadoc and summarized in [README.md](README.md)'s Limitations section
(no `fastutil` collections, lazy non-fatal target validation instead of Horizon's mixin-service
validation pass, and the added `applyToBytes(byte[])` entry point for Leaves'
`MixinURLClassLoader`).

```
MIT License

Copyright (c) 2025 CanvasMC

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Fabric mod-metadata format (fabric.mod.json / SpongePowered Mixin config JSON)

Cherry's Fabric-format support (`dev.iyanz.sourbyclip.cherry.manifest`,
`dev.iyanz.sourbyclip.cherry.discovery`, and `dev.iyanz.sourbyclip.cherry.fabric`) reads two openly
documented JSON **schemas** — the Fabric mod-metadata format (`fabric.mod.json`) and the
SpongePowered Mixin config format (`*.mixins.json`) — using Gson models written from scratch for
this repository. No source code is copied or adapted from FabricMC/fabric-loader, FabricMC/Yarn, or
any other Fabric-ecosystem project; only the publicly published file-format shapes (field names such
as `mixins`, `accessWidener`, `package`, `refmap`, `priority`, and their documented value shapes) are
read. This section exists purely for completeness/transparency, not because any upstream license
obligation applies here — see the repository README's [Fabric support](README.md#fabric-support)
section for the exact scope of what is (and is not) implemented.

## SpongePowered / Mixin

- Repository: <https://github.com/SpongePowered/Mixin> (consumed via its Fabric fork,
  `net.fabricmc:sponge-mixin`, pinned to `0.17.3+mixin.0.8.7` — the newest build currently published
  — a `compileOnly` build dependency of this repository)
- License: MIT

Not modified or vendored by Cherry or by this repository; listed here because
`src/hostStub/java/org/leavesmc/leavesclip/logger/{Logger,SimpleLogger}.java` (see the Leavesclip
section above) implement Mixin's `ILogger` interface and use its `Level` enum.

**Java 25 (class file major version 69) compatibility**: `net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7`
transitively pulls `org.ow2.asm:asm`/`asm-tree`/`asm-commons`/`asm-util` version **9.8** — the first
ASM release able to parse Java 25 class files (`Opcodes.V25 = 69`) that SpongePowered Mixin's
bytecode transform relies on. Verified directly (not just by citation): an ASM 9.8 `ClassReader`
parses one of this repository's own Java-25-compiled classes with no error, whereas the previously
(explicitly, but never actually at runtime) pinned ASM 9.7.1 throws `IllegalArgumentException:
Unsupported class file major version 69` on the identical input. See the README's
[Java 25 compatibility](README.md#java-25-compatibility) section for the full account.

## ASM (org.ow2.asm)

- Repository: <https://gitlab.ow2.org/asm/asm>
- License: BSD-3-Clause

Not modified or vendored by Cherry or by this repository. Consumed transitively via
`net.fabricmc:sponge-mixin` (see above, which pulls ASM 9.8) and directly pinned as a
`compileOnly`/test-scoped build dependency (`org.ow2.asm:asm-tree:9.8`, matching the version
`sponge-mixin` itself requires) for [`CherryAccessTransformers`](src/main/java/dev/iyanz/sourbyclip/cherry/at/CherryAccessTransformers.java)'s
own bytecode rewriting. Never bundled into any artifact this repository publishes — in production the
host (SourbyClip's launcher) always supplies ASM at runtime via Mixin, the same way it supplies Mixin
itself.
