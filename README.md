# Hypnosia Visuals

Hypnosia Visuals is a client-side Minecraft Fabric mod for `1.21.11`. It adds a custom in-game menu, visual settings, HUD modules, local profiles, and Hypnosia Cloud account/config integration.

This is a beta release: `beta 0.8.1 Hi`.

## Required Mods

- Minecraft `1.21.11`
- Fabric Loader `0.19.1` or newer
- Fabric API `0.141.3+1.21.11` or compatible newer build
- Fabric Language Kotlin `1.13.10+kotlin.2.3.20` or compatible newer build
- Java `21`

Install the required Fabric mods into the same `mods` folder as `hypnosia-0.8.1-beta-hi.jar`.

## What Is Included

- Custom Hypnosia menu opened with `Right Shift`.
- Account and cloud config UI for Hypnosia Cloud.
- Local config profiles with cloud upload/download support.
- HUD modules: Watermark, Hotbar, Armor HUD, Target HUD, Player Info, Inventory HUD, Cooldowns, Potions, and Hotkeys.
- Visuals: Aspect Ratio presets and custom free ratio mode.
- World settings: Fullbright and Custom Fog.
- Other tools: Friends, Streamer Mode, and Discord RPC.
- Profile activity screen with local playtime tracking.

## Current Notes

- If Hypnosia Cloud is offline or under maintenance, the menu shows a connection/maintenance screen with `Retry`.
- Cloud/account features require access to the Hypnosia service.
- Local HUD, visuals, and profile files are stored in the Minecraft instance, not in this repository.

## Public Release Scope

This public release is intended to include only the Minecraft client mod code, UI, client assets, shaders, and public documentation.

The account/license/cloud backend, local admin panel, deployment notes, server data, and private operations material are not part of the public release.

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
