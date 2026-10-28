# Changelog

Note: 0.9.0 is the version that fixes the low FPS problem. On a GT1030 it performs on par with vanilla and is playable, based on in-game testing.

Note: 0.8.8 is the first version intended to initialize git history. Earlier versions were experimental archives used while the renderer changed rapidly, so they were not added to git history.

## 0.12.2 - Terrain loading

- Put region pages and per-section terrain meshes into a few large shared GPU buffers and send everything built in one frame to bgfx as one update per buffer. In `renderBench uploads` on a GT 1030 every separate buffer update costs about 0.4 ms of bgfx render thread time regardless of its size, so rebuilding several pages, creating translucent section meshes and re-sorting translucent indices each cost a separate update before. Sixteen 160 KiB pages per frame took 8.2 ms that way and take 1.7 ms now.
- Re-sorted translucent indices go to a new range of the shared index buffer instead of updating each section's own buffer, and freed ranges are reused only after the next frame.
- Rebuild up to 32 regions or 4 MiB of pages per frame instead of 6 regions, and allow 16 section mesh creations and 32 index updates per frame instead of 4 each. Before, sections waiting for their region to be rebuilt were streamed every frame within a 6 MiB budget, and the sections over the budget were not drawn in that frame, which showed as missing or late terrain while chunks loaded. This is what the `skipped draws sections` counter of the performance log counted.
- With `-Ddx12mc.profile.cpu=true` the performance log also reports the game thread time per part of the frame, the bgfx render thread time, and per-frame counts of rebuilds, streamed sections, uploads and skipped draws by reason. It costs nothing when off.
- Add a development flight harness (`runHarness`) that flies a fixed route in a fresh world in a temporary run directory and logs frame times per phase. It is not part of the mod jar.
- In that harness on a GT 1030 at render distance 12, alternating runs of this version and 0.12.1 gave 138 and 92 fps while flying over new terrain, 217 and 191 fps over generated terrain, 191 and 160 fps on a second pass, and 286 fps for both when standing still. Undrawn sections while flying fell from about 1,400 to 9,000 per 5 seconds to 0 to 1,500. Vanilla OpenGL rendering in the same harness reached 231, 281, 247 and 332 fps. These are benchmark results, not in-game measurements, and the frame benchmark capture has no differing pixels.

## 0.12.1 - API and shader packs

- Add an experimental API for other mods (API version 0.1) in the `dx12mc-api` jar, which is also bundled in the mod. Mods register through a `dx12mc` Fabric entrypoint and can read whether the renderer is active, its version and the adapter, supply uniform values, and get notified when shader packs load. The API must be used on the render thread. See `docs/API.md`.
- Load shader packs from resource packs (`assets/<namespace>/dx12mc/pack.json`) with precompiled shaders. A pack can replace the programs of terrain and entity render types, add programs, uniforms and textures, render the world into a G-buffer of up to three extra colour targets, sample the scene colour and depth, and run fullscreen passes before translucent terrain, after the world and before the GUI. Packs reload with F3+T.
- Check every pack shader before it reaches bgfx: container header, shader contract, profile, bgfx binary layout, DXBC checksum, stage, vertex attributes, varyings, uniforms and sampler slots. A pack that fails a check or fails while running is disabled with a log line and the built-in shaders are used. Packs from a server are not loaded unless `-Ddx12mc.pack.allowServer=true` is set, and packs may use at most 256 MiB of targets and textures.
- Build normal and specular atlases from `_n` and `_s` companion textures with the block atlas layout and mip levels, only when an active pack asks for them.
- Add `compileShaderPack`, which compiles a pack with the pinned shaderc, and a sample pack under `examples/sample-pack` with a tint override and a normal and roughness override.
- The built-in fragment shaders have G-buffer variants that are loaded only for a pack with a G-buffer. The default shader binaries are unchanged.
- In the `renderBench` frame scenario on a GT 1030, alternating runs of this version and 0.12.0 without a pack gave 3.34 ms and 3.36 ms of GPU time, and the capture has no differing pixels. The sample pack cost about 0.55 ms more, a tint override 0.1 ms and a G-buffer pack with one pass 0.66 ms. These are benchmark results, not in-game measurements.
- Add `renderBench` pixel checks for overrides, rejected binaries, the G-buffer, depth reads, custom passes, material atlas placement and releasing pack resources on reload.

## 0.12.0 - Native render targets and post-processing

- Mirror Minecraft's offscreen render targets as bgfx frame buffers. A target gets a colour texture and, when Minecraft asked for one, a depth buffer the first time it is bound or sampled, and is released when Minecraft destroys it. Draws aimed at a target are drawn into it instead of being skipped, and clears of a target are applied to its own view. Before, every draw aimed at a render target other than the main one was dropped.
- Run `PostChain.process` on Direct3D 12 instead of skipping it. The `box_blur`, `entity_outline`, `entity_outline_box_blur`, `blit`, `invert`, `color_convolve`, `bits` and `spider` programs are ported to D3D12 shaders, and each pass reads its uniform values from the vanilla program and post chain JSON. A chain with a program that has no port (Fabulous `transparency`) is skipped with a log line.
- Enable Menu Background Blur again; its slider is no longer disabled. Frames that show a blur or a spectator effect draw the main target offscreen and copy it to the window at the end; all other frames are drawn as before.
- Draw glowing entity outlines and the spectator effects (creeper, spider, enderman) again.
- Fabulous graphics and panorama screenshots are still not supported. A saved Fabulous setting still loads as Fancy.
- Make `rendertype_entity_alpha` (the ender dragon death beams) output the raw texture colour like the vanilla shader. Before, it multiplied in the vertex colour and the shader colour.
- In the `renderBench` frame scenario on a GT 1030, alternating runs of this version and 0.11.2 gave 4.28 ms and 4.55 ms of GPU time on average, within the noise of the GPU clock, and the capture has no differing pixels. With the main target redirected and copied back the frame costs about 0.8 ms more, and with the six-pass blur about 4.5 ms more. These are benchmark results, not in-game measurements.
- Add `renderBench` pixel checks for the render target round trip, box blur, entity outline mask, creeper and enderman colour passes, target resize, swap chain reset, closing a target twice and the redirected main frame, and `effect=` and `compare=` options for the frame scenario.

