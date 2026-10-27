# Contributing

Pull requests are welcome for focused fixes and improvements. Open an issue first for larger changes so the direction can be agreed before you write code.

## Requirements

- Windows 10 or 11. The Direct3D 12 backend and the shader build are Windows-only.
- JDK 21 to run Gradle and JDK 17 for the Java 17 toolchain that compiles the mod.
- A Direct3D 12 capable GPU to run the game or the benchmarks.

## Build and check

```
.\gradlew.bat build
```

`build` runs the unit tests, compiles and verifies the D3D12 shaders, and checks formatting. The first build downloads bgfx `shaderc` and verifies it against the SHA-256 pinned in `gradle.properties`. If you set `DX12MC_SHADERC`, it must match that hash too.

Format your changes before committing:

```
.\gradlew.bat formatJava
```

Java sources use google-java-format. `checkJavaFormat` fails the build on unformatted code.

## Tests

Include tests when a change affects behavior that can be verified without launching the game. Tests that need Minecraft classes run without a window; code that needs a live bgfx device cannot be unit tested, so describe how you checked it in the pull request.

For rendering changes, the diagnostic tasks measure the local GPU and are not part of `build`:

- `renderBench` measures bgfx submission cost and has a native correctness check (`-PbenchArgs=verify`).
- `cpuBench` times the CPU-side encoding, packing and region assembly paths.

Report measured numbers with the method and hardware when a change claims a performance improvement. Do not claim an improvement that was not measured.

## Code style

- Write code, comments, log messages and commit messages in English.
- Match the surrounding code. Prefer small, complete changes over broad refactors.
- Do not add comments that restate the code. Comment only a non-obvious reason, constraint or workaround.
- Do not add dependencies without a clear reason.
- Native bgfx handles must be released exactly once, and failures must not be swallowed silently.

## Commits

Use a short subject that starts with a capitalized imperative verb, for example `Fix plugin loading` or `Add texture region merging`. Keep each commit to one change.

## Licensing

DX12MC source code is licensed under LGPL-3.0-only. By submitting a contribution you agree to license it under the same terms. Third-party components keep their own licenses; see `THIRD_PARTY.md`.

The DX12MC name and project logo are not covered by the license, and forks must use their own name and branding. See `README.md`.

## Releases

Maintainer approval is required before changes are merged or released under the DX12MC name.
