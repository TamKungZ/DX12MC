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
- `LevelRenderer` for section layer rendering.
- `BufferUploader` for immediate mesh submissions.
- `VertexBuffer` for persistent mesh uploads and draws.
- `NativeImage`, `TextureUtil`, and texture-related calls for image upload data.
- `GlStateManager`, `RenderSystem`, and `RenderTarget` where Minecraft still expects GL-shaped lifecycle or state calls.

The project prefers semantic Minecraft hooks over raw OpenGL interception. Low-level GL hooks are kept narrow: they mirror state DX12MC needs or stop unsupported raw rendering from being hidden.

## Bridge Responsibilities

The bridge is responsible for converting Minecraft render inputs into data the backend can submit:

- `bridge.mesh` encodes vertex and index data into compact CPU-side batches.
- `bridge.texture` tracks image revisions and uploads texture regions progressively.
- `bridge.shader` maps supported vertex formats and render types to shader families.
- `bridge.state` stores the render state needed by bgfx submissions.
- `Bridge` coordinates frame lifecycle, residency, defers, and draw submission.

The bridge keeps CPU mirrors for data that Minecraft may update asynchronously, such as sorted translucent indices or dirty texture regions. This lets DX12MC retry native work across frame boundaries without depending on stale OpenGL buffers.

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

- legacy menu blur is omitted until native render-target or post-processing support exists;
- automatic singleplayer world-icon capture is omitted until native readback exists;
- unsupported thick-line rendering is skipped at the mesh choke point.

These limitations are tracked in source and tests so missing coverage is visible during development.

## Tests

Tests cover small bridge components, encoding rules, shader source contracts, and selected source-level stability checks. They are intended to catch regressions in the current architecture, not to prove full Minecraft renderer compatibility.
