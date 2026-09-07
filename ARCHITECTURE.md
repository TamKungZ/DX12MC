# DX12MC renderer architecture

DX12MC treats Minecraft rendering intent as the primary integration surface and raw OpenGL calls as a last-line assertion surface.

```text
Minecraft 1.21.1 semantic render points
  GameRenderer / LevelRenderer / BufferUploader / VertexBuffer
                     |
                     v
              DX12MC Runtime
                     |
                     v
          backend-neutral bridge state
        mesh / texture / state / scopes
                     |
                     v
                   bgfx
                     |
                     v
             Direct3D 12 / DXGI
```

## Integration rules

1. Prefer a high-level Minecraft choke point when the operation has semantic meaning.
2. Translate visible drawing to D3D12 and cancel the matching vanilla draw.
3. Keep OpenGL bootstrap/resource bookkeeping only while Minecraft still requires it.
4. Never use OpenGL as a rendering or presentation fallback in native mode.
5. `GlStateManager` hooks mirror unavoidable fixed state and guard unknown raw draw/readback escapes. They are not the target renderer.
6. Non-visible legacy features that require stale OpenGL framebuffer contents may be skipped at their semantic source until a native equivalent exists. The current example is automatic singleplayer world-icon capture.

## Current semantic scopes

- `LevelRenderer.renderSectionLayer` -> compiled terrain/section geometry, chunk offset, matrices.
- `GameRenderer.processBlurEffect` -> legacy menu blur is omitted until native render-target/post-processing support exists.
- `GameRenderer.tryTakeScreenshotIfNeeded` -> automatic world-icon capture is omitted until native D3D12 readback exists.
- `BufferUploader` -> immediate GUI/entity/render-type mesh submission.
- `VertexBuffer` -> persistent mesh submission and scoped section-layer direct draws.

Unknown raw OpenGL draw/readback calls remain fatal so missing semantic coverage is visible instead of silently producing hybrid rendering.


## Immediate overlay compatibility
The generic immediate pipeline intentionally combines the proven GUI depth/cull profile with live explicit blend factors. This preserves menu stability while allowing semantic in-world blend modes such as the vignette multiply pass. Unsupported `rendertype_lines` is omitted at the high-level MeshData choke point until a native D3D12 thick-line shader exists; low-level OpenGL remains forbidden.


## Geometry memory ownership (0.8.5)

After native takeover, a Minecraft `VertexBuffer` keeps its Java/GL identity only as lifecycle compatibility. DX12MC owns the visible geometry contents:

```text
Minecraft MeshData upload
        |
        +--> compact DX12MC CPU mirror (short UV1/UV2, byte normal)
        |
        +--> bounded persistent bgfx/D3D12 mesh when resident
        |          or
        +--> native transient D3D12 stream under static-resource pressure

legacy OpenGL vertex VBO data store: suppressed after takeover
legacy OpenGL EBO/index storage: preserved (vanilla maps shared AutoStorageIndexBuffer)
OpenGL draw/present fallback:     forbidden
```

Persistent residency is bounded by both count and estimated GPU bytes. A static-allocation failure puts new section allocation into a one-frame backoff so bgfx can retire deferred destroys at `endFrame`; it does not trigger a destroy/recreate storm. If both static allocation and the per-frame transient ring are temporarily unavailable, section rendering is deferred for one frame and retried natively; no OpenGL path is enabled.


## 0.8.6 sorted section indices

Minecraft may rebuild a section while a queued translucent-quad sort result is still waiting to upload. DX12MC treats the captured CPU batch as authoritative. A valid sort updates only the resident dynamic D3D12 index buffer; a stale QUADS sort keeps the previous valid native order. Non-quad invalid index updates remain strict errors. This prevents asynchronous sort lifetime from becoming a GL fallback or a full section-mesh rebuild loop.


## 0.8.7 native staging ownership

Large upload payloads are no longer allocated by `bgfx_alloc`. DX12MC allocates bounded native blocks, transfers them with `bgfx_make_ref_release`, and frees them only from bgfx's release callback. This keeps upload memory explicitly bounded and turns short allocation pressure into a recoverable native backpressure signal.

Texture dirties are tracked as sparse regions rather than a single union rectangle. Existing GPU textures may render their last valid D3D12 revision for a frame while a pending native upload waits for staging capacity. If a required texture has no native revision yet, the affected draw itself is deferred for one frame and retried after the bgfx frame boundary. Both are native-only temporal defers, never GL fallbacks.

## 0.8.8 forward progress and stable residency

Texture staging is progressive: each initial mip or dirty region is divided into bounded row bands, and its cursor advances only after bgfx accepts that band. Backpressure preserves the partial native texture and resumes the unsubmitted suffix after a frame boundary. Dirty snapshots are detached from newly arriving CPU changes, so successful prefixes are never uploaded repeatedly and later revisions remain pending.

Sorted index updates are similarly coalesced in the CPU mirror and staged only when their section is visible. Pressure keeps the last valid resident index order instead of retiring the mesh. Resident meshes touched in the preceding frame are not eligible for demand eviction; an oversized visible working set uses bounded native transient streaming rather than cyclic LRU reconstruction.
