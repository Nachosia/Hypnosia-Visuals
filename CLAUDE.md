# CLAUDE.md

Инструкция для Claude/AI-ассистента по проекту Hypnosia Visuals.

## Главная цель проекта

Hypnosia Visuals - клиентский Minecraft Fabric 1.21.11 мод на Kotlin с кастомным UI, который переносится из Figma максимально 1:1. В проекте также есть отдельная серверная часть для аккаунтов, ролей, cloud-config и локальная админ-панель.

Главное правило: не ломать уже работающий рендер, layout и серверную схему. Любые изменения должны быть точечными и проверяться сборкой.

## Модули Gradle

Проект состоит из 3 частей:

1. Корневой мод Minecraft:
   - `src/main/kotlin/dev/hypnosia/...`
   - `src/main/resources/...`
   - собирается обычным `./gradlew build`

2. License/account API server:
   - `license-server/src/main/kotlin/dev/hypnosia/licenseserver/LocalLicenseServer.kt`
   - standalone Kotlin/JVM HTTP server без внешних веб-фреймворков
   - отвечает за аккаунты, role/license keys, HWID bind, cloud-config, presence, notifications

3. Admin panel:
   - `license-admin/src/main/kotlin/dev/hypnosia/licenseadmin/AdminPanelServer.kt`
   - standalone Kotlin/JVM HTTP server
   - работает только локально на VPS через SSH tunnel

Подпроекты подключены в `settings.gradle.kts`:

```kotlin
include("license-server")
include("license-admin")
```

## Технологии и ограничения

- Minecraft: `1.21.11`
- Fabric Loader + Fabric API
- Kotlin JVM `2.3.20`
- Java `21`
- Без внешних runtime-библиотек для UI/рендера.
- UI рендерится через vanilla Minecraft internals:
  - `DrawContext`
  - `Tessellator`
  - `BufferBuilder`
  - `BufferRenderer`
  - `RenderSystem`
  - custom Core Shaders
- Серверы `license-server` и `license-admin` используют стандартный `com.sun.net.httpserver.HttpServer`.

## Важные команды

Полная сборка:

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

Сборка только админки:

```powershell
.\gradlew.bat :license-admin:build
```

Сборка только license server:

```powershell
.\gradlew.bat :license-server:build
```

Экспорт иконок из Figma:

```powershell
.\gradlew.bat exportFigmaIcons
```

Для экспорта нужен `FIGMA_PAT`/`FIGMA_TOKEN` или локальный `figma.properties`. Не коммитить `figma.properties`.

## Безопасность репозитория

Нельзя коммитить:

- `figma.properties`
- `.env`
- реальные токены Figma
- SSH private keys
- пароли админки
- локальные `account.properties`
- локальные `license.properties`
- дампы базы с реальными ключами

Примеры безопасных файлов:

- `.env.example`
- `figma.properties.example`
- документация без секретов

## Minecraft client: структура

### Entry point

`src/main/kotlin/dev/hypnosia/HypnosiaClient.kt`

Отвечает за клиентскую инициализацию мода:

- регистрация client-only логики
- запуск проверки аккаунта/ролей на старте, если есть локальный account key
- регистрация команды `/actkey <role-key>`
- открытие/подключение UI

### Главное меню

`src/main/kotlin/dev/hypnosia/ui/HypnosiaMenuScreen.kt`

Экран меню. Важное:

- не должен рисовать vanilla background blur/dark gradient
- должен оставлять мир видимым за UI
- вызывает layout/render корня меню
- передает mouse events в UI tree

### Основной layout

`src/main/kotlin/dev/hypnosia/ui/layout/HypnosiaMainLayout.kt`

Главная сборка UI:

- root window
- sidebar
- top bar
- Home/Profile/Account/Visuals/etc. sections
- module grid
- account create/account manager screens

Если надо править внешний вид меню, почти всегда начинать отсюда.

### Layout engine

Папка:

`src/main/kotlin/dev/hypnosia/ui/layout/`

Ключевые файлы:

- `UiNode.kt` - базовый интерфейс UI-ноды
- `LayoutContainer.kt` - row/column layout
- `LayoutTypes.kt` - размеры, padding, axis, sizing modes
- `FigmaRoot.kt` - корень, фиксированные Figma-пиксели и scaling
- `ScrollColumn.kt` - scroll container + scissor clipping
- `UiInputState.kt` - состояние ввода мыши/клавиатуры

Правило: UI строится в фиксированных Figma pixel units. Нельзя рандомно центрировать или подбирать offsets "на глаз", если в Figma есть точные координаты.

### Components

Папка:

`src/main/kotlin/dev/hypnosia/ui/component/`

Файлы:

- `CategorySidebar.kt` - левая навигация, иконки категорий, hover/selected states
- `ModuleRow.kt` - карточка модуля 263x70 по Figma template
- `ToggleSwitch.kt` - переключатель с анимацией

### Profile/playtime

Папка:

`src/main/kotlin/dev/hypnosia/ui/profile/`

Главный файл:

- `HypnosiaPlaytime.kt`

Отвечает за локальное время игры, календарь активности, график последних 7 дней и блок со skin/player model.

Время игры хранится локально, не на сервере.

## UI render

Папка:

`src/main/kotlin/dev/hypnosia/ui/render/`

Файлы:

- `HypnosiaRenderUtils.kt` - SDF boxes, shadows, rounded textures, custom primitives
- `HypnosiaScissor.kt` - OpenGL scissor для clipping
- `FigmaTextRenderer.kt` - основной текст под Figma
- `HighQualityTextRenderer.kt` - улучшенный текстовый рендер
- `HypnosiaIconPrimitives.kt` - fallback/procedural icon primitives

### Важные правила рендера Minecraft 1.21.11

При кастомных примитивах:

1. Перед raw buffer draw вызвать:

```kotlin
context.draw()
```

2. Включить blend:

```kotlin
RenderSystem.enableBlend()
RenderSystem.defaultBlendFunc()
```

3. Не кешировать `BufferBuilder`.

4. Для каждого draw call создавать fresh buffer:

```kotlin
val tessellator = Tessellator.getInstance()
val buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
```

5. После `buffer.end()` нельзя использовать этот buffer повторно.

6. Вершины должны проходить через актуальную матрицу `DrawContext`:

```kotlin
val matrix = context.matrices.peek().positionMatrix
val vec = Vector4f(x, y, z, 1.0f).mul(matrix)
buffer.vertex(vec.x, vec.y, vec.z)
```

Нельзя терять Z-depth. Ранее из-за flatten Z в `0.0f` SDF quads пропадали на GUI Scale 2+.

### Scissor clipping

`RenderSystem.enableScissor(x, y, width, height)` принимает physical pixels и origin снизу-слева.

Minecraft GUI coordinates идут сверху-слева и масштабируются GUI scale.

Поэтому scissor должен учитывать:

- `window.scaleFactor`
- `window.height`
- текущую matrix transform

Не делать scissor по raw Figma coordinates без пересчета.

## Shaders

Kotlin registration:

`src/main/kotlin/dev/hypnosia/render/HypnosiaShaders.kt`

GLSL files:

`src/main/resources/assets/hypnosia/shaders/core/`

Сейчас есть:

- `sdf_rounded_rect.*`
- `sdf_drop_shadow.*`
- `sdf_linear_gradient_box.*`
- `sdf_rounded_texture.*`
- `hsv_color_canvas.*`
- `hsv_hue_strip.*`
- `hsv_alpha_strip.*`
- `hq_text.*`

Если добавлять новый shader:

- добавить `.vsh` и `.fsh`
- зарегистрировать в `HypnosiaShaders.kt`
- добавить wrapper в `HypnosiaRenderUtils.kt`
- проверить `./gradlew build`
- проверить в игре, что не ломается vanilla `DrawContext`

## Assets

Иконки:

`src/main/resources/assets/hypnosia/textures/gui/icons/`

Шрифты:

`src/main/resources/assets/hypnosia/font/`

Основные:

- `satyr_sp.ttf/.otf` - title/welcome style
- `inter_regular.ttf` - main UI text
- `title.json`
- `main.json`

