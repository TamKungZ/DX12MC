# DX12MC

DX12MC is a client-side Fabric mod that routes selected Minecraft Java rendering work through Direct3D 12 using bgfx.

The project is experimental and Windows-focused. It is not a general performance promise, compatibility layer, or replacement for Minecraft's renderer across every use case.

## Scope

- Minecraft Java client mod for Fabric.
- Direct3D 12 backend through bgfx.
- Rendering bridge work for geometry, textures, render state, shaders, and presentation.
- Source-first development; see `ARCHITECTURE.md` for a source-level overview.

## License

Source code is licensed under LGPL-3.0-only. See `LICENSE` and `NOTICE`.

Third-party components keep their own licenses. See `THIRD_PARTY.md`.

## Brand

The DX12MC name and project logo are not licensed under LGPL-3.0.

Forks and redistributed builds must use their own name and branding, and must not present themselves as the official DX12MC project or release channel.

## Contributions

Pull requests are welcome. Maintainer approval is required before changes are merged or released under the DX12MC name. See `CONTRIBUTING.md`.
