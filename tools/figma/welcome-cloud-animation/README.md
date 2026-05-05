# Hypnosia Welcome Cloud Animator

Локальный Figma plugin для слоя `accaut Welcome`.

Он ищет frame `496:450` или frame с именем `accaut Welcome`, удаляет старые сгенерированные кадры и создаёт 4 стадии анимации:

1. `accaut Welcome / 01 cloud idle`
2. `accaut Welcome / 02 cloud split`
3. `accaut Welcome / 03 text forming`
4. `accaut Welcome / 04 final Welcome Cloud`

Идея анимации: один конец облака вытягивается в белую линию, линия превращается в начало буквы, затем появляется надпись `Welcome Cloud`.

## Как запустить

1. Открой файл `GUI` в Figma.
2. Открой меню `Plugins -> Development -> Import plugin from manifest...`.
3. Выбери файл:

   `G:\.Hypnosia_Visuals\tools\figma\welcome-cloud-animation\manifest.json`

4. Запусти plugin:

   `Plugins -> Development -> Hypnosia Welcome Cloud Animator`

Plugin создаст кадры под исходным `accaut Welcome` и попробует поставить Smart Animate prototype-переходы.

Если текущий Figma runtime не даст выставить prototype reactions через API, кадры всё равно будут созданы. Тогда поставь переходы вручную:

- `01 -> 02`: After delay `250ms`, Smart Animate `500ms`, Ease Out
- `02 -> 03`: After delay `50ms`, Smart Animate `700ms`, Ease In Out
- `03 -> 04`: After delay `50ms`, Smart Animate `500ms`, Ease Out
