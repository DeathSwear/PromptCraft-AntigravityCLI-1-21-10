# PromptCraft — Состояние проекта и контекст разработки (Project State & Dev Memory)

> **Назначение документа:** Этот файл сохраняет полную историю разработки, архитектурные решения, контекст диалога с AI-ассистентом (Antigravity), решённые баги и инструкцию для продолжения работы при передаче проекта, форке или возобновлении сессии.

---

## 1. Общие сведения о проекте
- **Название:** PromptCraft
- **Платформа:** Minecraft Java Edition 1.21.4 / 1.21.10 (Fabric Loader, Fabric Loom 1.15.5)
- **Язык разработки:** Java 21
- **Репозиторий:** `https://github.com/DeathSwear/PromptCraft-AntigravityCLI-1-21-10.git` (ветка `main`)
- **Основная концепция:** Генерация, редактирование и стилизация построек в Minecraft с помощью больших языковых моделей (LLM: Gemini, OpenAI, Claude, DeepSeek, Local Ollama/LM Studio) в реальном времени.

---

## 2. Архитектура проекта и структура пакетов

```
dev.promptcraft/
├── PromptCraft.java                # Главный entrypoint мода (сервер/общий)
├── PromptCraftCommands.java        # Регистрация всех команд (/pwand, /pmenu, /pconfirm, /pcancel, /pundo, /predo, /protate, /paccess)
├── ai/
│   ├── AiClient.java               # Обработка запросов к LLM (stream-генерация, prompt modify, fallback-провайдеры)
│   ├── LlmProvider.java            # Перечисление провайдеров (OpenAI, Gemini, Anthropic, Ollama и др.)
│   └── SystemPrompts.java          # Системные промпты и правила вывода JSON для ИИ
├── client/
│   ├── PromptCraftClient.java      # Client entrypoint, бинды клавиш, выделение области, кеширование
│   ├── ModifyDraftState.java       # Сохранение драфта промпта для вкладки "Изменить"
│   ├── gui/
│   │   ├── PromptCraftSettingsScreen.java # Главный экран меню (/pmenu)
│   │   ├── Layout.java             # Геометрия меню, расчет координат, динамический шаги вкладок
│   │   ├── SettingsState.java      # Состояние UI параметров (модели, стили, флаги, ключи)
│   │   └── tab/
│   │       ├── CreateTab.java      # Вкладка генерации новой структуры
│   │       ├── ModifyTab.java      # Вкладка точечного изменения существующей зоны (in-painting)
│   │       ├── GenerationTab.java  # Параметры ИИ, выбор провайдера и модели
│   │       ├── VisualTab.java      # Настройки визуала, переключатель процедурного текстурирования
│   │       ├── ApiKeysTab.java     # Ввод и валидация API-ключей
│   │       ├── PromptsTab.java     # Настройка кастомных пресетов промптов
│   │       ├── HistoryTab.java     # История последних генераций и логирование
│   │       └── AboutTab.java       # Справка о проекте и авторство
│   └── render/
│       ├── SelectionRenderer.java  # Рендер светящейся рамки выделения области (палочка /pwand)
│       └── GhostPreviewRenderer.java # Рендер полупрозрачной структуры перед подтверждением
├── config/
│   ├── PromptCraftConfig.java      # Сериализуемый конфиг (JSON)
│   ├── PromptCraftConfigManager.java # Чтение/запись config.json
│   ├── PromptCraftEnv.java         # Безопасное хранение API-ключей в .env/секретах
│   └── PromptCraftLang.java        # Локализация RU / EN
├── network/
│   ├── PromptCraftNetworking.java  # Обработчики пакетов (C2S и S2C)
│   └── PromptCraftPayloads.java    # Определение CustomPayload (SaveGui, Action, Progress, Placement и т.д.)
├── selection/
│   ├── PlayerSelection.java        # Модель выделенной зоны кубоида (min, max, dimensions)
│   └── SelectionManager.java       # Хранение активного выделения игрока на сервере
├── session/
│   ├── GenerationSession.java      # Сессия активного запроса к ИИ, тайминги, отмена
│   └── PromptSessionManager.java   # Управление текущими процессами генерации игроков
├── structure/
│   ├── PromptCraftStructure.java   # Формат структуры: список операций (fill, place, hollow_box)
│   ├── StructureBlockCodec.java    # Парсинг и валидация BlockState из Minecraft ID
│   ├── StructureScanner.java       # Сканирование блоков в мире и сжатие в компактный JSON
│   ├── ProceduralTexturizer.java   # Процедурное текстурирование (3D Hermite Noise + weathering gradient)
│   ├── StructureRotationUtil.java  # Поворот структуры вокруг оси Y на 90/180/270 градусов
│   ├── BlockPlacementUtil.java     # Определение флагов установки блоков
│   ├── ConnectingBlocks.java       # Обработка соединяющихся блоков (стены, заборы, стекло, лестницы)
│   ├── BlockSnapshot.java          # Снимок блока (BlockPos + BlockState + NBT) для Undo/Redo
│   └── HistoryManager.java         # Стек истории Undo / Redo
└── task/
    ├── Task.java                   # Интерфейс тикаемой задачи
    ├── TaskManager.java            # Серверный планировщик задач на ServerTickEvents.END_SERVER_TICK
    ├── BuildTask.java              # Поэтапная расстановка блоков с бюджетом в тик (2048 бл/тик)
    └── DestructionTask.java        # Поэтапная очистка зоны перед генерацией нового здания
```