## 0.11.2 - Build fixes

- Rebuild the compiled D3D12 shaders when the pinned shaderc hash, shader profile or optimization level changes. Before, a changed compiler left stale shader binaries in place because only the shader sources were tracked.
- Put the compiled shaders on the main output, so a development run (`runClient`) finds them as well as the jar. Before, they were added to the jar only.
- Run Gradle in the `build.yml` workflow on the runner JDK with `-Dorg.gradle.java.home`, the same as `publish.yml`, instead of the machine-specific path in `gradle.properties`.
- Fix `ReleaseWorkflowTest`, which still checked an older tag pattern than the one `publish.yml` uses.

## 0.11.1 - End portal and End Poem background

- Draw the end portal and the end gateway with a port of the vanilla `rendertype_end_portal` shader instead of a plain white fill. The shader layers the end sky and end portal textures with Minecraft's per-layer colours, rotation, scale and game-time scroll (15 layers for the portal, 16 for the gateway). Previously these draws had no texture coordinates, so they fell back to the plain colour program and came out solid white.
- The End Poem screen draws its background with the same render type, so it uses the same fix.
- Add `rendertype_end_gateway` to the vanilla shader table; before it fell back to the default entry.
- F3, crash reports and the log now include the graphics driver version and the Direct3D feature level of the adapter bgfx opened. Both are read once at startup from DXGI and the Direct3D 12 device; NVIDIA's four-part driver number is shown in its usual form, other vendors show the raw version, and a value that cannot be read is left out.

## 0.11.0 - Smaller vertices and release publishing

- Store vertex light coordinates (UV2) as 16-bit integers instead of 32-bit floats. Terrain, GUI and other non-entity vertices shrink from 32 to 28 bytes, and entity and line vertices from 44 to 40 bytes. The shaders read the same unsigned values as before, so the rendered output does not change. In the new `renderBench` frame scenario on a GT 1030, GPU frame time went from 3.36 ms to 3.30 ms. This is a benchmark result, not an in-game measurement.
- Add a `renderBench` frame scenario (`-PbenchArgs=frame`) shaped like a render-distance-12 frame: about 1.8 million terrain vertices in region pages, sky, entities, water, particles, clouds, the hand and the GUI behind their depth clears. `profile` reports GPU time per pass, `f3` adds debug-screen text, and `capture=<name>` saves the frame to `build/frame-bench/` for exact image comparisons.
- Select the signing key by fingerprint instead of e-mail address.
- Publish releases from a version tag: the workflow builds and tests the tagged commit, publishes the signed jar, sources jar, javadoc jar and POM to GitHub Packages, and creates the GitHub release with notes taken from this changelog. No files are attached to the release.

## 0.10.0 - Rendering fidelity

- Translate Minecraft's mid-frame OpenGL clears to Direct3D 12. Each clear starts a new bgfx view, so the depth clears before the first-person hand and before the GUI take effect, and the level is cleared to the fog colour Minecraft sets.
- Translate immediate draws (GUI, HUD, text, entities, particles, hand) with Minecraft's actual depth, cull, colour-mask and blend state instead of a fixed compatibility state that always tested and wrote depth and forced alpha blending.
- Reproduce each vanilla core shader's fragment rules: the alpha discard threshold (alpha 0 for GUI, 0.1 for text, entities and particles, 0.5 for `cutout_mipped`) and whether it tests the texture alone, red-channel intensity text, and the lightmap lookup (filtered per vertex for terrain, texel fetch for entities, particles and text).
- Add vanilla fog to every D3D12 program that uses it, from Minecraft's fog start, end, colour and shape, in the same distance space and mode (linear or fade) as the vanilla shader. This covers render-distance, water, lava, powder snow, blindness, darkness, nether, end and boss fog.
- Light entities and items with Minecraft's diffuse light directions and normals, and apply the hurt and flash overlay, through a separate entity vertex layout. Terrain keeps its 32-byte layout.
- Take texture filtering, wrapping and the mip range from Minecraft's OpenGL texture parameters. The lightmap is now sampled with linear filtering, the block atlas blends between mip levels, and sampling never goes past the last uploaded mip level.
- Mirror single-channel textures the way OpenGL samples them (red only), so unicode-font glyphs are no longer drawn as solid boxes.
- Skip draws aimed at offscreen render targets and OpenGL post chains (glowing-entity outlines, spectator effects) instead of drawing them onto the screen. Menu Background Blur stays disabled.
- Draw Minecraft's line geometry. Lines (block outline, F3 axes, hitboxes, fishing line) are expanded to screen-space quads the way `rendertype_lines` does, and debug lines (F3+G chunk borders) are drawn as line primitives. Previously block outlines and the F3 axes were omitted, and fishing lines and debug line strips ended the game.
- F2 screenshots and singleplayer world icons are read back from the Direct3D 12 frame through bgfx and saved by Minecraft's own screenshot code, with the same file names and chat messages.
- Draw the text selection highlight (`RenderType.guiTextHighlight`), which uses OpenGL's `OR_REVERSE` colour logic operation. bgfx has no logic-op state, so it is drawn as two blend passes that give `src | ~dst` for vanilla's highlight colour. Previously selecting text in an edit box (Ctrl+A or shift-select) or drawing the creative inventory search box with a selection ended the game. Other logic operations, which vanilla does not use, still stop with a precise error.

## 0.9.2 - Video settings, F3 and license change

- VSync in Video Settings now sets the D3D12 present mode through bgfx and takes effect on the next frame. `-Ddx12mc.vsync` is no longer read, so VSync follows the vanilla option, which defaults to on.
- Lowering Mipmap Levels now removes the previous mip levels from the D3D12 texture mirror instead of keeping them.
- Glint Strength now applies to D3D12 enchantment glint draws.
- Graphics no longer offers Fabulous while D3D12 renders, because it needs OpenGL post-processing that DX12MC does not support. A saved Fabulous setting loads as Fancy.
- Menu Background Blur is shown disabled with the reason, because DX12MC does not render menu blur.
- F3 shows the Direct3D 12 adapter vendor and name and `Direct3D 12 (DX12MC <version>)` instead of the startup OpenGL context, and its GPU percentage comes from bgfx GPU timing. Crash reports name the D3D12 adapter under Backend API.
- Relicense the source code from GPL-3.0 to LGPL-3.0-only and add a NOTICE file. The GPL-3.0 text is kept as `LICENSE-GPL-3.0` because the LGPL incorporates it. Earlier releases remain available under the GPL-3.0 terms they were published with.

