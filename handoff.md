# Handoff To Kimi

## Project

Hypnosia Visuals, Minecraft Fabric 1.21.11 client mod on Kotlin.

Main V2 file currently being edited:

`src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaHomeV2Layout.kt`

Build command:

`./gradlew.bat --no-daemon --max-workers=1 build`

On Windows this repo often fails with Gradle cleanup locks on generated folders like `build-runclient-local-*`. If stacktrace shows `AccessDeniedException` for a generated build folder, stop Gradle and delete only that generated folder, then rebuild.

## Important Rules

- Do not touch unrelated files.
- Do not rewrite the whole UI.
- Keep changes minimal and local to V2 layout unless absolutely needed.
- Do not commit secrets or print `figma.properties` contents.
- Preserve the dock hover behavior as much as possible.
- Run full build before final answer.

## Current Goal

Implement two V2 pages from provided HTML mockups and wire them to dock buttons:

- `Home` page under home dock button.
- `Account` page under account/cloud dock button.
- Lower-left chapter button must show current page name: `Home`, `Account`, `Visuals`, `World`, `Client`, `Hud`, `Other`.
- Other dock pages can show a simple placeholder for now.

## Existing V2 Layout State

The V2 layout already has:

- Root size `698 x 567`.
- Search pill at top.
- Main panel at `x=0`, `y=40`, `w=698`, `h=464`.
- Bottom controls around `y=520`:
  - left chapter button,
  - central dock icons,
  - right profile button.
- Dock icons and hover PNGs are already exported under:
  - `src/main/resources/assets/hypnosia/textures/gui/v2/icons/`

Currently selected nav id is stored in:

`private var selectedNavId = "home"`

## Current Incomplete Work

I already started adding Home/Account render functions into `HypnosiaHomeV2Layout.kt`.

Expected functions added or partially added:

- `renderHomePage`
- `renderHomeFriendsColumn`
- `renderHomeConfigsColumn`
- `renderAccountPage`
- `renderAccountProfile`
- `renderAccountConfigs`
- `renderPlaceholderPage`
- helpers like `renderInput`, `renderPlainInput`, `renderTextRow`, `renderKeyValue`, `drawLabel`, `drawSmall`, `rect`, `fieldBox`

Likely compile issues to check:

- Missing or misplaced `chapterLabel()`.
- `NavItem` must include `label: String`, and all `NAV_ITEMS` constructors must include it.
- Missing constants:
  - `CONTENT_PADDING`
  - `FIELD`
  - `FIELD_ALT`
  - `HOVER_FIELD`
  - `DIVIDER`
  - `BORDER_DARK`
  - `TEXT_MAIN`
  - `TEXT_MUTED`
  - `MUTED_DARK`
  - `DANGER`
- Missing text styles:
  - `HEADER_TEXT`
  - `BODY_TEXT`
  - `SMALL_TEXT`
  - `CHIP_TEXT`

## Home Page Target

Render inside main panel `698 x 464` with HTML-like layout:

- Padding `24px`.
- Two columns with vertical divider.
- Left column: `Friends`.
- Right column: `Config Manager`.
- Headers: small uppercase muted text.
- Search input fields.
- Friend list rows:
  - `Username123`
  - `GhostPlayer`
  - `Ninja_Assasin`
  - `SniperPro2000`
  - `DarkKnight`
  - `ShadowWalker`
  - `LoneWolf`
- Config rows:
  - `Default` active row with subtle bg/stroke and action markers.
  - `Legit_V1`
  - `Rage_Testing`
  - `HVH_Main`
  - `Backup_Settings`

Colors from HTML:

- Main bg: `#0D0D0D`
- Field bg: `#151515` / `#222222`
- Border: `#333333` / `#1a1a1a`
- Hover: `#2a2a2a`
- Text: `#e5e5e5`
- Muted: `#888888` / `#666666`
- Accent: `#4f46e5`
- Danger: `#ef4444`

## Account Page Target

Render inside main panel `698 x 464` with React mockup layout:

- Padding `24px`.
- Left profile column about `300px`.
- Vertical divider.
- Right config column fills remaining width.

Left profile:

- Header: `ПРОФИЛЬ АККАУНТА`.
- Key/value rows:
  - `ID Аккаунта:` / `1`
  - `Создан:` / `2026-04-19`
- Roles chips:
  - `Owner`
  - `Admin`
  - `QA`
  - `SPONSOR`
  - `USER`
- Two inactive input-looking fields:
  - `Введите имя...`
  - `Введите контакт...`
- Footer help text:
  - `Без привязанного аккаунта TG или DC восстановление невозможно.`
  - `Для переноса обращайтесь:`
  - `DS: nachosia`
  - `TG: @Hypnosia_NSXS`

Right configs:

- Section `ЛОКАЛЬНЫЕ КОНФИГИ`.
- Input-looking field `Вставьте ключ облака...`.
- Local config rows:
  - `default.cfg`
  - `legit_v2.cfg`
  - `rage_main.cfg`
  - `hvh_secret.cfg`
- Divider.
- Section `ОБЛАЧНЫЕ КОНФИГИ` with counter `10/100`.
- Cloud config rows:
  - `Cloud key 1`
  - `Cloud key 2`
  - `Cloud key 3`
  - `Cloud key 4`
  - `Cloud key 5`

## Dock / Chapter Requirements

- Dock click must still update `selectedNavId`.
- Home button shows `Home` page.
- Account/cloud button shows `Account` page.
- Bottom-left button text must be current chapter label.
- Other buttons can show centered placeholder with chapter name and `Coming soon`.
- Do not break existing hover/selected dock behavior.

## Dock Geometry Context

There was recent work around dock hover spacing:

- `NAV_ITEM_GAP = 8.0f` between icon wrapper rects.
- Central dock background should keep visual padding around item wrappers.
- Side chapter/profile buttons should preserve `16px` gap from expanded dock background.
- Existing code computes `sideX` and `profileX` from current `navLayout.dockX/dockWidth`.

Do not redesign this unless compilation or obvious layout bug requires it.

## Prompt To Paste Into Kimi

Paste this into the Kimi terminal opened in `G:\.Hypnosia_Visuals`:

```text
You are assisting on a Kotlin Minecraft Fabric UI project. Effort HIGH. Work only in src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaHomeV2Layout.kt unless absolutely necessary.

Task:
1. Finish/repair the partially implemented V2 Home and Account pages in HypnosiaHomeV2Layout.kt.
2. Home page: static visual translation of the provided HTML layout: Friends column + Config Manager column inside the 698x464 main panel, using fixed Figma px, dark colors, inputs, rows, divider.
3. Account page: static visual translation of the provided React mockup: profile column + local/cloud configs column inside the same main panel.
4. Dock home button must show Home. Account/cloud button must show Account. Other dock buttons show placeholder.
5. Bottom-left chapter button must show selected chapter name.
6. Preserve existing dock hover/selected behavior and spacing. Do not rewrite the dock unless needed to compile.
7. No external dependencies, no SVG renderer, no unrelated code.
8. Run .\gradlew.bat --no-daemon --max-workers=1 build and fix compile errors.

Important compile repair checklist:
- Ensure NavItem has label: String and all NAV_ITEMS constructors pass label.
- Ensure chapterLabel() exists inside HomeV2Node.
- Define all missing color constants and FigmaTextRenderer styles.
- Keep helper functions inside HomeV2Node.
- Use HypnosiaRenderUtils.drawFigmaBox and FigmaTextRenderer.drawInBox only.

Return concise summary of changed files and build result.
```

## How To Transfer This Chat To Kimi

1. Open the existing Kimi terminal in `G:\.Hypnosia_Visuals`.
2. Paste the prompt from the previous section.
3. Also paste this file path for context: `handoff.md`.
4. Tell Kimi: `Read handoff.md and continue from the current worktree.`
5. Do not paste secrets, tokens, or contents of `figma.properties`.
6. After Kimi edits, run `git diff -- src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaHomeV2Layout.kt` and review before accepting.
7. Run `./gradlew.bat --no-daemon --max-workers=1 build`.

## Current Build Status

Last full build after adding the new page code was not completed because Gradle hit a Windows cleanup lock before compilation. The current code may still need compile fixes.