Фигма файл:

`4BfRUKPJD8vnOHSQbrzmKS`

Экспорт иконок автоматизирован Gradle task `exportFigmaIcons`.

Важно: Figma SVG не использовать напрямую в Minecraft resources. Нужны PNG.

## License/account system: client

Папка:

`src/main/kotlin/dev/hypnosia/license/`

Файлы:

- `AccountManager.kt` - клиентские запросы account/cloud/presence/notifications
- `AccountConfig.kt` - локальный account config
- `LicenseManager.kt` - role/license check/session roles
- `LicenseConfig.kt` - legacy license key config
- `LicenseRole.kt` - роли
- `HardwareFingerprint.kt` - HWID hash
- `HypnosiaPaths.kt` - пути Hypnosia в `.minecraft/hypnosia`
- `ActKeyCommand.kt` - единственная команда `/actkey <role-key>`

### Локальные файлы пользователя

В обычной Minecraft сборке данные лежат не в repo, а рядом с instance/game:

```text
.minecraft/hypnosia/account.properties
.minecraft/hypnosia/license.properties
.minecraft/hypnosia/configs/
```

`account.properties` содержит account key/id.

`license.properties` оставлен для совместимости с role/license key.

### Команды

Сейчас должна оставаться только:

```text
/actkey <32-char-role-key>
```

Старые команды `/account`, `/accaunt`, `/cloudcfg` не должны быть основной логикой. Account/cloud управление должно идти через меню.

## License server

Файл:

`license-server/src/main/kotlin/dev/hypnosia/licenseserver/LocalLicenseServer.kt`

Хранилища TSV:

```text
data/accounts.tsv
data/account-role-links.tsv
data/licenses.tsv
data/cloud-configs.tsv
data/account-presence.tsv
data/notifications.tsv
data/role-settings.tsv
```

На VPS реальные пути обычно находятся в:

```text
/opt/hypnosia/data/
```

### Основные API

Account:

- `POST /api/account/create`
- `POST /api/account/info`
- `POST /api/account/set-name`
- `POST /api/account/set-contact`
- `POST /api/account/apply-key`

Cloud config:

- `POST /api/cloud-config/save`
- `POST /api/cloud-config/load`
- `POST /api/cloud-config/delete`
- `POST /api/cloud-config/list`

Session/presence:

- `POST /api/session/online`
- `POST /api/session/offline`
- `POST /api/notifications/poll`

License legacy:

- `POST /api/license/check`

### Role key binding rule

Role/license key должен быть жестко привязан к одному `accountId`.

Если key уже связан с account #1, account #2 не должен получить эту роль, пока админ явно не отвяжет key.

Текущая ошибка для такого случая:

```text
LICENSE_ACCOUNT_BOUND
```

В админке есть действия:

- `Unlink ID` - отвязать role key от accountId
- `Reset HWID` - сбросить HWID key и account link
- `Delete` - удалить key

### Cloud config limits

Роли имеют настройки:

- `cloudLimit`
- `saveCooldownSeconds`
- `loadCooldownSeconds`

Defaults:

- `USER`: 3 конфига, 15 секунд save/load cooldown
- `SPONSOR`: 15 конфигов
- `ADMIN`/`OWNER`: 100 конфигов

Настройки ролей хранятся в `role-settings.tsv`.

## Admin panel

Файл:

`license-admin/src/main/kotlin/dev/hypnosia/licenseadmin/AdminPanelServer.kt`

Админка должна быть доступна только локально на VPS:

```text
127.0.0.1:9090
```

Открывать с ПК только через SSH tunnel:

```powershell
ssh -i "$env:USERPROFILE\.ssh\hypnosia_codex" -L 9090:127.0.0.1:9090 root@<VPS_IP>
```

Потом браузер:

```text
http://127.0.0.1:9090
```

Нельзя открывать `9090` публично.

### Разделы админки

- Overview/stats
- Notifications
- Accounts
- Role Keys
- Cloud Configs
- Debug logs 24h

### Что умеет админка

Accounts:

- создать account
- изменить display name/contact
- сбросить account key
- сбросить HWID
- ban/unban cloud upload
- удалить account
- открыть detail page по ID
- скачать `account.properties`

Role Keys:

- создать key
- задать роль
- задать custom role
- задать cloud slots/cooldowns
- скачать `license.properties`
- reset HWID
- unlink ID
- delete

Cloud Configs:

- посмотреть owner account
- скачать
- disable/enable
- delete

Notifications:

- отправить сообщение online пользователям
- сообщение приходит в Minecraft chat как:

```text
Nachosia [message]
```

Presence:

- клиент при старте/сессии сообщает online
- при закрытии сообщает offline
- offline пользователям уведомления не отправляются

Debug logs:

- кнопка `Debug logs 24h`
- читает `journalctl` за последние 24 часа по `hypnosia-license` и `hypnosia-admin`
- сервисный пользователь должен иметь доступ к группе `systemd-journal`

## Figma/tools

Папка:

`tools/figma/welcome-cloud-animation/`

Это development Figma plugin для генерации/редактирования welcome cloud animation frames.

Файлы:

- `manifest.json`
- `code.js`
- `README.md`

Использовать только как dev tooling. Не связано напрямую с runtime Minecraft.

## Документация

Папка:

`docs/`

Важные файлы:

- `license-admin-panel.md`
- `license-console-server.md`
- `license-hwid-plan.md`
- `license-server-security.md`
- `public-repo-security.md`
- `ui-element-registry.md`

Если меняется серверная логика или безопасность, обновить docs.

## Правила работы с UI/Figma

1. Если пользователь просит 1:1 Figma - сначала читать слой/узел Figma, не гадать.
2. Все размеры переносить в fixed px.
3. Не заменять абсолютную геометрию auto layout, если Figma явно задает coordinates.
4. Не менять все меню ради одного элемента.
5. Не ломать существующие hover/active animations.
6. Иконки брать из Figma/exported PNG, не рисовать похожие вручную, если asset есть.
7. Если asset выглядит криво - проверить сам PNG и `drawRoundedTexture`/UV/size/tint.

## Правила работы с сервером

1. Не добавлять внешние базы без явного решения. Сейчас база - TSV.
2. Любая запись должна быть atomic write.
3. Не хранить raw HWID, только hash.
4. Role key не должен переходить на другой accountId без admin unlink/reset.
5. Admin panel не должна быть публичной.
6. Не логировать account keys/license keys целиком без причины.
7. Перед деплоем обязательно `./gradlew build`.

## Частые проблемы

### SDF shapes invisible

Проверить:

- `context.draw()` перед custom draw
- `RenderSystem.enableBlend()`
- Matrix4f transform с сохранением Z
- shader registration
- vertex format совпадает с shader

### Buffer already closed

Причина: повторное использование `BufferBuilder` или смешивание vanilla batches без flush.

Правило: fresh buffer на каждый primitive, `context.draw()` до custom shader draw.

### Icons cropped/incorrect

Проверить:

- PNG экспорт из Figma с padding
- UV в `drawRoundedTexture`
- tintColor не затемняет белую иконку
- размер в UI соответствует Figma

### Account не создается / network error

Проверить:

- domain/API endpoint
- nginx proxy to `127.0.0.1:8080`
- `hypnosia-license` active
- local `account.properties`
- HWID format 64 hex chars

### Admin logs permission error

Проверить:

```bash
id hypnosia
```

Пользователь должен быть в группе:

```text
systemd-journal
```

## Что обязательно делать перед финальным ответом

1. Показать, какие файлы изменены.
2. Указать, прошла ли сборка.
3. Если деплой был на VPS - указать, какие сервисы перезапущены и активны ли они.
4. Если что-то не проверено - сказать прямо.

## Нельзя делать

- Нельзя коммитить/показывать пароли.
- Нельзя открывать admin panel наружу.
- Нельзя удалять чужие изменения без запроса.
- Нельзя использовать destructive git commands.
- Нельзя переписывать весь UI без причины.
- Нельзя добавлять сторонние библиотеки для UI/рендера без явного согласия.