## 0.9.1 - Review fixes

- While a terrain region waits for its rebuild, draw only the ranges of sections that are still current. Previously the whole stale page could be drawn, which showed geometry of removed or replaced sections next to their streamed replacement until the rebuild ran.
- Route failures while flushing a terrain section layer through the strict fatal path, so they are logged consistently and native resources are released.
- Treat staging backpressure during a region rebuild as a deferral instead of reporting it as a rebuild failure.

## 0.9.0 - Low FPS fix

Combines the 0.8.17 and 0.8.18 changes as released together.

- Merge dirty texture regions per mip level before upload, so animated atlas sprites no longer issue many bgfx texture updates per frame.
- Build region pages with one vertex buffer and a shared quad index buffer, and draw either a whole page or only the visible index ranges of its sections, whichever costs less.
- Limit region rebuilds (6 per frame), resident section mesh creations (4 per frame) and sorted translucent GPU index updates (4 per frame).
- Regions waiting for a rebuild keep drawing their previous page. Sections added since then are streamed within a per-frame byte budget, and a transient draw that does not fit is skipped for that frame instead of ending the game.
- Skip, instead of failing, a draw that samples a texture that has been allocated but has not received pixels yet. A missing texture still fails and reports the mirror state and recent deletions.
- Raise the bgfx transient buffer limits to 24 MiB vertex and 8 MiB index.
- Pin the shaderc download to a SHA-256 and fail the build on a mismatch.
- Average bgfx timings over each PERF interval and report texture updates and skipped draws in the PERF log line.
- Add `renderBench` and `cpuBench` Gradle tasks for local diagnostics. They are not part of the mod jar.

## 0.8.18 - Crash fixes for transient exhaustion and unready textures

- Fix the fatal "bgfx/D3D12 rejected immediate submission" crash during chunk loading. Streaming sections of regions waiting for a rebuild used up the bgfx transient buffer pool, so the next particle, entity or GUI draw failed. Streaming is now limited to a per-frame byte budget (`dx12mc.stream.bytesPerFrame`), and a transient draw that does not fit is skipped for that frame instead of ending the game.
- Regions waiting for a rebuild keep drawing their previous page, and only sections added since the last rebuild are streamed.
- A page build failure no longer corrupts the section ranges of the previous page, and the failure is counted and logged in the PERF output.
- A draw that samples a texture which was allocated but has not received pixels yet is skipped for that frame instead of ending the game. Draws with a missing texture still fail and now report the mirror state and recent deletions.
- Raise the region rebuild limit to 6 per frame and limit GPU index updates for sorted translucent sections to 4 per frame (`dx12mc.mesh.indexUpdatesPerFrame`).
- Report skipped draws by cause in the PERF log line.

## 0.8.17 - Render-thread stall fixes

- Coalesce dirty texture regions per mip level before upload. Each bgfx texture update costs about 0.27 ms on the render thread regardless of size, so animated atlas sprites previously issued 100 or more updates per frame. Tune with `dx12mc.texture.updateCallCostPixels`.
- Draw a whole region page when most of it is visible and only the visible index ranges when most of it is hidden, using a draw-call cost model. Tune with `dx12mc.region.drawCostIndices`.
- Limit terrain region rebuilds (`dx12mc.region.rebuildsPerFrame`, default 4) and resident section mesh creations (`dx12mc.mesh.createsPerFrame`, default 4) per frame. Regions waiting for a rebuild stream their visible sections through transient buffers instead of creating per-section GPU copies.
- Build region pages with one vertex buffer and a shared quad index buffer, replacing a per-page index buffer. Page creation is about 2.8 times cheaper in the benchmark.
- Raise bgfx transient buffer limits to 24 MiB vertex and 8 MiB index (`dx12mc.transient.vertexMiB`, `dx12mc.transient.indexMiB`).
- Average bgfx timings over each PERF interval and report texture updates per frame in the PERF log line.
- Pin the shaderc download to a SHA-256 and fail the build on a mismatch.
- Log failures while releasing native bridge resources at warn level instead of debug.
- Add `renderBench` and `cpuBench` Gradle tasks that measure bgfx submission and CPU-side mesh paths on the local machine.

## 0.8.16 - Region culling and shutdown hardening

- Close every bridge resource during shutdown even if one close fails, so a single error no longer leaks the remaining native handles.
- Reject mesh batches that declare indices without vertices and make batch size checks overflow-safe.
- Draw only the index ranges of visible sections inside a resident terrain region instead of the whole region page, so frustum and cave culling apply again and hidden geometry is no longer submitted.
- Pack vertex colors with a single write and hoist per-vertex attribute checks out of the staging loop.

## 0.8.15 - Remove duplicate OpenGL frame work

- Stop binding, viewport-setting and clearing Minecraft's unused OpenGL render target after D3D12 takes ownership of presentation.
- Suppress mirrored fixed-function OpenGL driver changes after recording their D3D12 state, removing duplicate per-frame GL calls from the native path.
- Constrain terrain regions to one vertical section so surface rendering cannot pull hidden underground or ocean geometry into a visible region draw.
- Emit a compact five-second performance sample to `latest.log`, including actual presented FPS, native submissions, geometry, region efficiency and bgfx CPU/GPU/wait timing.
- Translate Minecraft polygon offset to a native shader depth bias so block-overlay rendering no longer terminates strict native mode.

## 0.8.14 - Compiled-terrain region batching

