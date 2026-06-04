# Hypnosia Visuals

Hypnosia Visuals is a client-side Minecraft Fabric mod for `1.21.11`. It adds a custom in-game menu, visual settings, HUD modules, local profiles, and Hypnosia Cloud account / config integration.

**Current release:** `0.9 Beta`

---

## Required Mods

- **Minecraft** `1.21.11`
- **Fabric Loader** `0.19.1` or newer
- **Fabric API** `0.141.3+1.21.11` or compatible newer build
- **Fabric Language Kotlin** `1.13.10+kotlin.2.3.20` or compatible newer build
- **Java** `21`

Install the required Fabric mods into the same `mods` folder as `hypnosia-0.9-beta.jar`.

---

## What Is Included

- **V2 Home Menu** — fully redesigned home screen with spring animations, module grid, and settings drawer (open with `Right Shift`).
- **Image Rendering** — display custom static images and animated GIFs in-game with chroma key, scaling, and positioning.
- **HUD Modules** — Watermark, Hotbar, Armor HUD, Target HUD, Player Info, Inventory HUD, Cooldowns, Potions, and Hotkeys.
- **Visuals** — Aspect Ratio presets, custom free ratio mode, Fullbright, Custom Fog.
- **World & Other** — Friends list, Streamer Mode, Discord RPC with GIF icon and profile button.
- **Cloud & Profiles** — local config profiles with cloud upload / download, profile page on [nachosia.site](https://nachosia.site) with activity tracking and role gradients.
- **QoL** — Ctrl+V paste in all input fields, scale slider safeguards, no image duplication on drawer close.

---

## Links

- **Website:** [https://nachosia.site](https://nachosia.site)
- **Privacy Policy:** [https://nachosia.site/privacy](https://nachosia.site/privacy)

---

## Build

```powershell
.\gradlew.bat build
```

For isolated local checks:

```powershell
.\gradlew.bat "-PhypnosiaBuildDir=build-check-public" build --no-daemon --no-parallel
```

---

## License

This project uses a custom source-available license. You may view the code and use it for personal, non-commercial purposes only. See [`LICENSE`](./LICENSE) for the full terms and disclaimer.

---

## Security

Do not publish account keys, license keys, Figma tokens, SSH keys, `.env` files, local account/license properties, server data, or deployment notes.
