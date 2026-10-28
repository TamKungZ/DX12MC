# DX12MC API and shader packs

API version 0.1, shader contract 1. Both are experimental: until API 1.0 a minor release may change or remove anything here. Nothing outside this document and the `net.dx12mc.api` package is supported for other mods; internals change between releases.

## Java API

The API is the `dx12mc-api` jar (package `net.dx12mc.api`). It is also bundled in the DX12MC mod jar, so a mod compiles against `dx12mc-api` and does not ship it.

Register a Fabric entrypoint under the key `dx12mc` that implements `Dx12mcEntrypoint`. DX12MC calls it once during client start-up, so a mod that only touches the API from there never loads API classes when DX12MC is absent. To check for DX12MC without loading any class, read the string custom value `dx12mc:api` of the `dx12mc` mod container in Fabric Loader.

`Dx12mc` offers:

- `modVersion()`, `apiVersion()`, `active()`, `adapter()` (name, PCI ids, driver, feature level) and `supports(Capability)`.
- `registerUniformProvider(name, provider)`: supplies the values of pack uniforms declared with `"provider": name`. It is called once per frame and only while an active pack uses that uniform.
- `addReloadListener(listener)` and `packs()`: the result of every shader pack load, including the first one after the device starts.

Use `ApiVersion.satisfies(required)` to check compatibility; before 1.0 only the same minor version satisfies.

Threading: every API method must be called on the render thread (the client main thread) and throws `WrongThreadException` otherwise. Providers and listeners run on the render thread. A provider or listener that throws is logged and removed; it does not stop the game. An entrypoint that throws is logged and skipped.

## Shader packs

A shader pack is a resource pack, or a mod's own assets when the mod uses a resource loader such as Fabric API, with a manifest at `assets/<namespace>/dx12mc/pack.json`. Paths in the manifest are relative to `assets/<namespace>/dx12mc/`. Packs load with the normal resource reload, so F3+T applies changes without a restart.

Shaders are precompiled. The pack contains `.dxsh` files built by the compile tool, not source code.

```json
{
  "format": 1,
  "contract": 1,
  "programs": {
    "lit": {"vertex": "shaders/vs_block.dxsh", "fragment": "shaders/fs_lit.dxsh"},
    "bloom": {"fragment": "shaders/fs_bloom.dxsh"}
  },
  "overrides": {"rendertype_solid": "lit"},
  "uniforms": {
    "u_light": {"type": "vec4", "value": [0.3, 1.0, 0.5, 0.4]},
    "u_settings": {"type": "vec4", "count": 2, "provider": "mymod:settings"}
  },
  "textures": {"s_noise": {"file": "textures/noise.png", "filter": "linear", "wrap": "repeat"}},
  "targets": {"half": {"format": "rgba16f", "scale": 0.5}},
  "scene": {"gbuffer": [{"name": "normal", "format": "rgba8"}], "depth": true},
  "passes": [
    {"stage": "after_world", "program": "bloom", "inputs": {"s_input": "scene:color"},
     "output": "target:half", "blend": "none", "filter": "linear"}
  ],
  "materials": {"normal": true, "specular": true}
}
```

- `programs`: a vertex and fragment shader pair. A program used only by passes may leave out `vertex` and gets the built-in fullscreen vertex shader. `variants` maps a Minecraft version to other files for that version.
- `overrides`: replaces a built-in program for the draws Minecraft makes with that shader name. Only the names in the contract below can be overridden.
- `uniforms`: up to 16. `vec4` with `count` 1 to 4, or `mat4`. Either a constant `value` or a `provider` id.
- `textures`: up to 8 PNG files, sampled by name from slots 5 to 15.
- `targets`: up to 8 colour targets sized as a fraction of the window (`scale` 0.125 to 2). Formats: `rgba8`, `rgba16f`, `rg16f`, `r16f`, `r32f`, `rgb10a2`, `rg8`, `r8`. Targets keep their contents between frames, so a pass that reads a target before another pass writes it gets the previous frame (history).
- `scene`: renders the world into a scene target. `gbuffer` adds up to 3 colour attachments that overridden programs write as `gl_FragData[1..3]`; `depth` lets passes sample the scene depth. A pack that reads or writes `scene:` also gets a scene target.
- `passes`: fullscreen passes, run in order at a stage. Sources are `scene:color`, `scene:depth`, `scene:<gbuffer name>`, `target:<name>` and `texture:<name>`; outputs are `scene:color` or `target:<name>`. A pass cannot read its own output. `blend` is `none`, `alpha` or `add`.
- `materials`: requests the normal and specular atlases, see below.