- Combine nearby solid and cutout compiled sections into bounded persistent region pages, replacing hundreds of per-section D3D12 submissions with a much smaller set while preserving translucent section ordering.
- Rebuild dirty regions once per render-layer pass and retain clean pages across frames, with pressure-safe fallback to the original visible section submissions.
- Drop redundant per-section GPU copies after a region becomes resident, while retaining CPU mirrors for chunk updates and recovery without doubling terrain VRAM.
- Protect regions used in the current or previous frame from cross-layer LRU churn and size the entry cache to cover the tested render distance; enforce a separate 256 MiB resident-byte bound.
- Raise the bounded native staging allowance from 16 MiB to 64 MiB so several region pages can upload during rapid flight without unnecessary one-frame deferrals.
- Add terrain-region collection, page-submission, rebuild, deferral, residency and GPU-byte counters to the optional native diagnostics overlay.

## 0.8.13 - Immediate-draw frame-pacing cleanup

- Reuse growable CPU attribute and index buffers for immediate entity, particle, weather, text and GUI geometry instead of allocating and discarding up to seven large primitive arrays for every draw.
- Track logical vertex/index counts separately from reusable array capacity so transient uploads, statistics and memory accounting never process stale scratch data.
- Generate implicit triangle, quad, strip and fan indices directly into reusable storage while preserving exact-sized CPU mirrors for persistent compiled-section meshes.
- Preserve constant shader uniforms, textures, samplers and render state across compiled-section submissions within the same render layer; update only the per-section transform and geometry bindings.
- Stop manual F2 screenshots at the semantic screenshot entry point in native mode and show a non-fatal message instead of crashing on an unsupported OpenGL texture readback.

## 0.8.12 - Remove legacy GL calls from native terrain draws

- Stop uploading Minecraft's OpenGL `ChunkOffset` uniform for every compiled-section draw. The offset is already consumed by the D3D12 section transform, so the legacy upload was duplicate driver work.
- Stop binding and unbinding OpenGL vertex arrays inside the compiled-section layer after D3D12 takeover. The native mesh is bound by bgfx and never consumes the legacy VAO.
- Resolve the section shader, textures, lightmap, render state, tint and texture matrix once per render layer instead of repeating the same lookups for every visible section.
- Keep render-layer setup and shader state observation intact so D3D12 state, texture, cutout and lightmap selection retain vanilla semantics.

## 0.8.11 - Section draw hot-path cleanup

- Precompute section-layer clip transforms and framebuffer dimensions once per layer so visible sections avoid repeated lifecycle, window and matrix work.
- Add a section-specific native draw path while preserving resident mesh reuse, transient dynamic sections, streaming spill behavior and strict D3D12 error handling.
- Cache clean texture resolve lookups and reuse ready GPU textures without repeating upload bookkeeping.
- Avoid scanning every index array on submission by deriving index width from the validated batch vertex count.
- Publish resident mesh stats only when resident byte totals actually change.
- Remove Java source comments after the optimization pass.

## 0.8.10 - Frame pacing diagnostics

- Split renderer lifecycle, section transforms, draw-context resolution, pipeline ownership, mesh residency, texture GPU synchronization, mip dirty tracking, state encoding, and overlay formatting into cohesive components while preserving the existing Minecraft-facing APIs and render behavior.
- Disabled VSync by default so missed refresh deadlines do not quantize performance into large FPS steps. It remains available with `-Ddx12mc.vsync=true`.
- Limited the optional native diagnostic overlay to four refreshes per second instead of rebuilding more than 50 lines every rendered frame. Configure the interval with `dx12mc.overlay.refreshMillis`.
- Added prior-frame bgfx CPU, GPU, submit-wait, render-wait, native draw-count, static/transient submission, and JVM garbage-collection timing to the diagnostic overlay.
- Identified that the tested launcher profile forces a 32 MiB G1 region on a 2 GiB heap. That external JVM setting must be removed from the profile to avoid coarse, periodic collection work.

## 0.8.9 - Native draw hot-path optimization

- Reduced the D3D12 vertex stride from 52 to 32 bytes by staging only attributes consumed by the active shaders. Compact UV1 and normal data stays in the CPU batch for future shader families, while UV2 lightmap coordinates remain available to terrain rendering.
- Reused section transforms, bridge matrix/draw-context storage, frame dimensions, and resident-mesh metadata instead of allocating them for every compiled-section draw or frame.
- Cached translated render-state snapshots until the mirrored state or framebuffer dimensions change.
- Cached shader-family and cutout classification until Minecraft changes the active shader.
- Disabled the 54-line bgfx diagnostic overlay by default and avoided collecting its snapshot while hidden. It remains available with `-Ddx12mc.overlay=true`.

## 0.8.8 - Forward-progress staging + stable section residency

- Texture creation and dirty updates now retain an exact upload cursor across frame-boundary backpressure. Successfully submitted regions are not resent, and partially populated native textures are preserved until they are complete.
- Split large texture regions into bounded row bands (4 MiB by default, configurable with `dx12mc.texture.maxUploadChunkBytes`) so a texture larger than the global 16 MiB staging budget cannot starve forever.
- Coalesce translucent sort results in the CPU mirror and upload only the latest order when the section is next drawn.
- Staging pressure during a sorted-index update now preserves the resident VBO, dynamic IBO, and last valid native order. It retries later rather than rebuilding the whole section mesh.
- Protect meshes used in the preceding frame from LRU eviction. If the visible working set exceeds the configured cache, overflow draws use the native transient path instead of causing a cyclic evict/recreate scan.
- Added separate overlay counters for pressure-deferred index updates and genuine GPU index rebuilds.

## 0.8.7 - Native staging backpressure + sparse texture dirties

- Replaced large `bgfx_alloc` payload staging in textures, persistent meshes, sorted indices, and shader uploads with DX12MC-owned native buffers handed to bgfx through `bgfx_make_ref_release`.
- Added a 16 MiB default cap for outstanding native staging (`dx12mc.staging.maxOutstandingBytes`).
- Existing D3D12 texture revisions survive short staging-pressure spikes and retry pending dirty regions later instead of crashing or using OpenGL.
- Reworked texture dirty tracking from one expanding bounding rectangle to up to 64 sparse/coalesced dirty regions per mip level.
- Raised default resident static mesh count ceiling from 1536 to 4096 while retaining the 256 MiB byte budget, reducing cache thrash on larger render distances.
- Added overlay counters for native staging current/peak and deferred texture GPU updates.

