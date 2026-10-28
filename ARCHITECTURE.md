# Architecture

DX12MC is a client-side Fabric mod that redirects selected Minecraft Java rendering work to a Direct3D 12 backend through bgfx. It does this by intercepting specific Minecraft rendering points, translating the data DX12MC supports, and rejecting paths that would silently fall back to OpenGL rendering.

This document is a source-level overview for contributors. It is not a complete renderer specification.

## Runtime Shape

```text
Minecraft client
  GameRenderer / LevelRenderer / BufferUploader / VertexBuffer / texture upload points
        |
        v
Fabric mixins
        |
        v
DX12MC bridge
  mesh, texture, shader, render state, frame statistics
        |
        v
bgfx
        |
        v
Direct3D 12
```

The main boundary is the bridge layer under `net.dx12mc.bridge`. Minecraft-facing mixins collect data from known render paths, then pass normalized batches, textures, state, and frame events into the bridge. The lower bgfx wrapper under `net.dx12mc.graphics.bgfx` owns the native handles and staging memory used to submit work to bgfx.

## Integration Points

DX12MC hooks Minecraft at a small number of render and resource choke points:

- `GameRenderer` for frame-level behavior and selected legacy effects.
- `LevelRenderer` for section layer rendering, and the start and end of `renderLevel` for shader pack stages.
- `BufferUploader` for immediate mesh submissions.
- `VertexBuffer` for persistent mesh uploads and draws.
- `NativeImage`, `TextureUtil`, and texture-related calls for image upload data.
- `GlStateManager`, `RenderSystem`, and `RenderTarget` where Minecraft still expects GL-shaped lifecycle or state calls, including clears, texture parameters and the bound render target. `RenderTarget` binds, clears, destroys and screen blits are mirrored to native render targets, and `PostChain.process` runs natively.
- `Window.updateVsync`, `Options`, and `OptionInstance` so video settings either drive the D3D12 backend or are shown as unavailable.
- `ReloadableResourceManager` to add the shader pack loader to client resource reloads, and `TextureAtlas.upload` to record the block atlas sprite layout for material atlases.
- `GlUtil` and `TimerQuery` so renderer information and GPU timing describe the D3D12 adapter instead of the bootstrap OpenGL context. The adapter's driver version and feature level are read once at startup and shared by F3, crash reports and the log.

The project prefers semantic Minecraft hooks over raw OpenGL interception. Low-level GL hooks are kept narrow: they mirror state DX12MC needs or stop unsupported raw rendering from being hidden.

## Bridge Responsibilities

The bridge is responsible for converting Minecraft render inputs into data the backend can submit:

- `bridge.mesh` encodes vertex and index data into compact CPU-side batches; `MeshStore` owns CPU captures and bounded GPU residency.
- `bridge.texture` tracks image revisions and uploads texture regions progressively. `MipLevel` owns pixel/dirty-region state and merges dirty regions when one larger update is cheaper than several, while `TextureEntry` owns CPU-to-GPU synchronization.
- `bridge.shader` describes each vanilla core shader program (alpha test, fog mode and space, lightmap lookup, diffuse lighting, overlay) so the native programs reproduce it; a test checks the table against the shader sources in the Minecraft jar.
- `bridge.state` tracks Minecraft render state separately from the bgfx bit encoder and scissor conversion.
- `DrawContextFactory` resolves per-draw matrices, shader semantics, textures, and state without allocating on the steady-state path.
- `graphics.pipeline.PipelineSet` owns pipeline resources and routes draws to the colour, texture, lightmapped, entity, line or end-portal pipeline, each with an alpha-test variant. The end-portal pipeline evaluates the vanilla end portal and end gateway layers from the game time and binds both of their textures. Opaque programs never discard, so they keep early depth testing.
- Minecraft's mid-frame clears start a new sequential bgfx view on the bound target with the requested clear flags.
- `bridge.target.Targets` keeps one `graphics.bgfx.Target` (a bgfx frame buffer with an RGBA8 colour texture and an optional write-only depth buffer) per Minecraft `RenderTarget`, keyed by its colour texture id. A target is created the first time it is drawn into or sampled, and released after the next bgfx frame once Minecraft calls `destroyBuffers`. Binding a target starts a new view with `bgfx_set_view_frame_buffer` and the target's size; draws, clears and post passes after that go to it. Clears of a target Minecraft has not drawn into yet are kept until the first draw or read, so an unused outline target costs nothing.
- The main target is the backbuffer unless the frame needs to read it: a menu blur, an active spectator effect, or a shader pack that uses the scene, decided when the frame opens (`Controller.ensureFrame`). Those frames render into a main `Target` and end with one blit view onto the backbuffer. A chain that reads the main target on any other frame is skipped.
- `render.PostProcessor` runs `PostChain.process` for the programs in `graphics.pipeline.PostPipeline.Effect` (`box_blur`, `entity_outline`, `entity_outline_box_blur`, `blit`, `invert`, `color_convolve`, `bits`, `spider`). Each pass is one view on the output target with the vanilla clear, a fullscreen triangle, uniform values read from the `EffectInstance`, and the vanilla blend and filter mode. The shaders sample with the OpenGL texture-coordinate convention flipped once in `post.sh`. A chain with a program that has no port is skipped and logged once.
- `pack` loads shader packs (see `docs/API.md`). `client.ShaderPackLoader` reads and validates them during the resource reload, off the render thread; `pack.PackSet` creates their native programs, uniforms, textures and targets at the next frame boundary and releases the previous set. `DrawContextFactory` resolves an override when the shader name changes, and `ShaderPipeline` submits the override program instead of the built-in one. With a G-buffer the pipelines switch to `_mrt` fragment variants that write zero to the extra attachments, from the first section layer until `renderLevel` returns. Errors in a pack disable it at the next frame boundary; they are not fatal.
- Screenshots replace Minecraft's OpenGL readback with a bgfx screenshot of the last presented frame, then let Minecraft's own code save it.
- `Bridge` is the native-rendering facade and coordinates draw submission across these components.