Stages:

| Stage | When |
| --- | --- |
| `before_translucent` | after terrain, entities and block entities, before translucent terrain |
| `after_world` | after the world, before the hand |
| `before_gui` | after the hand, before the GUI |

Several packs can be active. The highest pack in the resource pack list wins an override that two packs declare, and the conflict is logged. Only one pack may declare a G-buffer.

### Failure and safety rules

- A pack is checked before anything reaches bgfx: manifest keys, names and limits; each shader's container header, bgfx binary layout, DXBC checksum, stage and Shader Model 5.0; vertex attributes against the vertex layout; that the fragment inputs match the vertex outputs; every uniform and sampler against the contract or the manifest; sampler slots; and the memory the pack needs.
- A pack that fails any check, or fails while its resources are created or while it runs, is disabled with a log line that names the pack, its source and the reason. The built-in renderer is used instead and the game continues. DX12MC's own shaders keep the strict policy: a failure there ends the game.
- Packs that come from a server are not loaded unless the game is started with `-Ddx12mc.pack.allowServer=true`. Shader binaries are driver input, and a bad one could hang the GPU.
- Targets, the scene target, pack textures and material atlases together may use at most 256 MiB (`-Ddx12mc.pack.maxVramMiB`). The limit is checked at load and on resize.
- bgfx predefined uniform names (`u_view`, `u_proj`, `u_viewProj`, `u_invViewProj`, `u_model` and the others) are rejected, because bgfx fills them itself and DX12MC never sets them.

## Shader contract 1

Shaders are written in bgfx's shader language (`.sc`) and compiled with the pinned bgfx `shaderc` for Direct3D (DXBC, Shader Model 5.0). Include `dx12mc.sh`. A `.dxsh` file declares the contract version, the profile `d3d12_s_5_0` and the bgfx shader binary version 11; a mismatch disables the pack.

### Overridable programs

| Program | Family |
| --- | --- |
| `rendertype_solid` | block |
| `rendertype_cutout_mipped` | block |
| `rendertype_cutout` | block |
| `rendertype_translucent` | block |
| `rendertype_tripwire` | block |
| `rendertype_entity_solid` | entity |
| `rendertype_entity_cutout` | entity |
| `rendertype_entity_cutout_no_cull` | entity |
| `rendertype_entity_cutout_no_cull_z_offset` | entity |
| `rendertype_entity_smooth_cutout` | entity |
| `rendertype_entity_translucent` | entity |
| `rendertype_entity_translucent_cull` | entity |
| `rendertype_item_entity_translucent_cull` | entity |
| `rendertype_armor_cutout_no_cull` | entity |

An override is used only for draws that use that family's vertex layout; other draws of the same shader name, such as an entity model that DX12MC uploads as a static mesh, keep the built-in program.

### Vertex layouts

Block vertices are 28 bytes: `a_position` (3 × float), `a_color0` (4 × uint8, normalized), `a_texcoord0` (2 × float, atlas UV), `a_texcoord2` (2 × int16 lightmap coordinate; read it with `dx12mcUnsigned16`). Entity vertices are 40 bytes: the same plus `a_texcoord1` (2 × float overlay coordinate) and `a_normal` (4 × uint8, normalized; the normal is `(a_normal.xyz * 255 - 128) / 127`). Block vertices have no normal: derive one from screen-space derivatives, as the sample does. A vertex shader may read only the attributes of its family. Varyings are free; use the default `varying.def.sc` of the tool or put one next to the shaders.

### Uniforms

Draw uniforms are set for overridden programs on every draw. Frame uniforms describe the last world camera of the frame and are available to every pack program. Matrices map camera-relative world positions (world position minus `u_cameraPos`) and produce Direct3D depth from 0 to 1.