## 0.8.6 - Sorted-index stability + in-place transparency updates

- Fixed the observed 0.8.5 crash after ~1 minute: `VertexBuffer.uploadIndexBuffer` can deliver a translucent-quad sort result that no longer validates against the currently captured section generation. QUADS now preserve the last valid native D3D12 order instead of terminating the client; unknown/non-quad invalid updates remain fatal.
- Decode sort results by their exact `ByteBufferBuilder.Result` byte width first (16-bit vs 32-bit), then validate every index against the current vertex generation.
- Persistent section meshes now use a static vertex buffer plus a dynamic bgfx/D3D12 index buffer. Camera-driven transparency re-sorts update only the index buffer in-place instead of destroying/recreating the entire VBO+IBO pair.
- Removed index-update-driven LRU eviction/recreation churn, which was a major contributor to low FPS and bgfx handle pressure in 0.8.5.
- If a native dynamic-index update cannot be staged, only that resident GPU mesh is retired and rebuilt/streamed by the existing bounded native path on its next draw; OpenGL fallback remains disabled.
- Added overlay counters for stale sort preservation, in-place GPU index updates, and deferred GPU index rebuilds.

## 0.8.5 - Safe vertex-storage ownership

- Fixed the immediate world-entry crash `Failed to map GL buffer`.
- The 0.8.4 GL storage optimization was too broad: its upload scope also covered Minecraft's shared `AutoStorageIndexBuffer`, which allocates an `GL_ELEMENT_ARRAY_BUFFER` and maps it immediately to generate sequential/quad indices.
- Duplicate OpenGL storage suppression is now restricted to `GL_ARRAY_BUFFER` vertex data only. Element/index buffers always retain vanilla allocation/mapping semantics.
- Removed the suppression scope around explicit `uploadIndexBuffer` updates.
- Kept the 0.8.4 compact CPU mirrors, byte-bounded D3D12 residency, LRU eviction and native streaming spill behavior.

## 0.8.4 - Bounded memory ownership

- Fixed a contradictory stability regression test: 0.8.4 intentionally suppresses only scoped VertexBuffer GL data-store allocation after D3D12 takeover while preserving the rest of Minecraft's legacy GL resource lifecycle.
- Compact retained section CPU mirrors: UV1/UV2 remain 16-bit and normals remain signed bytes until D3D12 staging; absent attributes retain zero-length arrays instead of per-vertex zero storage.
- Bound persistent section GPU residency by both resource count and byte budget (defaults: 4096 meshes / 256 MiB).
- Replace fatal bgfx static-buffer exhaustion with a strict native transient D3D12 spill for the affected section. Static allocation backs off until the next bgfx frame boundary so deferred destroys can retire instead of being hammered repeatedly; an exhausted per-frame transient ring defers only the affected section draw to the next frame.
- Suppress redundant OpenGL VertexBuffer data-store allocation after D3D12 takeover while preserving Minecraft GL object/VAO lifecycle bookkeeping. No GL draw or present path is enabled.
- Add CPU/GPU section-memory, streaming-spill and avoided-GL-upload diagnostics.
- Preserve 0.8.3 sky state and 0.8.2 terrain/cutout rendering parity.

## 0.8.3 - World stability + camera-relative sky parity

- Bounded persistent bgfx section-mesh residency with an LRU working set (default 2048) so large render distances no longer exhaust bgfx vertex/index-buffer handles.
- Added one-shot emergency mesh eviction/retry for native handle pressure; this never falls back to OpenGL.
- Added a semantic `LevelRenderer.renderSky` scope. Sky background geometry now uses D3D12 state with depth testing/writes disabled, preventing camera-relative sky discs from masking terrain as a gray plane.
- Preserved strict D3D12 draw/present policy and all 0.8.2 terrain/cutout fixes.

## 0.8.2 - Terrain raster parity

- Fixed D3D12 world-section front-face parity. The previous bridge forced bgfx `FRONT_CCW`, which inverted Minecraft exterior faces after the D3D12 top-left viewport transform and made solid terrain appear hollow/missing.
- Added native cutout fragment pipelines for `rendertype_cutout`, `cutout_mipped`, terrain. Transparent atlas texels now discard before depth/color writes instead of becoming opaque leaf/grass quads.
- Kept strict D3D12 policy: no OpenGL draw or presentation fallback.
- Kept 0.8.1 semantic world-entry/readback suppression and native line omission behavior.

## 0.8.1 - World visibility + line survival

- Fix in-world immediate overlay blending: keep the proven GUI depth/cull profile, but preserve Minecraft's live blend factors when blending is explicitly enabled. This prevents the vignette/multiplicative overlay from becoming an opaque gray/black fullscreen quad over the D3D12 world.
- Prevent `rendertype_lines` (block outline/debug thick lines) from crashing world entry. The path is omitted at the semantic MeshData choke point until the native screen-space thick-line shader is implemented; it never falls back to OpenGL.
- Add diagnostics/regressions for special immediate blend modes and omitted native line overlays.

## 0.8.0

- Promoted the Minecraft-facing bridge toward semantic choke points instead of treating raw OpenGL calls as the primary translation layer.
- Added a high-level `GameRenderer.tryTakeScreenshotIfNeeded()` hook for vanilla's automatic singleplayer world-icon capture. The old path downloads the legacy OpenGL main RenderTarget with `glGetTexImage`; after D3D12 takeover that storage is not the visible frame. DX12MC now skips only this non-gameplay capture until native D3D12 readback exists.
- User/mod initiated raw OpenGL readbacks remain fatal. Strict no-fallback policy is unchanged.
- Retained the 0.7.2 `LevelRenderer.renderSectionLayer` semantic section scope and native compiled terrain path.
- Added semantic intent text to strict raw-GL escape errors so the next uncovered path is easier to locate.
- Added diagnostics and regression coverage for the automatic world-icon readback path.
- Goal of this milestone: remove the last observed world-entry blocker without weakening D3D12 ownership.

## 0.7.2