The Minecraft-facing `Controller` is the integration point inside the mod. Other mods use only the `net.dx12mc.api` package in the `api` subproject, which depends on nothing in the mod. `RendererLifecycle` owns backend initialization, frame and present boundaries, fatal state, and shutdown; `SectionRenderContext` owns the reusable transforms for compiled section layers.

The bridge keeps CPU mirrors for data that Minecraft may update asynchronously, such as sorted translucent indices or dirty texture regions. This lets DX12MC retry native work across frame boundaries without depending on stale OpenGL buffers.

Compiled opaque sections are combined into region pages. Pages made only of quads share one static index buffer, and each frame draws either a whole page or only the index ranges of its visible sections, whichever costs less. Region rebuilds, per-section GPU mesh creations and GPU index updates are limited per frame. A region waiting for a rebuild keeps drawing its previous page, and sections added since then are streamed through transient buffers within a per-frame byte budget so draws such as particles and GUI keep their share of the transient pool.

The steady-state section draw path avoids creating Java objects per draw. It reuses matrix, draw-context, and resident-metadata storage, caches shader classification while the active shader is unchanged, and reuses translated render-state snapshots until state or framebuffer dimensions change. Native vertex staging contains only attributes consumed by current shaders; richer compact attributes remain in the CPU batch until a shader family needs them.

## Backend Responsibilities

The bgfx layer wraps the native resources used by the bridge:

- buffers and meshes;
- textures;
- shader programs;
- transient and reference-counted staging memory;
- backend probing and debug text.

Large uploads use bounded native staging. If bgfx cannot accept more data in the current frame, DX12MC records the pending work and resumes later instead of allocating without limit.

## Resource Ownership

After DX12MC takes over a supported path, visible rendering data is owned by DX12MC's CPU mirrors and bgfx resources. Minecraft objects may still exist because vanilla code expects their lifecycle, but they are not treated as the source of visible native rendering once the data has entered the bridge.

Persistent section meshes are kept resident while capacity allows. Under pressure, DX12MC can defer a draw for a later frame or use bounded transient streaming for visible work. Recently used resident meshes are protected from immediate eviction to avoid rebuild loops.

Texture uploads are tracked by revision. Initial uploads and dirty regions are split into row bands, so a large texture can make forward progress over multiple frames. If a texture has a previous valid native revision, DX12MC may keep drawing that revision while newer regions wait for staging capacity. If no native revision exists yet, the affected draw is deferred.

## Unsupported Paths

DX12MC does not try to make every Minecraft render path work by mixing native D3D12 output with OpenGL output. Unsupported visible paths are expected to fail loudly or be skipped at a known semantic hook until a native implementation exists.

Examples of intentionally limited behavior:

- the Fabulous graphics mode is not offered: it needs five more full-size targets, a depth copy between targets and the `transparency` program;
- post chains with a program that has no native port are skipped;
- screenshots of offscreen render targets (panorama capture) are not supported, because bgfx completes a texture read frames after it is requested;
- `rendertype_leash` interpolates its vertex colour, where the vanilla shader uses flat shading from the last vertex of each triangle.

These limitations are tracked in source and tests so missing coverage is visible during development.

## Tests

`renderBench` and `cpuBench` are Gradle tasks that measure bgfx submission and CPU-side mesh paths on the local GPU. They are diagnostics, not part of `check`.

Tests cover small bridge components, encoding rules, shader source contracts, and selected source-level stability checks. Source checks follow responsibilities across extracted components instead of assuming every invariant lives in one large facade. They are intended to catch regressions in the current architecture, not to prove full Minecraft renderer compatibility.
