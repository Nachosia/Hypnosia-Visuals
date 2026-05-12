# Hypnosia UI Element Registry

## hypnosia.base-menu-shell - Base Menu Shell
- Purpose: 698x464 Click GUI shell matching Figma node `1:12` (`Base`) with top search/chapter/profile slots, left icon rail, centered content host, 1920x1080 Figma reference-resolution scaling, and transformed hitboxes.
- Files: `src/main/kotlin/dev/hypnosia/ui/HypnosiaMenuScreen.kt`, `src/main/resources/assets/hypnosia/textures/gui/figma/*.png`
- Inputs/Props: active view, selected category, search query, selected module.
- States: welcome, home, category, search, profile calendar, active category highlight with white icon mask.
- Dependencies: none.
- Reuse notes: Keep all menu coordinates in Base-local space and transform render/hitboxes through the same `minOf(scaledWidth / FIGMA_WIDTH, scaledHeight / FIGMA_HEIGHT)` multiplier; center using `(scaledWidth / scaleMultiplier - 698) / 2`, `(scaledHeight / scaleMultiplier - 464) / 2`.

## hypnosia.module-row - Module Row
- Purpose: Reusable Figma-sized `263x70` Click GUI module row template matching the MC_DOCS module-list blueprint and Figma preview component geometry.
- Files: `src/main/kotlin/dev/hypnosia/ui/component/ModuleRow.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`, `src/main/resources/assets/hypnosia/textures/gui/icons/module_*.png`
- Inputs/Props: title, description, icon path, enabled state, settings-open provider, enabled-change callback, settings-open callback.
- States: enabled + settings open, enabled + settings closed, disabled + settings open, disabled + settings closed; disabled uses `#191919` header, `#FFFFFF` title, `#272727` stroke, enabled uses `#FFFFFF` header/stroke and `#000000` title.
- Dependencies: none.
- Reuse notes: Instantiate `ModuleRow(...)` inside module lists and search results; keep child geometry literal from Figma (`title 6,2`, plug `9,31`, gear/setup `226,31`) and use the dedicated Figma-exported module icon assets instead of tinting the generic sidebar icons.

## hypnosia.toggle-switch - Animated Toggle Switch
- Purpose: Reusable SDF-rendered switch with spring-animated thumb movement and animated track color for module enabled/disabled states.
- Files: `src/main/kotlin/dev/hypnosia/ui/component/ToggleSwitch.kt`, `src/main/kotlin/dev/hypnosia/ui/animation/FigmaAnimation.kt`
- Inputs/Props: initial checked state, accent color, `onChanged` callback.
- States: off, on, hover, animated transition.
- Dependencies: none.
- Reuse notes: Use `ToggleSwitch` for boolean settings and module power states; call `setChecked(value, snap)` when external module state changes.

## hypnosia.module-settings-drawer - Module Settings Drawer
- Purpose: Right-side module settings drawer matching Figma node `244:1214`, opened from the ModuleRow gear action and anchored to the Base right edge.
- Files: `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: selected module title, close callback.
- States: open, closed.
- Dependencies: none.
- Reuse notes: Keep drawer-local geometry literal from Figma (`236x318`, close at `177,17`, divider at `15,59`) and render it after the selected content page.

## hypnosia.profile-calendar-content - Profile Calendar Content
- Purpose: Base content view matching Figma node `272:744` (`profile-calendar-content`) for profile activity summaries, calendar grid, profile skin panel, and seven-day graph.
- Files: `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`, `src/main/kotlin/dev/hypnosia/ui/HypnosiaMenuScreen.kt`
- Inputs/Props: Minecraft username, active role label, local playtime snapshot, local day activity map.
- States: shown when interacting with the Base profile area.
- Dependencies: none.
- Reuse notes: Keep the view at Base-local `73,67` with size `604x370`; playtime is intentionally local-only via `HypnosiaPlaytime`.

## hypnosia.local-playtime-tracker - Local Playtime Tracker
- Purpose: Tracks local total playtime, daily activity, launch count, and login streak for the profile calendar without sending playtime data to the server.
- Files: `src/main/kotlin/dev/hypnosia/ui/profile/HypnosiaPlaytime.kt`, `src/main/kotlin/dev/hypnosia/HypnosiaClient.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: Minecraft client tick state and local date.
- States: no-world idle, in-world counting, periodic saved snapshot.
- Dependencies: none.
- Reuse notes: Store future profile-only counters in `minecraft/hypnosia/playtime.properties`; keep cloud/license data separate from local playtime data.