- Added native D3D12 coverage for Minecraft 1.21.1 compiled chunk/section rendering.
- `LevelRenderer.renderSectionLayer` uses `VertexBuffer.draw()` directly after manually binding a shader; this was the raw GL escape seen on first world frame.
- Scoped section-layer context mirrors the vanilla `ChunkOffset` uniform and folds it into the D3D12 model-view transform.
- Unknown raw draw paths remain fatal instead of silently falling back to OpenGL.

## 0.7.1

- Corrected the Minecraft 1.21.1 Mojang-mapped blur target to `GameRenderer.processBlurEffect(float)`.

## 0.7.0

- Fixes the remaining white secondary-menu regression by suppressing `GameRenderer.renderBlur()` only after native D3D12 takeover; the legacy GL post-chain can no longer paint stale framebuffer data over the D3D12 frame.
- No OpenGL fallback was added. The blur effect is omitted until a native render-target/post-process implementation replaces it.
- Adds `ShaderFamily` routing for GUI, text, terrain, entity, particle and basic shaders.
- Extends `Batch`, `Encoder`, bgfx layout and packing with UV1, UV2 and normal data.
- Adds a Sampler2 D3D12 lightmap shader/pipeline using Minecraft's UV2 lightmap coordinate domain.
- Preserves the 0.6.3 immediate GUI/text compatibility path to avoid regressing the now-correct title screen.
- Adds lightmap and legacy-blur diagnostics plus regression tests.

## 0.6.3

- Fixes the 0.6.x regression where title-screen widgets/text disappeared and several screens became white.
- Persistent VertexBuffer/world draws keep the 0.6.x depth/blend/cull/color-mask/scissor translator.
- Immediate MeshData (GUI, text, overlays and fullscreen helper quads) uses the exact known-good 0.5.12 bgfx D3D12 state while still honoring Minecraft scissor.
- No OpenGL draw or presentation fallback was added. Raw GL draw/present escapes remain fatal.
- Adds regression tests for the compatibility profile and scissor preservation.

## 0.6.2

- Fix a contradictory RenderState regression test left over from 0.6.0.
- Keep the 0.6.1 Minecraft post-bootstrap initial state (depth test enabled, GL_LEQUAL, depth writes enabled).
- Test the intended invariant explicitly by disabling depth test before asserting that bgfx emits neither depth testing nor depth writes, even when depthMask remains true.
- No renderer behavior rollback and no OpenGL fallback changes.

## 0.6.1

- Fix render-state mirror startup desynchronization introduced in 0.6.0.
- Seed depth state from Minecraft 1.21.1 `RenderSystem.setupDefaultState()` (depth test enabled + GL_LEQUAL) instead of raw OpenGL defaults.
- Prevent coplanar GUI/title-screen quads from being rejected by an incorrect GL_LESS native depth state.
- Keep the rest of the 0.6.0 D3D12 state bridge and strict no-OpenGL-fallback policy unchanged.

## 0.6.0

- Promote the 0.5.12 known-good renderer into a render-state-aware native bridge instead of continuing to submit every draw with one hard-coded bgfx state.
- Mirror Minecraft 1.21.1 depth-test enable/disable, depth function, depth write mask, blend enable/disable, separate blend factors, blend equation, culling, color write mask, scissor enable/box into backend-neutral state.
- Translate the mirrored state to bgfx/D3D12 per draw, including OpenGL bottom-left scissor coordinates to the D3D12/bgfx top-left framebuffer convention. Unsupported depth/blend enums fail explicitly instead of silently producing the wrong D3D12 state.
- Keep draw cancellation at the high-level `VertexBuffer` / `BufferUploader` choke points. Rename the old misleading "OpenGL draws suppressed" counter to "vanilla draw calls replaced by D3D12"; add a separate raw-OpenGL-escape counter.
- Add strict guards for `_glDrawPixels`, `_getTexImage`, and both `_readPixels` overloads after takeover. A stale OpenGL framebuffer/texture can no longer be read as if it were the D3D12 result.
- Correct two state-translation edge cases found during the full audit: depth writes are now disabled whenever OpenGL depth testing is disabled (matching GL semantics), and a single blend equation is encoded into both bgfx RGB and alpha equation fields rather than leaving alpha on ADD.
- Clip scissor rectangles against both framebuffer dimensions before converting bottom-left GL coordinates to bgfx/D3D12 top-left coordinates; empty scissors now skip transient/persistent buffer binding entirely.
- Track polygon offset, polygon mode and color-logic state. These are not silently approximated: an affected native draw reports a precise unsupported-path error until their D3D12 implementation exists.
- Guard framebuffer-to-texture copies and low-level OpenGL framebuffer blits after takeover, in addition to GL pixel/texture readback, so stale legacy render targets cannot leak back into the native renderer.
- Include the active Minecraft shader name in native translation diagnostics to make the next missing shader family immediately identifiable.
- Preserve the 0.5.12 conservative OpenGL resource/bootstrap lifecycle. This release does not reintroduce resource suppression or an OpenGL draw/present fallback.
- Add pure-Java tests for render-state bit encoding, scissor conversion, unsupported-state diagnostics, and source regression checks for every new state observer.

## 0.5.12

- Restore a conservative, non-cancelling `GlStateManager._texImage2D` dimension observer so dynamic textures that bypass `TextureUtil.prepareImage` still give DX12MC their real target size. Vanilla OpenGL resource allocation continues unchanged.
- Fix dynamic/atlas sub-uploads that still arrive before any authoritative texture allocation. Destination offsets now grow an inferred D3D12 CPU mirror instead of being incorrectly validated against the first source NativeImage size (e.g. a 5x8 glyph uploaded at y=9).
- Track authoritative TextureUtil dimensions separately from inferred upload extents. Inferred mirrors may grow while preserving captured pixels; authoritative textures remain strict and reject out-of-bounds uploads.
- Stop using source-image dimensions to infer target texture dimensions. Source skip/size and destination offset/extent are validated independently, matching NativeImage upload semantics.
- Preserve early captured pixels if a later TextureUtil allocation reveals a larger authoritative texture. A contradictory smaller authoritative allocation now fails explicitly instead of silently corrupting the mirror.
- Reject non-zero mip uploads before base dimensions are known rather than guessing an ambiguous base texture size.
- Remove the dormant 0.5.10 OpenGL resource-suppression API surface from Runtime/Controller so the conservative 0.5.11 policy cannot be accidentally re-enabled by a future Mixin. OpenGL resource/bootstrap lifecycle remains intact; OpenGL draw/present fallback remains forbidden.
- Add regression tests for inferred texture growth, pixel preservation, mip extent calculations, and conservative resource ownership.

