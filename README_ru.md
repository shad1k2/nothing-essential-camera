# Nothing Essential Camera

![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple?logo=kotlin)
![Android](https://img.shields.io/badge/Android-10%2B-green?logo=android)
![LSPosed](https://img.shields.io/badge/LSPosed-Zygisk-blue?logo=android)
![Xposed](https://img.shields.io/badge/Xposed-API%2082-orange)
![Nothing Phone](https://img.shields.io/badge/Nothing-3a%2F3a%20Pro-gray?logo=android)
![License](https://img.shields.io/badge/License-GPL--3.0-red)

Модуль Xposed (LSPosed), который превращает **Essential Key** на Nothing Phone (3a / 3a Pro) в настоящую кнопку спуска затвора камеры: короткое нажатие — фото, удержание — запись видео.

## Возможности

- Короткое нажатие — фото
- Удержание — запись видео
- Двойное нажатие — переключение камеры / зум
- Громкость = зум при удержании
- Работает только в Nothing Camera; Essential Space вне камеры не ломается

## Требования

- Nothing Phone с Essential Key (3a, 3a Pro и др.)
- Root + LSPosed (Zygisk)
- Android 10+ (API 29)

## Установка

1. Соберите APK:
   ```bash
   ./gradlew assembleDebug
   ```
   APK: `app/build/outputs/apk/debug/`

2. Установите APK на телефон.

3. В LSPosed включите модуль и укажите область действия: **Nothing Camera** (и `system`/`android`, если потребуется — хук на `system_server` работает в процессе `android`).

4. Перезапустите Nothing Camera (force-stop), чтобы применились хуки.

## Настройки

Откройте модуль в LSPosed → **Launch**. Экран оформлен в стиле Nothing OS: логотип и заголовки разделов шрифтом NDot, основной текст — Geist, метаданные — Geist Mono, тёмная палитра `#1B1B1D` с красным акцентом `#D71921` (Material 3).

- Включить модуль
- Только в камере (foreground)
- Короткое нажатие: фото / фокус
- Удержание: видео / ничего
- Двойное нажатие: камера / зум
- Громкость = зум при удержании

## Проверка скан-кода

```bash
adb shell getevent -lp
```
Устройство `gpio-keys` должно иметь код **250**.

## Лицензия

GPL-3.0