# Third-party components

Packaged in the mod JAR:

- **LWJGL bgfx bindings 3.3.3** - BSD-3-Clause - nested as `META-INF/jars/lwjgl-bgfx-3.3.3.jar`.
- **bgfx native library** - BSD-2-Clause - `bgfx.dll` for Windows x64, built by LWJGL and copied from the `lwjgl-bgfx` natives artifact.

License texts for the packaged components are included in the JAR under `META-INF/licenses/`.

Not packaged:

- **LWJGL core 3.3.3** - BSD-3-Clause - supplied by Minecraft.
- **Fabric Loader** - Apache-2.0 - supplied by the player's Fabric installation.
- **SpongePowered Mixin (Fabric fork)** - MIT - supplied by Fabric Loader.
- **Fabric Loom** - MIT - build-time Gradle plugin.
- **bgfx shaderc** - BSD-2-Clause - build-time shader compiler; the compiled shaders are packaged, the compiler is not.
- **JUnit Jupiter 5.11.4** - EPL-2.0 - test dependency.

Minecraft is built against Mojang's official mappings (Loom `officialMojangMappings`). They are used at build time, no mapping files are packaged, and they are subject to Mojang's terms.

DX12MC source code is licensed under LGPL-3.0-only. Third-party components keep their own licenses.