## 0.5.11

- Keeps the 0.5.10 D3D12 memory/staging fixes (`bgfx_alloc`, lazy/coalesced texture uploads, CPU limits).
- Reverts speculative cancellation of vanilla OpenGL texture and VBO/EBO resource allocation. These legacy resource operations are preserved for Minecraft bootstrap/resource compatibility and are not considered a rendering fallback.
- Strict D3D12 policy remains unchanged for actual drawing and presentation: raw OpenGL draws and GLFW SwapBuffers remain forbidden after takeover.
- Fixes the 0.5.10 stability test that failed because its forbidden-token assertion matched a comment rather than executable code.

## 0.5.10

- Fix the 0.5.9 resource-reload/native-memory failure at its ownership boundary: ordinary NativeImage/TextureUtil resource texture storage is CPU-mirrored for DX12MC and the duplicate OpenGL allocation/upload is cancelled after D3D12 takeover. Legacy RenderTarget FBO storage remains explicitly scoped for 1.21.1 lifecycle compatibility.
- Make D3D12 texture uploads lazy and coalesced: resource reloads no longer enqueue a bgfx upload for every NativeImage sub-upload before a frame can consume staging memory.
- Replace `MemoryUtil.memAlloc -> bgfx_copy` double staging in textures, persistent meshes, and shader program loading with direct `bgfx_alloc` writes.
- Preserve and grow mip-level CPU mirrors instead of rejecting/losing late mip uploads; bgfx storage is recreated lazily only when mip capacity must expand.
- Add per-texture (256 MiB default) and registry-wide CPU mirror (512 MiB default) safety limits with diagnostic errors instead of uncontrolled heap/native OOM.
- Avoid directly referencing Minecraft 1.21.1's package-private `NativeImage.InternalGlFormat`; direct overload allocations are covered at the lower `GlStateManager._texImage2D` choke point instead.
- Add strict guards for OpenGL `glDrawPixels`, framebuffer-to-texture copies and GL framebuffer readback after takeover; these now report unsupported native paths rather than touching stale GL render data.
- Expand stability regression tests around D3D12 resource ownership, staging allocation, mip growth and GL escape detection.

## 0.5.9

- Suppress Minecraft 1.21.1 `RenderTarget.blitToScreen` after D3D12 takeover; this is the legacy OpenGL framebuffer-to-window presentation quad, not game geometry.
- Cover both `BufferUploader.drawWithShader(MeshData)` and `BufferUploader.draw(MeshData)` immediate paths with the native bridge.
- Keep strict behavior: unexpected raw OpenGL draws remain fatal; no GL readback and no OpenGL presentation fallback were added.
- Add regression coverage for the legacy framebuffer blit and both BufferUploader entry points.

## 0.5.8

- Fixed Mixin annotation processing for `RenderSystem.flipFrame(long)` by using the exact descriptor `flipFrame(J)V` and disabling remapping on the redirect selector; Blaze3D's method name is stable here and does not need an obfuscation lookup.
- Removed the invalid optional `GlStateManager._drawArrays(III)V` injector. Minecraft 1.21.1 does not expose that wrapper, so it only produced a Mixin AP warning.
- Kept the strict-D3D12 policy unchanged: no OpenGL draw/present fallback was reintroduced.
- Updated stability source-contract tests so they now reject the nonexistent `_drawArrays` hook and require the non-remapped `flipFrame(J)V` redirect.

## 0.5.7

- Made native/debug rendering strict D3D12: no OpenGL draw fallback and no GLFW/OpenGL present fallback after takeover.
- Removed `dx12mc.bridge.cancel` and `dx12mc.present.exclusive`; native mode can no longer be switched into hybrid rendering accidentally.
- D3D12 initialization, mesh translation, index translation, texture mirroring, bgfx submission, and present failures now throw `FatalRenderException` with an explicit `DX12MC STRICT D3D12 ERROR` message so Minecraft produces a crash report instead of silently continuing through OpenGL.
- Missing texture mirrors now raise an explicit unsupported-path error when a D3D12 draw requires them. Unsupported CPU texture uploads may remain ignored for bootstrap compatibility, but are never used as an OpenGL render fallback.
- Kept the GLFW/OpenGL bootstrap context where Minecraft's existing lifecycle still needs it; explicit `mode=probe` remains the only mode allowed to present OpenGL because takeover is intentionally disabled.
- Removed silent native texture-update recovery; a failed bgfx texture update now propagates to the strict renderer instead of being discarded.
- Added a post-takeover raw OpenGL draw guard on `GlStateManager._drawElements`; 0.5.7 also attempted an optional `_drawArrays` hook that was invalid for Minecraft 1.21.1 and is removed in 0.5.8.
- Added source-contract tests that prevent reintroducing conditional GL swap fallback, raw GL draw escape, or the old compatibility switches.
- Retained 0.5.6 stability work: D3D12 present at the vanilla swap point, immediate MeshData bridging, mutable texture updates, and delayed native texture retirement.

## 0.5.6

- Moved D3D12 presentation to the exact `RenderSystem.flipFrame` GLFW swap point and suppresses only `glfwSwapBuffers` while the native backend is healthy. Vanilla event polling, render-call replay and Tesselator cleanup remain in the normal Minecraft flow.
- Added `-Ddx12mc.present.exclusive=true` (default) so OpenGL and D3D12 no longer compete to present to the same HWND. `false` restores the old hybrid-present behavior for diagnostics only.
- Added a `BufferUploader.drawWithShader(MeshData)` bridge so immediate Tesselator / RenderType / GUI geometry can be submitted to D3D12 before exclusive presentation.
- Reworked RGBA8 texture mirrors to create mutable bgfx textures and apply accepted NativeImage sub-image changes with `bgfx_update_texture_2d` instead of destroying and recreating a complete GPU texture after every upload.
- Kept replaced/deleted native texture handles alive for a short submitted-frame retirement window before destruction.
- Added stability source-contract JUnit tests covering exclusive swap ownership, immediate MeshData bridging and mutable texture updates.
- Kept the 0.5.5 Gradle output fix: shader staging has its own task output and no longer writes into `build/resources/main`, removing the implicit `compileTestJava -> installD3D12Shaders` dependency problem.