## hypnosia.rounded-primitive-renderer - Rounded Primitive Renderer
- Purpose: Reusable native Minecraft GUI renderer for smooth rounded rectangles, panels, and dots without external UI libraries.
- Files: `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaRenderUtils.kt`, `src/main/kotlin/dev/hypnosia/ui/render/GuiPrimitives.kt`
- Inputs/Props: local rect bounds, radius, ARGB fill/stroke color, segment count.
- States: static geometry submitted through the current DrawContext matrix state.
- Dependencies: none.
- Reuse notes: Use `GuiPrimitives.panel`, `GuiPrimitives.roundedRect`, or `HypnosiaRenderUtils.drawRoundedRect` instead of assembling rounded corners from square fills.

## hypnosia.figma-text-renderer - Figma Text Renderer
- Purpose: High-quality Figma typography renderer using Java2D-rasterized TTF glyph atlases, explicit font IDs, letter spacing, line height, and fallback vanilla text rendering.
- Files: `src/main/kotlin/dev/hypnosia/ui/render/FigmaTextRenderer.kt`, `src/main/kotlin/dev/hypnosia/ui/render/HighQualityTextRenderer.kt`, `src/main/kotlin/dev/hypnosia/render/HypnosiaShaders.kt`, `src/main/resources/assets/hypnosia/shaders/core/hq_text.vsh`, `src/main/resources/assets/hypnosia/shaders/core/hq_text.fsh`, `src/main/resources/assets/hypnosia/font/main.json`, `src/main/resources/assets/hypnosia/font/title.json`, `src/main/resources/assets/hypnosia/font/inter_regular.ttf`, `src/main/resources/assets/hypnosia/font/satyr_sp.ttf`
- Inputs/Props: text string, position, font size, ARGB color, `FigmaTextRenderer.Font.Main` or `FigmaTextRenderer.Font.Title`.
- States: static text; shadow disabled for all Figma text draws; atlas cached per font/size.
- Dependencies: none.
- Reuse notes: Use `FigmaTextRenderer.draw` / `drawInBox` for all Figma text, with explicit `FigmaTextStyle` for font, size, line height, letter spacing, and baseline offset; do not call vanilla `context.drawText` directly in Figma-mapped UI. The high-quality atlas path is automatic and falls back to vanilla only if atlas generation fails.

## hypnosia.settings-color-picker - Settings Color Picker
- Purpose: Deferred settings/color view based on Figma node `77:742`.
- Files: `src/main/kotlin/dev/hypnosia/ui/HypnosiaMenuScreen.kt`, `src/main/kotlin/dev/hypnosia/ui/model/UiTokens.kt`
- Inputs/Props: static foundational color settings for the first pass.
- States: disabled while the micro-settings drawer is removed.
- Dependencies: none.
- Reuse notes: Rebuild alongside the next settings drawer pass; do not wire this to the removed drawer path.

