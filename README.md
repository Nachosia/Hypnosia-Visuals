# Hypnosia Visuals

Hypnosia Visuals is a client-side Minecraft Fabric mod with a custom UI and visual/HUD configuration system.

## Public Release Scope

This public release is intended to include only the Minecraft client mod code, UI, client assets, shaders, and public documentation.

The account/license/cloud backend, local admin panel, deployment notes, server data, and private operations material are not part of the public release.

## Requirements

- Minecraft 1.21.11
- Fabric Loader
- Fabric API
- Java 21
- Kotlin JVM 2.3.20 through Gradle

## Build

```powershell
.\gradlew.bat build
```

For isolated local checks:

```powershell
.\gradlew.bat "-PhypnosiaBuildDir=build-check-public" build --no-daemon --no-parallel
```

## License

This project uses a custom source-available license. You may view the code and use it for personal, non-commercial purposes only. See `LICENSE` for the full terms and disclaimer.

## Security

Do not publish account keys, license keys, Figma tokens, SSH keys, `.env` files, local account/license properties, server data, or deployment notes. See `SECURITY.md`.