## 0.5.5

- Restored the proven `vec2_splat` / `vec3_splat` / `vec4_splat` shader helpers used by the working 0.4.x pipeline; bgfx shaderc injects `vec4_splat` into fragment wrappers even when user shader code does not call it.
- Split shader support into `common.sh` and `texture.sh` so basic shaders do not carry texture resource declarations while every stage still receives the common shaderc compatibility helpers.
- Kept the D3D texture sampler model as separate `SamplerState` + `Texture2D` resources with non-reserved `m_sampler` / `m_texture` fields.
- Replaced deprecated `project.exec` shader invocation with an aggregate `ProcessBuilder` compiler task that attempts all four shaders and reports every shader failure together.
- Added `verifyD3D12Shaders`, which compiles every shader and verifies the generated bgfx VSH/FSH container headers.
- Added JUnit 5 tests for primitive-index conversion, CPU buffer packing, ABGR-to-RGBA pixel packing, mode parsing, and shader-source contracts.
- Added `verifyDX12MC` as the full verification entry point and decoupled `gradlew test` from shader compilation so Java tests can still run when a shader is broken.

## 0.5.4

- Fixed D3D12 shader compilation: the custom HLSL sampler struct used the reserved identifier `sampler`.
- Aligned the minimal sampler helper with bgfx's HLSL resource-object layout (`m_sampler` / `m_texture`).
- Removed the texture helper include from shaders that do not sample textures, reducing unnecessary HLSL resource declarations.
- Strengthened shader source validation to reject the reserved-field regression explicitly.

## 0.5.3

- Fixed the shader-source validator false positive introduced in 0.5.2.
- Made the bundled shader helper intentionally D3D/HLSL-only; the generated shader set has no GLSL fallback.
- Replaced the broad string check with a line-anchored invalid-sampler macro check.
- Kept the texture bridge and renderer policy unchanged from 0.5.2.

## 0.5.2

- Fixed the D3D12 textured fragment shader helper: HLSL now receives a `SamplerState` + `Texture2D` pair instead of the invalid GLSL `sampler2D` declaration.
- Kept the shaderc tool pinned to LWJGL/bgfx 3.3.3.
- Added a shader-source preflight task so the invalid sampler helper cannot silently regress before `shaderc` runs.
- No renderer bridge policy changes from 0.5.1.

## 0.5.1

- Fixed Mixin annotation processing for stable-named `GlStateManager` texture hooks by disabling remapping only on those injection targets.
- Fixed the same mapping issue for the stable `TextureUtil.prepareImage` overloads.
- No renderer/texture-bridge behavior changes from 0.5.0.

## 0.5.0

- Added UV0 decoding to the Minecraft mesh bridge.
- Added a shared position/color/UV vertex layout for native pipelines.
- Added a bgfx RGBA8 texture owner and sampled texture pipeline.
- Mirrors Minecraft `NativeImage` level-0 RGBA uploads directly from CPU memory; no OpenGL texture readback is used.
- Tracks texture allocation, binding, deletion, filtering and clamp intent through thin Mixins.
- Applies Minecraft's texture matrix in the D3D12 textured vertex shader.
- Textured draws are cancelled on the OpenGL side only when the required D3D12 texture mirror exists and submission succeeds.
- Missing, non-RGBA and mip-only texture cases deliberately fall back to OpenGL.
- Expanded the native debug overlay with textured-submit, texture-upload and fallback counters.
- Kept `mode=debug` as the standalone proven bgfx/D3D12 diagnostic screen.

## 0.4.0

- First native Minecraft `VertexBuffer` geometry bridge through bgfx Direct3D 12.
- Position/color/index capture, static and transient buffers, topology conversion and sorted index updates.
- OpenGL draw cancellation only after successful D3D12 submission.

## 0.3.0

- Added the first practical/playable mode: Minecraft keeps its OpenGL-facing renderer while Mesa's Gallium D3D12 driver translates the work to Direct3D 12.
- Added Fabric `preLaunch` bootstrap so the graphics library is selected before GLFW creates Minecraft's WGL context.
- Added a pinned Windows x64 Mesa runtime resolver with upstream download, SHA-256 verification, cache and extraction.
- Pinned `mesa-d3d12-x64-26.1.6.7z` with its upstream-published SHA-256.
- Added `-Ddx12mc.mesa.path=...` for an externally supplied `opengl32.dll`.
- Added `-Ddx12mc.mesa.download=false` for offline/manual operation.
- Configures both LWJGL (`org.lwjgl.opengl.libname`) and GLFW WGL (`GLFWNativeWGL.setPath`) to use the same Mesa OpenGL implementation.
- Added runtime OpenGL vendor/renderer/version logging so the D3D12 path can be verified from `latest.log`.
- Kept the bgfx Direct3D 12 takeover screen as `-Ddx12mc.mode=debug`; `takeover` and `diagnostic` remain aliases.
- Reorganized code into `bootstrap`, `driver.gl`, `driver.mesa`, `debug`, `graphics.bgfx`, `platform.win32`, `client`, `config`, and thin `mixin` packages.
- Applied the requested low-memory Gradle configuration (`768m` heap, no parallel build, max 2 workers, cache enabled).

## 0.2.0

- Reorganized packages and class ownership; removed redundant DX/D3D/bgfx wording from class names where package context already provides it.
- Split Win32, bgfx probing/lifecycle and debug-text responsibilities.
- Added a visible bgfx Direct3D 12 diagnostic draw path with live frame counter, backbuffer size and color bars.

## 0.1.1

- Fixed LWJGL bgfx `bgfx_frame(false)` signature.
- Verified Direct3D 12 takeover, presentation and clean shutdown.