## hypnosia.target-hud - Target HUD Overlay
- Purpose: Figma-derived Target HUD overlay with six selectable versions, shared equipment strip, target name/health display, and player head/model preview.
- Files: `src/main/kotlin/dev/hypnosia/hud/TargetHud.kt`, `src/main/kotlin/dev/hypnosia/hud/TargetHudSettings.kt`, `src/main/kotlin/dev/hypnosia/HypnosiaClient.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: enabled state, `Version` (`V1`..`V6`), normalized `x/y` position, equipment strip toggles, held-item/armor/durability toggles, model yaw/pitch/scale.
- States: disabled, no target, V1 bar, V2 outline circle, V3 filled circle, V4 large model bar, V5 large model outline circle, V6 large model filled circle; draggable while chat is open.
- Dependencies: none.
- Reuse notes: Keep geometry in Target HUD group-local Figma pixels and render through fixed framebuffer scaling, matching `HudModulesHud`; use `TargetHudSettings` for persistence instead of hard-coded positions.

## hypnosia.sdf-figma-box - SDF Figma Box
- Purpose: Shader-backed rounded rectangle primitive for pixel-aligned Figma panels, buttons, fields, pills, and cards with CSS-style inner stroke.
- Files: `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaRenderUtils.kt`, `src/main/kotlin/dev/hypnosia/render/HypnosiaShaders.kt`, `src/main/resources/assets/hypnosia/shaders/core/sdf_rounded_rect.vsh`, `src/main/resources/assets/hypnosia/shaders/core/sdf_rounded_rect.fsh`
- Inputs/Props: x, y, width, height, radius, ARGB fill color, ARGB stroke color, stroke thickness.
- States: static; animate inputs through `AnimatedFloat`, `SpringFloat`, or `TimedTransition`.
- Dependencies: none.
- Reuse notes: Prefer `HypnosiaRenderUtils.drawFigmaBox` for raw gameplay/HUD/status rectangles and `HypnosiaRenderUtils.drawThemedBox(..., role = ThemeRole...)` for menu surfaces. Do not reintroduce global color-based theme interception; it breaks HP, armor, food, oxygen, target bars, and other gameplay indicators.

## hypnosia.sdf-gradient-box - SDF Linear Gradient Box
- Purpose: Shader-backed rounded rectangle primitive for Figma panels and indicators with two-color linear gradient fills and optional inner stroke.
- Files: `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaRenderUtils.kt`, `src/main/kotlin/dev/hypnosia/render/HypnosiaShaders.kt`, `src/main/resources/assets/hypnosia/shaders/core/sdf_linear_gradient_box.vsh`, `src/main/resources/assets/hypnosia/shaders/core/sdf_linear_gradient_box.fsh`
- Inputs/Props: x, y, width, height, radius, start/end ARGB colors, angle in degrees, optional stroke color/thickness.
- States: static or animated via color/angle interpolation.
- Dependencies: none.
- Reuse notes: Use `HypnosiaRenderUtils.drawLinearGradientBox` for gradient rows, accent fills, and future multi-color Figma effects that can be represented by two stops.

## hypnosia.hsv-picker-primitives - HSV Picker Primitives
- Purpose: Native shader primitives for the color picker canvas, hue strip, and alpha strip with checkerboard transparency preview.
- Files: `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaRenderUtils.kt`, `src/main/kotlin/dev/hypnosia/render/HypnosiaShaders.kt`, `src/main/resources/assets/hypnosia/shaders/core/hsv_color_canvas.*`, `src/main/resources/assets/hypnosia/shaders/core/hsv_hue_strip.*`, `src/main/resources/assets/hypnosia/shaders/core/hsv_alpha_strip.*`
- Inputs/Props: bounds, radius, hue degrees, selected ARGB color, checker size.
- States: static shader surfaces; handles and selected values are owned by the color picker widget.
- Dependencies: none.
- Reuse notes: Use `drawHsvColorCanvas`, `drawHueStrip`, and `drawAlphaStrip` for color settings instead of rasterizing picker gradients from Figma.

## hypnosia.sdf-rounded-texture - SDF Rounded Texture
- Purpose: Rounded image/icon renderer for PNG-backed Figma assets with anti-aliased clipping and optional active/inactive tint.
- Files: `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaRenderUtils.kt`, `src/main/kotlin/dev/hypnosia/render/HypnosiaShaders.kt`, `src/main/resources/assets/hypnosia/shaders/core/sdf_rounded_texture.vsh`, `src/main/resources/assets/hypnosia/shaders/core/sdf_rounded_texture.fsh`
- Inputs/Props: texture `Identifier`, x, y, width, height, radius, ARGB tint color.
- States: static, hover tint, active tint, disabled tint.
- Dependencies: none.
- Reuse notes: Use `HypnosiaRenderUtils.drawRoundedTexture` for exported icons, album art, profile images, and any image that needs Figma-correct rounded corners.

## hypnosia.sdf-shadow-box - SDF Shadow Box
- Purpose: Shader-backed soft outer drop shadow/glow for rounded Figma panels, popups, HUD pills, and floating controls.
- Files: `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaRenderUtils.kt`, `src/main/kotlin/dev/hypnosia/render/HypnosiaShaders.kt`, `src/main/resources/assets/hypnosia/shaders/core/sdf_drop_shadow.vsh`, `src/main/resources/assets/hypnosia/shaders/core/sdf_drop_shadow.fsh`
- Inputs/Props: x, y, width, height, radius, shadow spread, shadow blur, ARGB color.
- States: static; animate color alpha, blur, or spread for hover/focus glow.
- Dependencies: none.
- Reuse notes: Draw `drawSdfShadowBox` immediately before the matching panel/texture so the panel covers the solid center of the shadow.

## hypnosia.layout-engine - Figma Pixel Layout Engine
- Purpose: Lightweight Kotlin layout tree for Figma-style fixed, fill, and hug sizing with axis, padding, gap, and GUI-scale compensation.
- Files: `src/main/kotlin/dev/hypnosia/ui/layout/LayoutTypes.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/UiNode.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/LayoutContainer.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/FigmaRoot.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/LayoutExamples.kt`
- Inputs/Props: `Axis`, `Insets`, gap, `SizeMode.Fixed`, `SizeMode.Fill`, `SizeMode.Hug`, alignment, root design width/height.
- States: layout-only; interactive state remains on widgets/components.
- Dependencies: none.
- Reuse notes: Build repeated UI sections with `row` and `column`; use `FigmaRoot` when exact Figma physical pixels must stay stable across Minecraft GUI scale settings.

## hypnosia.main-layout-shell - Main Click GUI Shell
- Purpose: Absolute-geometry Hypnosia `Base` shell matching Figma node `1:12` at `698x464`, with MC_DOCS-defined welcome/search/category/home/settings behavior.
- Files: `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`, `src/main/kotlin/dev/hypnosia/ui/HypnosiaMenuScreen.kt`
- Inputs/Props: fixed Figma root size, category module data, selected category, selected module, search state, settings drawer state.
- States: welcome, home management, category module grid, search results, module settings drawer.
- Dependencies: none.
- Reuse notes: Use `HypnosiaMainLayout.create()` as the menu root; keep all shell coordinates as literal Base-local values and route render/hitboxes through `FigmaRoot`.

## hypnosia.scissor-utility - Matrix-Aware Scissor Clipping
- Purpose: Converts Figma-local `UiNode` bounds through the current DrawContext matrix into physical framebuffer scissor rectangles with bottom-left OpenGL origin handling.
- Files: `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaScissor.kt`, `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaRenderUtils.kt`
- Inputs/Props: `DrawContext`, local `Rect`, active matrix stack, framebuffer size, GUI scale.
- States: supports nested scissor stack; active scissor also clips custom SDF render passes.
- Dependencies: none.
- Reuse notes: Wrap clipped UI rendering in `HypnosiaScissor.withLocalRect(context, bounds) { ... }`; avoid direct scissor calls in components.

## hypnosia.scroll-column - Smooth Scroll Column
- Purpose: AutoLayout-compatible vertical scroll container with clipped children, spring-smoothed wheel scrolling, reverse-order event delegation, and optional SDF scrollbar indicator.
- Files: `src/main/kotlin/dev/hypnosia/ui/layout/ScrollColumn.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: width/height size modes, padding, gap, alignment, scroll step, scrollbar visibility.
- States: idle, scrolling, clipped overflow, scrollbar visible when content exceeds viewport.
- Dependencies: none.
- Reuse notes: Use `scrollColumn(...)` for module lists, settings panes, search results, and any overflowing Figma column instead of manually offsetting child coordinates.