| Uniform | Type | Scope | Meaning |
| --- | --- | --- | --- |
| `u_mvp` | mat4 | draw | vertex position to clip space |
| `u_color` | vec4 | draw | shader colour (`RenderSystem` colour, glint alpha applied) |
| `u_texMatrix` | mat4 | draw | texture matrix for `a_texcoord0` |
| `u_fogModel` | mat4 | draw | clip space to fog space; use with `dx12mcFogDistance` |
| `u_fogParams` | vec4 | draw | fog start, end, shape, mode |
| `u_fogColor` | vec4 | draw | fog colour |
| `u_material` | vec4 | draw | alpha threshold, alpha tests the texture, vertex alpha mode, intensity texture |
| `u_texLod` | vec4 | draw | x: highest uploaded mip level of `s_texColor`, also valid for the material atlases |
| `u_lightParams` | vec4 | draw | lightmap mode, overlay on, diffuse lighting on |
| `u_lightDir0` | vec4 | entity draw | first vanilla light direction |
| `u_lightDir1` | vec4 | entity draw | second vanilla light direction |
| `u_cameraViewProj` | mat4 | frame | camera-relative world to clip |
| `u_cameraInvViewProj` | mat4 | frame | clip to camera-relative world |
| `u_cameraPrevViewProj` | mat4 | frame | this frame's camera-relative world to last frame's clip, for motion vectors |
| `u_cameraView` | mat4 | frame | camera rotation |
| `u_cameraProj` | mat4 | frame | projection |
| `u_cameraInvProj` | mat4 | frame | inverse projection |
| `u_cameraPos` | vec4 | frame | camera world position |
| `u_sunDir` | vec4 | frame | xyz: direction to the sun in world space, w: sun angle in radians |
| `u_moonDir` | vec4 | frame | xyz: direction to the moon, w: sun angle |
| `u_time` | vec4 | frame | day time in ticks, seconds (wraps at 3600), partial tick, frame number (wraps at 65536) |
| `u_viewSize` | vec4 | frame | window width, height, 1/width, 1/height |
| `u_frameFog` | vec4 | frame | fog start, end, shape at the time the stage runs |
| `u_frameFogColor` | vec4 | frame | fog colour at the time the stage runs |
| `u_targetSize` | vec4 | pass | output width, height, 1/width, 1/height |

### Samplers

| Sampler | Slot | Scope | Content |
| --- | --- | --- | --- |
| `s_texColor` | 0 | draw | the draw's texture |
| `s_overlay` | 1 | entity draw | hurt and white flash overlay |
| `s_lightmap` | 2 | draw | lightmap |
| `s_normals` | 3 | draw | normal atlas when the draw uses the block atlas, else the normal default |
| `s_specular` | 4 | draw | specular atlas when the draw uses the block atlas, else the specular default |

Pack textures use slots 5 to 15 in overridden programs. Pass programs may use any slot for their inputs. Sample the block atlas and the material atlases with `dx12mcTexture2DMaxLod(sampler, uv, u_texLod.x)`: bgfx allocates the full mip chain but only the levels Minecraft uses are filled.

### Outputs and render state

An overridden program writes `gl_FragColor`, or `gl_FragData[0..n]` when the pack declares a G-buffer. Draws made by programs that the pack does not override write zero to the G-buffer attachments. DX12MC owns blending, depth test and write, culling, colour mask, scissor, polygon offset, primitive type and the target. A shader may change the colour, discard, and write the G-buffer.

## Material textures

With `"materials"` in the manifest, DX12MC builds a normal and a specular atlas with the same size, sprite placement and mip levels as the block atlas. For each block atlas sprite `<namespace>:<path>` it reads `assets/<namespace>/textures/<path>_n.png` and `_s.png` from the resource packs (labPBR naming). A companion must have the sprite's width; for animated sprites only the first frame is used. Missing companions use `normalDefault` (128, 128, 255, 255) and `specularDefault` (0, 0, 0, 0), which the manifest can change.

DX12MC does not decode the channels. Mip levels are a per-channel 2×2 average, so a shader should renormalize a sampled normal. The decoding and the lighting model belong to the pack: the sample pack reads labPBR 1.3 (normal X and Y in red and green, DirectX orientation, ambient occlusion in blue; smoothness in red of `_s`).

Only the block atlas, which also holds item textures, has material atlases. Entity, armor, particle and other textures do not. The atlases cost width × height × 4 bytes × 4/3 each, for example 5.6 MiB each for a 1024 × 1024 atlas. Nothing is loaded or allocated when no active pack asks for materials.

## Compile tool

```
.\gradlew.bat compileShaderPack -PpackDir=examples\sample-pack
```

The task compiles every `vs_*.sc` and `fs_*.sc` under `assets/<namespace>/dx12mc/shaders/` with the shaderc that the build downloads and checks against the SHA-256 in `gradle.properties`, wraps each result in a `.dxsh` file, copies the other files, and writes `build/shaderpacks/<name>.zip`. The include directory `tools/shaderpack/include` holds `dx12mc.sh` and the default `varying.def.sc`. `examples/sample-pack` is a complete pack with a tint override and a normal and roughness override with a fixed light.

The 1.20.1 and 1.21.1 lines produce byte-identical `.dxsh` files from the same sources, so one build serves both unless the contract differs; use `variants` when it does.