---

## 3. Ключевые возможности и как они работают

### А. Взаимодействие игрока с модом
1. **Выделение области (`/pwand`):**
   - Дает в руку деревянную мотыгу (`WOODEN_HOE`).
   - ЛКМ — установка первой точки, ПКМ — установка второй точки.
   - Вокруг выделения рендерится светящаяся анимированная рамка с размерами (`XxYxZ`).
   - *Важно:* Палочка WorldEdit (деревянный топор) выделяет зону для WorldEdit, но PromptCraft использует свои C2S пакеты выделения. Для PromptCraft выделение делается через `/pwand`.
2. **Меню управления (`/pmenu`):**
   - Открывает GUI с 8 вкладками. Вкладки автоматически масштабируются под экран (`Layout.java`).
3. **Режимы генерации:**
   - **Создать (Вкладка 1):** Очищает выделенную зону (`DestructionTask`) и возводит новую структуру с нуля.
   - **Изменить (Вкладка 2 — In-place Modification):** Сканирует существующие блоки внутри выделения (`StructureScanner`), отправляет их ИИ и применяет только изменения поверх существующего мира без стирания.
4. **Свободное размещение (Free Placement Mode):**
   - Структура отображается призрачным голографическим контуром (`GhostPreviewRenderer`).
   - Стрелками или кнопками мыши можно двигать и вращать постройку (`/protate`).
   - Подтверждение установки: клавиша `Enter` или команда `/pconfirm`.
   - Отмена: `/pcancel`.
5. **История и откат:**
   - `/pundo` — откат последней генерации или модификации.
   - `/predo` — возврат отмененного действия.

---

### Б. Процедурное текстурирование (`ProceduralTexturizer.java`)
Предотвращает эффект монотонных "коробок", типичный для генераций языковых моделей:
- **Алгоритм:** Быстрый 3D шум на базе целочисленного хеширования и кубической интерполяции Эрмита (`smoothstep`).
- **Градиент влажности:** Нижние 1-2 блока каменных конструкций получают естественное старение (замшелые кирпичи, булыжник).
- **Материалы стен:**
  - `stone_bricks` -> `mossy_stone_bricks`, `cracked_stone_bricks`, `andesite`, `cobblestone`.
  - `deepslate_bricks` -> `cracked_deepslate_bricks`, `cobbled_deepslate`, `deepslate_tiles`.
  - `cobblestone` -> `mossy_cobblestone`, `stone`, `andesite`.
  - `sandstone` / `red_sandstone` -> `smooth_sandstone`, `cut_sandstone`, `chiseled_sandstone`.
  - `bricks` -> `mud_bricks`, `granite`, `terracotta`.
  - `planks` -> акценты из обтесанного дерева (`stripped_wood`).
- **Защита геометрии:** Ступени, полублоки, двери, факелы и функциональные блоки не модифицируются, сохраняя целостность дизайна.
- **Управление:** Включается/выключается во вкладке «Визуал» (`VisualTab.java`) и сохраняется в `PromptCraftConfig.proceduralTexturing`.