## hypnosia.category-sidebar - Category Sidebar
- Purpose: Reusable Base-local `50.5x464` sidebar with exact Figma button slots, category selection state, hover/active spring transitions, and icon rendering from exported PNG assets.
- Files: `src/main/kotlin/dev/hypnosia/ui/component/CategorySidebar.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: selected category provider, category-selected callback.
- States: idle, hover, active selected category.
- Dependencies: none.
- Reuse notes: Use `CategorySidebar` as the only category rail for the Base shell; route category switching through its callback so content transitions stay centralized in `HypnosiaMainLayout`.

## hypnosia.mc-docs-client-icons-card - MC Docs Client Icons Card
- Purpose: Fixed `236x132` documentation card matching Figma node `365:6047` with absolute title/body text geometry, clipped overflow, and state-aware hover/selected stroke animation.
- Files: `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: selected state owned internally for the first pass; rendered under the Client category content.
- States: idle, hover, selected.
- Dependencies: none.
- Reuse notes: Keep card-local child coordinates literal from Figma (`title 11,9`, `body 11,33`); create a new card node only when a new MC_DOCS Figma frame is provided.

## hypnosia.procedural-menu-icons - Procedural SDF Menu Icons
- Purpose: Native fallback icon renderer for core menu chrome when exported Figma assets are missing or accidentally saved as SVG with a PNG extension.
- Files: `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaIconPrimitives.kt`, `src/main/kotlin/dev/hypnosia/ui/component/CategorySidebar.kt`, `src/main/kotlin/dev/hypnosia/ui/component/ModuleRow.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: icon file/category key, Figma-local bounds, ARGB tint.
- States: inherits hover/selected tint from the owning component.
- Dependencies: none.
- Reuse notes: Use this only for built-in menu symbols or as a guard against broken assets; real exported PNG Figma icons can still use `HypnosiaRenderUtils.drawRoundedTexture`.

## hypnosia.account-cloud-flow - Account Cloud Flow
- Purpose: Account creation, Welcome Cloud transition, and account manager content matching the Figma `accaut create`, `accaut Welcome`, and `account manager` frames inside the existing Base shell.
- Files: `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`, `src/main/resources/assets/hypnosia/textures/gui/icons/account_cloud.png`, `src/main/resources/assets/hypnosia/textures/gui/icons/cloud_outline.png`, `src/main/resources/assets/hypnosia/textures/gui/icons/cloud_config_key.png`, `src/main/resources/assets/hypnosia/textures/gui/icons/folder_select_cloud.png`
- Inputs/Props: `AccountManager.state`, local terms checkbox state, create callback, current Minecraft player name, active account session data.
- States: unchecked terms, checked terms, creating, create error, welcome cloud animation, account manager with/without active account.
- Dependencies: none.
- Reuse notes: Use the left sidebar account button or the top-right profile pill to enter the account flow; keep the black-hole topbar icon reserved for the profile/calendar screen.

## hypnosia.hud-modules-overlay - HotBaR and Armor HUD Overlay
- Purpose: Figma-derived HUD overlay modules for `HotBaR` and `Armor HUD`, with selectable version, X/Y axis layout, and normalized screen position controls.
- Files: `src/main/kotlin/dev/hypnosia/hud/HudModulesHud.kt`, `src/main/kotlin/dev/hypnosia/hud/HudModuleSettings.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: enabled state, `Version` (`V1`/`V2`), `Axis` (`X`/`Y`), normalized `x/y` position, current player inventory/equipment/status values.
- States: enabled/disabled module row, horizontal/vertical axis, V1/V2 rendering, X/Y slider dragging in the settings drawer.
- Dependencies: none.
- Reuse notes: Use `HudModuleSettings` for new HUD overlay persistence and `HudModulesHud` for fixed-framebuffer rendering that stays visually stable across Minecraft GUI scale.

## hypnosia.extra-hud-modules - Player Info, Inventory, Cooldown, Potions HUD
- Purpose: Additional Figma-derived HUD overlays for live player info, inventory grid, active item cooldowns, and status effects.
- Files: `src/main/kotlin/dev/hypnosia/hud/PlayerInfoHud.kt`, `src/main/kotlin/dev/hypnosia/hud/InventoryHud.kt`, `src/main/kotlin/dev/hypnosia/hud/CooldownHud.kt`, `src/main/kotlin/dev/hypnosia/hud/PotionsHud.kt`, `src/main/kotlin/dev/hypnosia/hud/HudRenderSupport.kt`, `src/main/kotlin/dev/hypnosia/hud/HudDragController.kt`, `src/main/kotlin/dev/hypnosia/hud/HudModuleSettings.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: enabled state, version/mode, normalized `x/y` position, current player velocity/TPS/coords/inventory/cooldowns/effects.
- States: enabled/disabled module row, V1/V2/V3/V4 where applicable, Player Info mode `BPS/TPS/CORDS/ALL`, drag in chat, clipped marquee for long cooldown/effect names.
- Dependencies: none.
- Reuse notes: Use `HudRenderSupport` for fixed-framebuffer coordinates, shared colors, Inter Medium text, and marquee; use `HudDragController` for any new simple draggable HUD overlay.

## hypnosia.icon-settings - Global Icon Settings
- Purpose: Client `Icons` module that globally hides the black-hole icon and recolors GUI/HUD icon textures through the shared render path.
- Files: `src/main/kotlin/dev/hypnosia/config/IconSettings.kt`, `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaRenderUtils.kt`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: `module.client.icons.enabled`, `icons.blackHole.visible`, `icons.color`.
- States: enabled/disabled module, black-hole show/hide, collapsed/expanded color palette.
- Dependencies: none.
- Reuse notes: Route new icon textures through `HypnosiaRenderUtils.drawIconTexture` or `drawRoundedTexture`; the global tint/black-hole filtering is applied there automatically.

## hypnosia.theme-settings - Global UI Theme Settings
- Purpose: Client `Theme` module that recolors only explicit menu/UI surfaces through semantic `ThemeRole`s, with Dark, White, Transparent, Custom gradient, and Liquid Glass modes.
- Files: `src/main/kotlin/dev/hypnosia/config/ThemeSettings.kt`, `src/main/kotlin/dev/hypnosia/ui/render/HypnosiaRenderUtils.kt`, `src/main/kotlin/dev/hypnosia/ui/render/GlassSurfaceTokens.kt`, `src/main/kotlin/dev/hypnosia/ui/render/LiquidGlassSurface.kt`, `src/main/kotlin/dev/hypnosia/ui/render/FigmaTextRenderer.kt`, `src/main/kotlin/dev/hypnosia/render/HypnosiaShaders.kt`, `src/main/resources/assets/hypnosia/shaders/core/sdf_liquid_glass_box.*`, `src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`
- Inputs/Props: `module.client.theme.enabled`, `theme.mode`, `theme.font`, `theme.liquidGlass`, `theme.gradient`, `theme.baseColor`, `theme.gradientStart`, `theme.gradientEnd`.
- States: dark baseline, white readable text/icon remap inside themed UI context, transparent tinted panels without text alpha changes, custom gradient panels, Liquid Glass layered frosted surfaces, optional custom font file at `hypnosia/fonts/custom.ttf`.
- Dependencies: none.
- Reuse notes: New menu surfaces must use `HypnosiaRenderUtils.drawThemedBox` with an explicit `ThemeRole` (`MAIN_PANEL`, `CARD`, `HEADER`, `BUTTON`, `INPUT`, `DRAWER`, `ICON_BUTTON`). `drawThemedBox` delegates Liquid Glass surfaces to `LiquidGlassSurface`, which draws the full layered glass stack. HUD/gameplay overlays must keep using raw `drawFigmaBox`; transparent/glass affects surfaces only and must never reduce text/icon alpha or recolor HP/armor/food/oxygen/status bars.

## hypnosia.other-runtime-modules - Friends TAB Marker and Discord RPC Foundation
- Purpose: Runtime behavior for `Friends` and `Discord RPC` modules: TAB friend marking from `friends.txt`, and Discord Rich Presence lines from account state.
- Files: `src/main/kotlin/dev/hypnosia/other/FriendsManager.kt`, `src/main/java/dev/hypnosia/mixin/PlayerListHudMixin.java`, `src/main/kotlin/dev/hypnosia/other/DiscordRpcManager.kt`, `src/main/resources/hypnosia.mixins.json`, `src/main/resources/fabric.mod.json`
- Inputs/Props: `module.other.friends.enabled`, `friends.txt`, `module.other.discord_rpc.enabled`, `discord_rpc.applicationId`, `AccountManager.state`.
- States: Friends on/off, friend name decorated in TAB, Discord RPC disconnected/no app id/connected, no-account line `no acc`, account lines `ID: ...` and `Role: ...`.
- Dependencies: none.
- Reuse notes: Keep Discord app id out of source control; set it through client settings or a future drawer field. Friend display should continue routing through `FriendsManager.decorateTabName` so the TAB mixin stays minimal.