---

### В. Сканирование и доработка существующих построек (`StructureScanner.java`)
- Для вкладки «Изменить» существующие блоки в выделенной зоне `[0, 0, 0] ... [W, H, D]` сканируются сервером.
- Воздух игнорируется. Одинаковые подряд идущие блоки объединяются в 1D-спаны `fill`.
- Формат сжатия:
  ```json
  [
    {"t":"fill","f":[0,0,0],"to":[4,0,4],"b":"minecraft:stone_bricks"},
    {"t":"place","p":[2,1,0],"b":"minecraft:oak_door[facing=south]"}
  ]
  ```
- ИИ получает этот контекст и генерирует **только дельту** (добавление новых элементов, замена существующих или удаление через `minecraft:air`).
- `BuildTask` записывает снимки заменяемых блоков для `/pundo` и размещает новые, не уничтожая остальную часть постройки.

---

## 4. История решённых багов и инцидентов

1. **Вылет при открытии `/pmenu` (IndexOutOfBoundsException / GUI Layout):**
   - *Причина:* При добавлении 8 вкладок координаты кнопок выходили за пределы экрана и накладывались.
   - *Решение:* В `Layout.java` уменьшен вертикальный отступ шага (`tabStep` уменьшен до 23, `menuY` поднят до `centerY() - 95`).
2. **Netty EncoderException при входе в Free Placement (`start_free_placement`):**
   - *Причина:* Пакет с сырыми операциями структуры превышал лимиты стандартного буфера пакета Netty.
   - *Решение:* Оптимизирована сериализация данных структуры и разделение на чанки.
3. **Пустая генерация (область очищалась, но ничего не появлялось):**
   - *Причина:* Неправильный парсинг JSON ответов от некоторых провайдеров или пустой ответ от ИИ приводил к тому, что `DestructionTask` запускался, а `BuildTask` завершался с 0 блоков.
   - *Решение:* Добавлена строгая предвалидация структуры до запуска очистки территории.
4. **Невозможность выделить область палочкой WorldEdit:**
   - *Причина:* Игрок использовал деревянный топор WorldEdit.
   - *Решение:* Добавлены разъясняющие подсказки в чат о том, что для PromptCraft требуется палочка мода (`/pwand` — деревянная мотыга).
5. **Java Compiler error: integer number too large в ProceduralTexturizer:**
   - *Причина:* Константа `3266489917` превышала знаковый `Integer.MAX_VALUE`.
   - *Решение:* Заменена на `(int) 3266489917L`.

---

## 5. Инструкция по сборке и запуску

### Требования:
- JDK 21 или выше
- Gradle 8+ (используется встроенный `./gradlew`)
- Клиент Minecraft с Fabric 1.21.4 (или 1.21.10 с поддержкой Fabric API)

### Команды:
- Сборка мода:
  ```powershell
  ./gradlew build -x test
  ```
- Готовый jar-файл создается в:
  `build/libs/promptcraft-1.0.0.jar`
- Целевой путь для локального клиента:
  `C:\Users\v_gol\AppData\Roaming\.tlauncher\legacy\Minecraft\game\mods\promptcraft-1.0.0.jar`

---

## 6. Перспективный Roadmap для следующих итераций

1. **Интеграция с WorldEdit API:**
   - Подписаться на события выделения WorldEdit (`WorldEdit.getInstance().getSession(player).getSelection()`), чтобы игроки могли использовать как `/pwand`, так и `//wand`.
2. **Дообучение (Fine-Tuning) и LoRA для архитектурных стилей:**
   - Собрать датасет из Minecraft schematics (NBT -> компактный JSON PromptCraft).
   - Создать специализированную легковесную модель (например, на базе Llama 3 8B / Qwen 2.5 Coder), понимающую пропорции, кровлю и расстановку интерьера.
3. **Воксельный стриминг-превью:**
   - Отображение постройки блоками-призраками в реальном времени прямо во время стриминга токенов от ИИ (до нажатия кнопки подтверждения).
4. **Поддержка вращения всех блоков со сложными свойствами (`BlockProperties`):**
   - Расширить `StructureRotationUtil` для поворота направлений сундуков, ступеней, дверей, калиток и баннеров при любом угле поворота (`90`, `180`, `270`).
