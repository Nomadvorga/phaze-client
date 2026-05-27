package vorga.phazeclient.base.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Tiny i18n dispatcher for the menu's user-facing strings.
 *
 * <p>Two locales right now: {@link #EN} (default) and {@link #RU}.
 * Add a third by extending the static map below; lookup falls back
 * to the EN entry when a key is missing in the active locale, so a
 * partially-translated locale still renders something sane instead
 * of crashing on a {@code null} text.
 *
 * <p>Module names + category labels are intentionally NOT keyed
 * here - the user explicitly asked for those to stay in their
 * canonical (English) form regardless of locale.
 *
 * <p>Active locale is read from {@link
 * vorga.phazeclient.implement.features.modules.client.Theme#language}
 * which routes through SelectSetting + the standard config save
 * pipeline, so the choice survives restarts.
 */
public final class Lang {

    public static final String EN = "English";
    public static final String RU = "Русский";

    /** Active locale key. Set by Theme on every change-listener tick. */
    private static volatile String active = EN;

    public static void setActive(String locale) {
        if (RU.equals(locale)) {
            active = RU;
        } else {
            active = EN;
        }
    }

    public static String getActive() {
        return active;
    }

    public static String t(String key) {
        Map<String, String> table = RU.equals(active) ? RU_TABLE : null;
        if (table != null) {
            String v = table.get(key);
            if (v != null) return v;
        }
        // EN fallback: lookup in EN_TABLE so a missing translation
        // returns the English text rather than a raw key tag like
        // {@code "modal.share.title"}.
        String fallback = EN_TABLE.get(key);
        return fallback != null ? fallback : key;
    }

    /**
     * Translates a free-form English string to the active locale.
     * Used by setting names / module descriptions where the source
     * text IS the canonical English wording (no separate "key" /
     * "value" pair). Falls back to the input when no translation
     * is registered, so unknown strings render verbatim.
     *
     * <p>Module / category names skip this on purpose - the user
     * explicitly asked for those to remain English.
     */
    public static String translate(String english) {
        if (english == null || english.isEmpty()) return english;
        if (!RU.equals(active)) return english;
        String v = SETTING_TRANSLATIONS.get(english);
        return v != null ? v : english;
    }

    /* ============================================================ */
    /* Tables                                                        */
    /* ============================================================ */

    private static final Map<String, String> EN_TABLE = new HashMap<>();
    private static final Map<String, String> RU_TABLE = new HashMap<>();

    static {
        // Modal: SHARE
        en("modal.share.title", "Create Key");
        en("modal.share.subtitle.prefix", "Config");
        en("modal.share.placeholder", "Number of uses");
        en("modal.share.primary", "Create");
        en("modal.cloud.title", "Cloud Configs");
        en("modal.cloud.subtitle", "Temporarily unavailable");

        // Modal: RENAME
        en("modal.rename.title", "Rename Config");
        en("modal.rename.placeholder", "New name");
        en("modal.rename.primary", "Save");

        // Modal: IMPORT
        en("modal.import.title", "Load Config from Key");
        en("modal.import.subtitle", "Enter the code from your friend");
        en("modal.import.placeholder", "nomad-9wxf-49k7");
        en("modal.import.primary", "Load");

        // Buttons / common
        en("button.cancel", "Cancel");
        en("status.loading", "Loading...");
        en("status.server_unreachable", "Server unreachable");
        en("status.error_prefix", "Error");
        en("status.copy_failed", "Failed to pack config");
        en("status.copied_prefix", "Code");
        en("status.copied_suffix", "copied");
        en("status.cloud_disabled_short", "Unavailable");
        en("status.cloud_disabled_detail", "This is not a bug. It is intentionally disabled.");
        en("status.import_failed", "Import failed");
        en("status.imported_prefix", "Imported:");
        en("status.key_not_found", "Key not found");
        en("status.key_exhausted", "Key exhausted");
        en("status.empty_response", "Empty server response");
        en("status.enter_code", "Enter code");
        en("status.invalid_code_format", "Invalid code format");
        en("status.config_name_missing", "Config name not set");
        en("status.enter_new_name", "Enter new name");
        en("status.name_taken", "Name already in use");
        en("status.rename_failed", "Rename failed");
        en("status.rename_error", "Rename error");

        // Configs view kebab popup
        en("kebab.share", "Share");
        en("kebab.rename", "Rename");
        en("kebab.delete", "Delete");

        // Sidebar / general
        en("sidebar.new_config", "NEW CONFIG");
        en("sidebar.edit_hud", "EDIT HUD");

        // ---- RU translations -----------------------------------
        ru("modal.cloud.title", "Облачные конфиги");
        ru("modal.cloud.subtitle", "Временно недоступно");
        ru("modal.share.title", "Создание ключа");
        ru("modal.share.subtitle.prefix", "Конфиг");
        ru("modal.share.placeholder", "Количество использований");
        ru("modal.share.primary", "Создать");

        ru("modal.rename.title", "Переименование конфига");
        ru("modal.rename.placeholder", "Новое имя");
        ru("modal.rename.primary", "Сохранить");

        ru("modal.import.title", "Загрузить конфиг из ключа");
        ru("modal.import.subtitle", "Введите код, полученный от друга");
        ru("modal.import.placeholder", "nomad-9wxf-49k7");
        ru("modal.import.primary", "Загрузить");

        ru("button.cancel", "Отмена");
        ru("status.loading", "Загрузка...");
        ru("status.server_unreachable", "Сервер недоступен");
        ru("status.error_prefix", "Ошибка");
        ru("status.copy_failed", "Не удалось упаковать конфиг");
        ru("status.copied_prefix", "Код");
        ru("status.copied_suffix", "скопирован");
        ru("status.cloud_disabled_short", "Недоступно");
        ru("status.cloud_disabled_detail", "Это не баг. Это сделано специально.");
        ru("status.import_failed", "Не удалось импортировать");
        ru("status.imported_prefix", "Импортирован:");
        ru("status.key_not_found", "Ключ не найден");
        ru("status.key_exhausted", "Ключ исчерпан");
        ru("status.empty_response", "Пустой ответ сервера");
        ru("status.enter_code", "Введите код");
        ru("status.invalid_code_format", "Неверный формат кода");
        ru("status.config_name_missing", "Имя конфига не задано");
        ru("status.enter_new_name", "Введите новое имя");
        ru("status.name_taken", "Имя уже занято");
        ru("status.rename_failed", "Не удалось переименовать");
        ru("status.rename_error", "Ошибка переименования");

        ru("kebab.share", "Поделиться");
        ru("kebab.rename", "Переименовать");
        ru("kebab.delete", "Удалить");

        ru("sidebar.new_config", "НОВЫЙ КОНФИГ");
        ru("sidebar.edit_hud", "РЕДАКТ. HUD");
    }

    private static final Map<String, String> SETTING_TRANSLATIONS = new HashMap<>();
    static {
        SETTING_TRANSLATIONS.put("24 Hour Format", "24-часовой формат");
        SETTING_TRANSLATIONS.put("Abilities", "Способности");
        SETTING_TRANSLATIONS.put("Adds a stats panel to the death screen with killer / weapon / session counters", "Добавляет панель статистики на экран смерти: убийца, оружие, счётчик сессии");
        SETTING_TRANSLATIONS.put("Adds smooth UI animations: chat message fade-in, tab list slide-in/out", "Плавные анимации UI: затухание сообщений чата, выезд таб-листа");
        SETTING_TRANSLATIONS.put("Adds tier-coloured ping, version, and raw MOTD to the vanilla multiplayer server list", "Добавляет цветной пинг, версию и сырой MOTD в ванильный список серверов");
        SETTING_TRANSLATIONS.put("Advanced", "Расширенное");
        SETTING_TRANSLATIONS.put("Affect Clouds", "Влиять на облака");
        SETTING_TRANSLATIONS.put("All", "Все");
        SETTING_TRANSLATIONS.put("Always", "Всегда");
        SETTING_TRANSLATIONS.put("Always Show", "Всегда показывать");
        SETTING_TRANSLATIONS.put("Animals", "Животные");
        SETTING_TRANSLATIONS.put("Animates loaded chunks sliding smoothly into place from below or from a chosen direction", "Плавно выдвигает прогруженные чанки снизу или с выбранной стороны");
        SETTING_TRANSLATIONS.put("Animation", "Анимация");
        SETTING_TRANSLATIONS.put("Animation Speed", "Скорость анимации");
        SETTING_TRANSLATIONS.put("Animation Type", "Тип анимации");
        SETTING_TRANSLATIONS.put("Animations", "Анимации");
        SETTING_TRANSLATIONS.put("Anti AFK", "Анти-AFK");
        SETTING_TRANSLATIONS.put("Anti Caps", "Анти-капс");
        SETTING_TRANSLATIONS.put("API Key", "Ключ API");
        SETTING_TRANSLATIONS.put("Apify Actor", "Apify Actor");
        SETTING_TRANSLATIONS.put("Apify Token", "Apify токен");
        SETTING_TRANSLATIONS.put("Appearance", "Внешний вид");
        SETTING_TRANSLATIONS.put("Apply", "Применить");
        SETTING_TRANSLATIONS.put("Armor", "Броня");
        SETTING_TRANSLATIONS.put("Aspect Factor", "Множитель пропорций");
        SETTING_TRANSLATIONS.put("Aspect Preset", "Пресет пропорций");
        SETTING_TRANSLATIONS.put("Aspect Ratio", "Соотношение сторон");
        SETTING_TRANSLATIONS.put("Audio", "Звук");
        SETTING_TRANSLATIONS.put("Auto", "Авто");
        SETTING_TRANSLATIONS.put("Auto Eat", "Авто-еда");
        SETTING_TRANSLATIONS.put("Auto Respawn", "Авто-возрождение");
        SETTING_TRANSLATIONS.put("Auto Sprint", "Авто-спринт");
        SETTING_TRANSLATIONS.put("Auto Swap", "Авто-смена");
        SETTING_TRANSLATIONS.put("Auto Switch", "Авто-смена");
        SETTING_TRANSLATIONS.put("Auto Walk", "Авто-ходьба");
        SETTING_TRANSLATIONS.put("Auto-Detect Source", "Авто-определение источника");
        SETTING_TRANSLATIONS.put("Auto-translate incoming chat messages and post the translation as a new row below the original", "Авто-перевод входящих сообщений: перевод появляется отдельной строкой под оригиналом");
        SETTING_TRANSLATIONS.put("Automatically drinks potions when effects expire", "Автоматически пьёт зелья при истечении эффектов");
        SETTING_TRANSLATIONS.put("Automatically eats food (or runs /feed) when hunger drops below the threshold", "Автоматически ест еду (или /feed) при падении голода ниже порога");
        SETTING_TRANSLATIONS.put("Automatically executes /near max command", "Автоматически выполняет команду /near max");
        SETTING_TRANSLATIONS.put("Automatically reissues your auction items on FunTime", "Автоматически перевыставляет ваши лоты на FunTime");
        SETTING_TRANSLATIONS.put("Automatically respawns and optionally runs a command after death", "Автовозрождение и опциональная команда после смерти");
        SETTING_TRANSLATIONS.put("Automatically sprints while holding forward", "Автоматический спринт при движении вперёд");
        SETTING_TRANSLATIONS.put("Automatically taps shift for short duration", "Автоматически нажимает Shift на короткое время");
        SETTING_TRANSLATIONS.put("Background", "Фон");
        SETTING_TRANSLATIONS.put("Background Blur Radius", "Радиус размытия фона");
        SETTING_TRANSLATIONS.put("Background Opacity", "Прозрачность фона");
        SETTING_TRANSLATIONS.put("Background Preset", "Стиль фона");
        SETTING_TRANSLATIONS.put("Base Color", "Базовый цвет");
        SETTING_TRANSLATIONS.put("Batches all 2D HUD elements into a single FBO and refreshes at a throttled rate", "Объединяет 2D-элементы HUD в один FBO и ограничивает частоту обновления");
        SETTING_TRANSLATIONS.put("Better Death", "Улучшенная смерть");
        SETTING_TRANSLATIONS.put("Better F3", "Улучшенный F3");
        SETTING_TRANSLATIONS.put("Bind", "Бинд");
        // Per-slot Binds module labels - the module preallocates 8
        // slots numbered 1..8 and synthesises three settings per
        // slot ({@code "Bind N"} section, {@code "Bind N Key"} bind,
        // {@code "Bind N Message"} text). Adding the localized
        // entries here lets the existing Lang.translate dispatch
        // pick them up without touching the module's storage keys.
        for (int i = 1; i <= 8; i++) {
            SETTING_TRANSLATIONS.put("Bind " + i, "Бинд " + i);
            SETTING_TRANSLATIONS.put("Bind " + i + " Key", "Бинд " + i + " — клавиша");
            SETTING_TRANSLATIONS.put("Bind " + i + " Message", "Бинд " + i + " — сообщение");
        }
        SETTING_TRANSLATIONS.put("Binds", "Привязки");
        SETTING_TRANSLATIONS.put("Blacklist", "Чёрный список");
        SETTING_TRANSLATIONS.put("Block Reach", "Дальность блока");
        SETTING_TRANSLATIONS.put("Blur Radius", "Радиус размытия");
        SETTING_TRANSLATIONS.put("Bold", "Жирный");
        SETTING_TRANSLATIONS.put("Border", "Граница");
        SETTING_TRANSLATIONS.put("Border Color", "Цвет границы");
        SETTING_TRANSLATIONS.put("Box", "Куб");
        SETTING_TRANSLATIONS.put("Box Color", "Цвет рамки");
        SETTING_TRANSLATIONS.put("Bozhestvennaya Aura Name", "Имя «Божья аура»");
        SETTING_TRANSLATIONS.put("Break Block Particles", "Частицы ломки блоков");
        SETTING_TRANSLATIONS.put("Brightness", "Яркость");
        SETTING_TRANSLATIONS.put("Camera", "Камера");
        SETTING_TRANSLATIONS.put("Cancel", "Отмена");
        SETTING_TRANSLATIONS.put("Change Color When Looking on Entity", "Менять цвет при наведении на сущность");
        SETTING_TRANSLATIONS.put("Change the time of day in the world (client-side, works on any server)", "Меняет время суток (клиентски, работает на любом сервере)");
        SETTING_TRANSLATIONS.put("Change the weather in the world (client-side, works on any server)", "Меняет погоду в мире (клиентски, работает на любом сервере)");
        SETTING_TRANSLATIONS.put("Changes the visual properties of your hands - position, scale, and active side", "Меняет вид рук: позицию, масштаб и активную сторону");
        SETTING_TRANSLATIONS.put("Chat", "Чат");
        SETTING_TRANSLATIONS.put("Chat Fade", "Затухание чата");
        SETTING_TRANSLATIONS.put("Chat Height", "Высота чата");
        SETTING_TRANSLATIONS.put("Chat History", "История чата");
        SETTING_TRANSLATIONS.put("Chat Notify", "Уведомление в чат");
        SETTING_TRANSLATIONS.put("Chat Opacity", "Прозрачность чата");
        SETTING_TRANSLATIONS.put("Chat Width", "Ширина чата");
        SETTING_TRANSLATIONS.put("Cinematic Camera", "Кинематик-камера");
        SETTING_TRANSLATIONS.put("Cinematic-style camera smoothing for mouse movement", "Кинематографическое сглаживание камеры мыши");
        SETTING_TRANSLATIONS.put("Circle", "Круг");
        SETTING_TRANSLATIONS.put("Circle Color", "Цвет круга");
        SETTING_TRANSLATIONS.put("Circle Glow", "Свечение круга");
        SETTING_TRANSLATIONS.put("Circle Segments", "Сегменты круга");
        SETTING_TRANSLATIONS.put("Circle Thickness", "Толщина круга");
        SETTING_TRANSLATIONS.put("Classic", "Классический");
        SETTING_TRANSLATIONS.put("Click", "Клик");
        SETTING_TRANSLATIONS.put("Click Delay", "Задержка клика");
        SETTING_TRANSLATIONS.put("Click Type", "Тип клика");
        SETTING_TRANSLATIONS.put("Close", "Закрыть");
        SETTING_TRANSLATIONS.put("Cloud Brightness", "Яркость облаков");
        SETTING_TRANSLATIONS.put("Cloud Height", "Высота облаков");
        SETTING_TRANSLATIONS.put("Clouds", "Облака");
        SETTING_TRANSLATIONS.put("Collapse Repeats", "Сворачивать повторы");
        SETTING_TRANSLATIONS.put("Color", "Цвет");
        SETTING_TRANSLATIONS.put("Color By Shulker", "Цвет по шалкеру");
        SETTING_TRANSLATIONS.put("Color Based On Usage", "Цвет от использований");
        SETTING_TRANSLATIONS.put("Color Brightness", "Яркость цвета");
        SETTING_TRANSLATIONS.put("Color By Cooldown", "Цвет по перезарядке");
        SETTING_TRANSLATIONS.put("Color By Durability", "Цвет по прочности");
        SETTING_TRANSLATIONS.put("Color By HP", "Цвет по HP");
        SETTING_TRANSLATIONS.put("Color By TPS", "Цвет по TPS");
        SETTING_TRANSLATIONS.put("Color By Type", "Цвет по типу");
        SETTING_TRANSLATIONS.put("Color Coding", "Цветовая разметка");
        SETTING_TRANSLATIONS.put("Color Correction", "Цветокоррекция");
        SETTING_TRANSLATIONS.put("Color correction post-process effect", "Пост-эффект цветокоррекции");
        SETTING_TRANSLATIONS.put("Color Message", "Цветное сообщение");
        SETTING_TRANSLATIONS.put("Color Phase", "Фаза цвета");
        SETTING_TRANSLATIONS.put("Color Ping", "Цветной пинг");
        SETTING_TRANSLATIONS.put("Color Preset", "Пресет цветов");
        SETTING_TRANSLATIONS.put("Color Settings", "Настройки цвета");
        SETTING_TRANSLATIONS.put("Color When In Range", "Цвет в радиусе");
        SETTING_TRANSLATIONS.put("Colors", "Цвета");
        SETTING_TRANSLATIONS.put("Command", "Команда");
        SETTING_TRANSLATIONS.put("Compact", "Компактный");
        SETTING_TRANSLATIONS.put("Compact custom F3 debug overlay with color-coded values and FPS bar", "Компактный F3-оверлей с цветными значениями и графиком FPS");
        SETTING_TRANSLATIONS.put("Compact Labels", "Компактные подписи");
        SETTING_TRANSLATIONS.put("Compact Mode", "Компактный режим");
        SETTING_TRANSLATIONS.put("Compact Rows", "Компактные строки");
        SETTING_TRANSLATIONS.put("Configuration", "Конфигурация");
        SETTING_TRANSLATIONS.put("Contrast", "Контраст");
        SETTING_TRANSLATIONS.put("Cooldown", "Перезарядка");
        SETTING_TRANSLATIONS.put("Cooldown HUD", "HUD перезарядки");
        SETTING_TRANSLATIONS.put("Coordinates HUD", "HUD координат");
        SETTING_TRANSLATIONS.put("Counts combo hits until enemy hits back", "Считает комбо-удары до удара противника");
        SETTING_TRANSLATIONS.put("Counts consumable items in your inventory and shows them as a draggable icon grid", "Считает расходники в инвентаре и показывает их сеткой иконок");
        SETTING_TRANSLATIONS.put("CPS HUD", "CPS HUD");
        SETTING_TRANSLATIONS.put("Crosshair", "Прицел");
        SETTING_TRANSLATIONS.put("Crosshair Color", "Цвет прицела");
        SETTING_TRANSLATIONS.put("Crosshair Size", "Размер прицела");
        SETTING_TRANSLATIONS.put("Crosshair Style", "Стиль прицела");
        SETTING_TRANSLATIONS.put("Crosshair Type", "Тип прицела");
        SETTING_TRANSLATIONS.put("Cursor", "Курсор");
        SETTING_TRANSLATIONS.put("Custom", "Кастомный");
        SETTING_TRANSLATIONS.put("Custom Hit Color", "Свой цвет удара");
        SETTING_TRANSLATIONS.put("Custom hit color for entities", "Свой цвет вспышки удара по сущностям");
        SETTING_TRANSLATIONS.put("Custom fog with adjustable distance, density and color", "Свой туман с настраиваемой дальностью, плотностью и цветом");
        SETTING_TRANSLATIONS.put("Density", "Плотность");
        SETTING_TRANSLATIONS.put("Color Mode", "Режим цвета");
        SETTING_TRANSLATIONS.put("Fog Color", "Цвет тумана");
        SETTING_TRANSLATIONS.put("Affect Sky Fog", "Влиять на туман неба");
        SETTING_TRANSLATIONS.put("Theme", "Тема");
        SETTING_TRANSLATIONS.put("Scope", "Область");
        SETTING_TRANSLATIONS.put("Distance in blocks at which the fog reaches full opacity", "Дальность в блоках, при которой туман достигает полной непрозрачности");
        SETTING_TRANSLATIONS.put("How much of the distance range is fogged. 0 = thin band at the far plane, 1 = solid wall starting at the camera", "Какая часть диапазона дальности туманит. 0 = тонкая полоса на дальнем плане, 1 = сплошная стена от камеры");
        SETTING_TRANSLATIONS.put("Custom uses the picker below, Theme follows the active GUI palette", "Custom использует пикер ниже, Theme следует активной палитре интерфейса");
        SETTING_TRANSLATIONS.put("Custom fog colour. Ignored when Color Mode is set to Theme", "Свой цвет тумана. Игнорируется, если Color Mode = Theme");
        SETTING_TRANSLATIONS.put("Final fog alpha multiplier. 1 = fully opaque, 0 = invisible", "Множитель альфы тумана. 1 = непрозрачный, 0 = невидимый");
        SETTING_TRANSLATIONS.put("Apply the override to the sky-dome fog pass too. Disable to keep the horizon clear and only fog terrain.", "Также применять к туману неба. Выключи, чтобы горизонт оставался чистым, а туман был только на ландшафте.");
        SETTING_TRANSLATIONS.put("Smooth Skies", "Гладкое небо");
        SETTING_TRANSLATIONS.put("Smooths the transition between fog and sky so lower fog opacity doesn't produce a hard horizon line. Active only when Custom Fog is on.", "Сглаживает переход между туманом и небом, чтобы при низкой прозрачности тумана не было резкой линии на горизонте. Работает только при включённом Custom Fog.");
        SETTING_TRANSLATIONS.put("Fix Skybox Clipping", "Исправить клиппинг скайбокса");
        SETTING_TRANSLATIONS.put("Pushes the far plane out so a low render-distance skybox doesn't clip into the fog band. Inspired by SmoothSkies (MIT).", "Отодвигает дальнюю плоскость, чтобы скайбокс на низкой дальности прорисовки не клиппился в туман. Вдохновлено SmoothSkies (MIT).");
        SETTING_TRANSLATIONS.put("Lower Void Darkness", "Снизить темноту бездны");
        SETTING_TRANSLATIONS.put("Stops the fog from going pitch-black when the camera is below world height. Inspired by SmoothSkies (MIT).", "Не даёт туману уходить в кромешную темноту, когда камера ниже мира. Вдохновлено SmoothSkies (MIT).");
        SETTING_TRANSLATIONS.put("Custom Mentions", "Кастомные упоминания");
        SETTING_TRANSLATIONS.put("Custom sky and cloud tint with day/night gradient and sunset boost", "Своя окраска неба и облаков с дневным/ночным градиентом и усилением заката");
        SETTING_TRANSLATIONS.put("Custom themes for GUI", "Свои темы для GUI");
        SETTING_TRANSLATIONS.put("Customize entity hitbox color", "Свой цвет хитбокса сущностей");
        SETTING_TRANSLATIONS.put("Damage", "Урон");
        SETTING_TRANSLATIONS.put("Day Phase", "Фаза дня");
        SETTING_TRANSLATIONS.put("Death Stats", "Статистика смертей");
        SETTING_TRANSLATIONS.put("Default", "По умолчанию");
        SETTING_TRANSLATIONS.put("Default Zoom", "Зум по умолчанию");
        SETTING_TRANSLATIONS.put("Delay", "Задержка");
        SETTING_TRANSLATIONS.put("Delay (ms)", "Задержка (мс)");
        SETTING_TRANSLATIONS.put("Description", "Описание");
        SETTING_TRANSLATIONS.put("Details", "Детали");
        SETTING_TRANSLATIONS.put("Dezorientation Name", "Имя «Дезориентация»");
        SETTING_TRANSLATIONS.put("Direction", "Направление");
        SETTING_TRANSLATIONS.put("Disabled", "Выключен");
        SETTING_TRANSLATIONS.put("Discord Rich Presence: customisable Playing-on-server card with templated lines", "Discord Rich Presence: настраиваемая карточка с шаблонными строками");
        SETTING_TRANSLATIONS.put("Display", "Отображение");
        SETTING_TRANSLATIONS.put("Display Mode", "Режим отображения");
        SETTING_TRANSLATIONS.put("Display Option", "Вариант отображения");
        SETTING_TRANSLATIONS.put("Display Ping Number", "Показывать число пинга");
        SETTING_TRANSLATIONS.put("Display Server Icon", "Показывать иконку сервера");
        SETTING_TRANSLATIONS.put("Distance", "Расстояние");
        SETTING_TRANSLATIONS.put("Drakon Trapka Name", "Имя «Драконья трапка»");
        SETTING_TRANSLATIONS.put("Draws a configurable PvP-reach circle around entities (port of uku3lig/hitrange, MIT)", "Рисует настраиваемый круг pvp-реча вокруг сущностей (порт uku3lig/hitrange, MIT)");
        SETTING_TRANSLATIONS.put("Durability Mode", "Режим прочности");
        SETTING_TRANSLATIONS.put("Durability Threshold", "Порог прочности");
        SETTING_TRANSLATIONS.put("Duration", "Длительность");
        SETTING_TRANSLATIONS.put("Dynamic", "Динамический");
        SETTING_TRANSLATIONS.put("Dynamic Cursor", "Динамический курсор");
        SETTING_TRANSLATIONS.put("Dynamic Ping Color", "Динамический цвет пинга");
        SETTING_TRANSLATIONS.put("Easing", "Сглаживание");
        SETTING_TRANSLATIONS.put("Effect Duration", "Длительность эффекта");
        SETTING_TRANSLATIONS.put("Effects", "Эффекты");
        SETTING_TRANSLATIONS.put("Elapsed Time", "Прошедшее время");
        SETTING_TRANSLATIONS.put("Enable Limits", "Включить лимиты");
        SETTING_TRANSLATIONS.put("Enabled", "Включён");
        SETTING_TRANSLATIONS.put("Enchanted Gapple Cooldown", "Кд зачар. золотого яблока");
        SETTING_TRANSLATIONS.put("Estimates server TPS based on world-time advancement", "Оценивает серверный TPS по продвижению мирового времени");
        SETTING_TRANSLATIONS.put("Expand Slang", "Расшифровка сленга");
        SETTING_TRANSLATIONS.put("Extra Triggers", "Доп. триггеры");
        SETTING_TRANSLATIONS.put("F5 Animation Speed", "Скорость анимации F5");
        SETTING_TRANSLATIONS.put("F5 Interpolation", "Интерполяция F5");
        SETTING_TRANSLATIONS.put("Fade Distance", "Дистанция затухания");
        SETTING_TRANSLATIONS.put("Fake FPS", "Фейк FPS");
        SETTING_TRANSLATIONS.put("Fast Exp", "Быстрый опыт");
        SETTING_TRANSLATIONS.put("Fill Color", "Цвет заливки");
        SETTING_TRANSLATIONS.put("Filled", "Заполнено");
        SETTING_TRANSLATIONS.put("Filter", "Фильтр");
        SETTING_TRANSLATIONS.put("Fire", "Огонь");
        SETTING_TRANSLATIONS.put("Flash On Expiry", "Мигание при истечении");
        SETTING_TRANSLATIONS.put("Food Particles", "Частицы еды");
        SETTING_TRANSLATIONS.put("Forces maximum lightmap brightness so caves and night render at near-day levels", "Принудительно ставит макс яркость, чтобы пещеры и ночь рендерились почти как днём");
        SETTING_TRANSLATIONS.put("Format", "Формат");
        SETTING_TRANSLATIONS.put("FOV", "FOV (поле зрения)");
        SETTING_TRANSLATIONS.put("FPS HUD", "FPS HUD");
        SETTING_TRANSLATIONS.put("Free Look", "Свободный взгляд");
        SETTING_TRANSLATIONS.put("FunTime ability detector and visualizer toggles", "Детектор и визуализатор способностей FunTime");
        SETTING_TRANSLATIONS.put("Gamma", "Гамма");
        SETTING_TRANSLATIONS.put("Gapple Saturation", "Сытость от голд. яблока");
        SETTING_TRANSLATIONS.put("General", "Общее");
        SETTING_TRANSLATIONS.put("Glow", "Свечение");
        SETTING_TRANSLATIONS.put("Glow Strength", "Сила свечения");
        SETTING_TRANSLATIONS.put("Glowing", "Свечение");
        SETTING_TRANSLATIONS.put("Gradient", "Градиент");
        SETTING_TRANSLATIONS.put("Hand", "Рука");
        SETTING_TRANSLATIONS.put("Hand Depth Threshold", "Порог глубины руки");
        SETTING_TRANSLATIONS.put("Hand Sway", "Покачивание руки");
        SETTING_TRANSLATIONS.put("Hand Tweaks", "Настройки руки");
        SETTING_TRANSLATIONS.put("Heal", "Восстановление");
        SETTING_TRANSLATIONS.put("Healing Helper", "Помощник лечения");
        SETTING_TRANSLATIONS.put("Healing Potion HP", "HP для зелья лечения");
        SETTING_TRANSLATIONS.put("Health", "Здоровье");
        SETTING_TRANSLATIONS.put("Height", "Высота");
        SETTING_TRANSLATIONS.put("Held Item", "Предмет в руке");
        SETTING_TRANSLATIONS.put("Hidden", "Скрытый");
        SETTING_TRANSLATIONS.put("Hide Coordinates", "Скрыть координаты");
        SETTING_TRANSLATIONS.put("Hide HUD", "Скрыть HUD");
        SETTING_TRANSLATIONS.put("Hide in F1", "Скрывать в F1");
        SETTING_TRANSLATIONS.put("Hide In Menus", "Скрывать в меню");
        SETTING_TRANSLATIONS.put("Hide Nicknames", "Скрыть никнеймы");
        SETTING_TRANSLATIONS.put("Hide Passwords", "Скрыть пароли");
        SETTING_TRANSLATIONS.put("Hide Player Names", "Скрыть имена игроков");
        SETTING_TRANSLATIONS.put("Hide Tab List Names", "Скрыть имена в табе");
        SETTING_TRANSLATIONS.put("Hide When Looking At", "Скрывать при взгляде");
        SETTING_TRANSLATIONS.put("Hide While Sneaking", "Скрывать при приседании");
        SETTING_TRANSLATIONS.put("Hides sensitive info while streaming: coordinates and passwords", "Скрывает чувствительную инфу на стриме: координаты и пароли");
        SETTING_TRANSLATIONS.put("Highlight", "Подсветка");
        SETTING_TRANSLATIONS.put("Highlight Background", "Фон подсветки");
        SETTING_TRANSLATIONS.put("Highlight Color", "Цвет подсветки");
        SETTING_TRANSLATIONS.put("Highlight Own Name", "Подсвечивать свой ник");
        SETTING_TRANSLATIONS.put("Highlights mentions of your name in chat and plays a ping sound", "Подсвечивает упоминания вашего ника в чате и играет звук");
        SETTING_TRANSLATIONS.put("History Limit", "Лимит истории");
        SETTING_TRANSLATIONS.put("Hit Color", "Цвет удара");
        SETTING_TRANSLATIONS.put("Hit Particles", "Частицы удара");
        SETTING_TRANSLATIONS.put("Hit Range", "Зона удара");
        SETTING_TRANSLATIONS.put("Hit Sound", "Звук удара");
        SETTING_TRANSLATIONS.put("Hitbox Color", "Цвет хитбокса");
        SETTING_TRANSLATIONS.put("Hold", "Удерживать");
        SETTING_TRANSLATIONS.put("Hold Shift + drag the mouse over slots to shift-click them all without extra clicks", "Зажмите Shift и протащите мышь по слотам — все будут shift-кликнуты без лишних кликов");
        SETTING_TRANSLATIONS.put("Hotbar", "Хотбар");
        SETTING_TRANSLATIONS.put("Hotbar Flush", "Очистка хотбара");
        SETTING_TRANSLATIONS.put("Hotbar Rollover", "Перенос хотбара");
        SETTING_TRANSLATIONS.put("Hotbar Slide", "Анимация хотбара");
        SETTING_TRANSLATIONS.put("Hotbar Slide Speed", "Скорость прокрутки хотбара");
        SETTING_TRANSLATIONS.put("HP Prefix", "Префикс HP");
        SETTING_TRANSLATIONS.put("HUD Length", "Длина HUD");
        SETTING_TRANSLATIONS.put("Hunger", "Голод");
        SETTING_TRANSLATIONS.put("Hunger Threshold", "Порог голода");
        SETTING_TRANSLATIONS.put("Impact Marker", "Маркер попадания");
        SETTING_TRANSLATIONS.put("Imported from cloud", "Импортировано из облака");
        SETTING_TRANSLATIONS.put("In Range Color", "Цвет в радиусе");
        SETTING_TRANSLATIONS.put("Info", "Инфо");
        SETTING_TRANSLATIONS.put("Info Display", "Информация");
        SETTING_TRANSLATIONS.put("Intensity", "Интенсивность");
        SETTING_TRANSLATIONS.put("Interval", "Интервал");
        SETTING_TRANSLATIONS.put("Inventory", "Инвентарь");
        SETTING_TRANSLATIONS.put("Inventory HUD", "HUD инвентаря");
        SETTING_TRANSLATIONS.put("Inverted", "Инвертированный");
        SETTING_TRANSLATIONS.put("Invisibility", "Невидимость");
        SETTING_TRANSLATIONS.put("Item Counts", "Счётчики предметов");
        SETTING_TRANSLATIONS.put("Item Highlight", "Подсветка предметов");
        SETTING_TRANSLATIONS.put("Item Physics", "Физика предметов");
        SETTING_TRANSLATIONS.put("Item Pickup", "Подбор предметов");
        SETTING_TRANSLATIONS.put("Item Scroller", "Прокрутка предметов");
        SETTING_TRANSLATIONS.put("Item Types", "Типы предметов");
        SETTING_TRANSLATIONS.put("Items", "Предметы");
        SETTING_TRANSLATIONS.put("Keep the vanilla crosshair visible in third-person (F5) view", "Оставляет ванильный прицел видимым в виде от 3-го лица (F5)");
        SETTING_TRANSLATIONS.put("Key", "Клавиша");
        SETTING_TRANSLATIONS.put("Keybind", "Бинд");
        SETTING_TRANSLATIONS.put("Language", "Язык");
        SETTING_TRANSLATIONS.put("Last modified", "Изменён");
        // ---- Theme names ----
        // Translated as a friendly Russian phrase rather than a
        // literal word-for-word so the picker reads naturally in
        // the same way the English originals do (e.g. "Mocha Gold"
        // is a coffee-and-gold mood, not "мокка золото"). Each
        // entry is also a fixed key in {@code MenuPalettes.byName}
        // - storage stays English so configs save / load on either
        // locale, only the displayed label changes.
        SETTING_TRANSLATIONS.put("Lunar Blue", "Синий Лунар");
        SETTING_TRANSLATIONS.put("Mocha Gold", "Мокка с золотом");
        SETTING_TRANSLATIONS.put("Rose Quartz", "Розовый кварц");
        SETTING_TRANSLATIONS.put("Emerald Frost", "Изумрудный иней");
        SETTING_TRANSLATIONS.put("Arctic Mint", "Арктическая мята");
        SETTING_TRANSLATIONS.put("Crimson Silk", "Алый шёлк");
        SETTING_TRANSLATIONS.put("Solar Ember", "Солнечный жар");
        SETTING_TRANSLATIONS.put("Midnight Bloom", "Полуночный цвет");
        SETTING_TRANSLATIONS.put("Desert Mirage", "Пустынный мираж");
        SETTING_TRANSLATIONS.put("Sapphire Steel", "Сапфировая сталь");
        SETTING_TRANSLATIONS.put("Velvet Plum", "Бархатная слива");
        SETTING_TRANSLATIONS.put("Frosted Peach", "Морозный персик");
        SETTING_TRANSLATIONS.put("Moss Smoke", "Мшистый дым");
        SETTING_TRANSLATIONS.put("Polar Night", "Полярная ночь");
        SETTING_TRANSLATIONS.put("Snow", "Снег");
        SETTING_TRANSLATIONS.put("Obsidian", "Обсидиан");
        SETTING_TRANSLATIONS.put("Nebula", "Туманность");
        SETTING_TRANSLATIONS.put("Coral", "Коралл");
        SETTING_TRANSLATIONS.put("Jade", "Нефрит");
        SETTING_TRANSLATIONS.put("Sunset", "Закат");
        SETTING_TRANSLATIONS.put("Violet", "Фиолетовый");
        SETTING_TRANSLATIONS.put("Ocean", "Океан");
        SETTING_TRANSLATIONS.put("Layout", "Расположение");
        // ---- Select-value lexicon ----
        // Centralised pool of localised choice labels used by every
        // SelectSetting in the project. Stored here (instead of next
        // to each module) so the wording can be tuned in one place;
        // storage keys remain English so saved configs are
        // language-agnostic and any
        // {@code .getSelected().equals("Linear")}-style logic in
        // module code keeps working untouched.
        SETTING_TRANSLATIONS.put("Linear", "Линейная");
        SETTING_TRANSLATIONS.put("Fast", "Быстрая");
        SETTING_TRANSLATIONS.put("Balanced", "Сбалансированная");
        SETTING_TRANSLATIONS.put("Back", "Возврат");
        SETTING_TRANSLATIONS.put("Overshoot", "Перелёт");
        SETTING_TRANSLATIONS.put("Elastic", "Упругая");
        SETTING_TRANSLATIONS.put("Bounce", "Отскок");
        SETTING_TRANSLATIONS.put("Ease Out", "Замедление");
        SETTING_TRANSLATIONS.put("Spring", "Пружина");
        SETTING_TRANSLATIONS.put("Decelerate", "Торможение");
        SETTING_TRANSLATIONS.put("Slide", "Сдвиг");
        SETTING_TRANSLATIONS.put("Slide+Scale", "Сдвиг+Масштаб");
        SETTING_TRANSLATIONS.put("Up", "Вверх");
        SETTING_TRANSLATIONS.put("Left", "Влево");
        SETTING_TRANSLATIONS.put("North", "Север");
        SETTING_TRANSLATIONS.put("South", "Юг");
        SETTING_TRANSLATIONS.put("East", "Восток");
        SETTING_TRANSLATIONS.put("West", "Запад");
        SETTING_TRANSLATIONS.put("Top", "Сверху");
        SETTING_TRANSLATIONS.put("Bottom", "Снизу");
        SETTING_TRANSLATIONS.put("Side", "Сторона");
        SETTING_TRANSLATIONS.put("Outline", "Контур");
        SETTING_TRANSLATIONS.put("Filled", "Заливка");
        SETTING_TRANSLATIONS.put("Chat", "Чат");
        SETTING_TRANSLATIONS.put("Clipboard", "Буфер обмена");
        SETTING_TRANSLATIONS.put("Both", "Оба");
        SETTING_TRANSLATIONS.put("Long", "Длинный");
        SETTING_TRANSLATIONS.put("Short", "Короткий");
        SETTING_TRANSLATIONS.put("Session", "Сессия");
        SETTING_TRANSLATIONS.put("World", "Мир");
        SETTING_TRANSLATIONS.put("Off", "Выкл");
        SETTING_TRANSLATIONS.put("Custom", "Свой");
        SETTING_TRANSLATIONS.put("Theme", "Тема");
        SETTING_TRANSLATIONS.put("Vanilla", "Ванильный");
        SETTING_TRANSLATIONS.put("Line", "Линия");
        SETTING_TRANSLATIONS.put("Thick", "Толстая");
        SETTING_TRANSLATIONS.put("Sphere", "Сфера");
        SETTING_TRANSLATIONS.put("Circle", "Круг");
        SETTING_TRANSLATIONS.put("Ukrainian", "Украинский");
        SETTING_TRANSLATIONS.put("Belarusian", "Белорусский");
        SETTING_TRANSLATIONS.put("Spanish", "Испанский");
        SETTING_TRANSLATIONS.put("French", "Французский");
        SETTING_TRANSLATIONS.put("German", "Немецкий");
        SETTING_TRANSLATIONS.put("Italian", "Итальянский");
        SETTING_TRANSLATIONS.put("Portuguese", "Португальский");
        SETTING_TRANSLATIONS.put("Polish", "Польский");
        SETTING_TRANSLATIONS.put("Turkish", "Турецкий");
        SETTING_TRANSLATIONS.put("Chinese", "Китайский");
        SETTING_TRANSLATIONS.put("Japanese", "Японский");
        SETTING_TRANSLATIONS.put("Korean", "Корейский");
        SETTING_TRANSLATIONS.put("Arabic", "Арабский");
        SETTING_TRANSLATIONS.put("Hindi", "Хинди");
        SETTING_TRANSLATIONS.put("Vietnamese", "Вьетнамский");
        SETTING_TRANSLATIONS.put("Indonesian", "Индонезийский");
        // ArmorHud / RectHudModule durability mode
        SETTING_TRANSLATIONS.put("Units", "Единицы");
        SETTING_TRANSLATIONS.put("Percent", "Проценты");
        // Consumable.layout
        SETTING_TRANSLATIONS.put("Column", "Столбец");
        SETTING_TRANSLATIONS.put("Table", "Таблица");
        // InventoryHud.source
        SETTING_TRANSLATIONS.put("Main", "Основной");
        SETTING_TRANSLATIONS.put("Ender Chest", "Эндер-сундук");
        // MemoryHud.format
        SETTING_TRANSLATIONS.put("Percentage", "Проценты");
        SETTING_TRANSLATIONS.put("Megabytes", "Мегабайты");
        SETTING_TRANSLATIONS.put("Gigabytes", "Гигабайты");
        // MovementSpeedHud.precision
        SETTING_TRANSLATIONS.put("Nearest", "Ближайшее");
        SETTING_TRANSLATIONS.put("1 Decimal", "1 знак");
        SETTING_TRANSLATIONS.put("2 Decimals", "2 знака");
        SETTING_TRANSLATIONS.put("3 Decimals", "3 знака");
        // PlayerModelHud.mode
        SETTING_TRANSLATIONS.put("Follow Mouse", "Следовать за мышью");
        SETTING_TRANSLATIONS.put("Auto Rotate", "Авто-вращение");
        SETTING_TRANSLATIONS.put("Static", "Статично");
        // Saturation.mode
        SETTING_TRANSLATIONS.put("Yellow Bar", "Жёлтая полоса");
        SETTING_TRANSLATIONS.put("Second Hunger Bar", "Вторая полоса голода");
        // TrapTimer.mode
        SETTING_TRANSLATIONS.put("Normal", "Обычный");
        SETTING_TRANSLATIONS.put("Dragon", "Дракон");
        // WeatherChanger.weatherType
        SETTING_TRANSLATIONS.put("Clear", "Ясно");
        SETTING_TRANSLATIONS.put("Rain", "Дождь");
        SETTING_TRANSLATIONS.put("Thunder", "Гроза");
        // NoFluid.mode (Water/Lava already share lexicon needs)
        SETTING_TRANSLATIONS.put("Water", "Вода");
        SETTING_TRANSLATIONS.put("Lava", "Лава");
        // MouseClicker.hand
        SETTING_TRANSLATIONS.put("Right", "Правая");
        // Translator.provider
        SETTING_TRANSLATIONS.put("Google", "Google");
        SETTING_TRANSLATIONS.put("Apify", "Apify");
        // Predictions / FTHelper neutral preset
        SETTING_TRANSLATIONS.put("Black", "Чёрный");
        // Consumable.itemTypes (vanilla item names; localised for the
        // Russian audience - storage stays English so each entry maps
        // back to the canonical ItemRegistry id without ambiguity).
        SETTING_TRANSLATIONS.put("Snowball", "Снежок");
        SETTING_TRANSLATIONS.put("Egg", "Яйцо");
        SETTING_TRANSLATIONS.put("Wind Charge", "Заряд ветра");
        SETTING_TRANSLATIONS.put("Golden Apple", "Золотое яблоко");
        SETTING_TRANSLATIONS.put("Enchanted Golden Apple", "Зачарованное яблоко");
        SETTING_TRANSLATIONS.put("Arrow", "Стрела");
        SETTING_TRANSLATIONS.put("Spectral Arrow", "Призрачная стрела");
        SETTING_TRANSLATIONS.put("Tipped Arrow", "Стрела с эффектом");
        SETTING_TRANSLATIONS.put("Totem", "Тотем");
        SETTING_TRANSLATIONS.put("Chorus Fruit", "Плод хоруса");
        SETTING_TRANSLATIONS.put("Ender Pearl", "Жемчуг эндера");
        SETTING_TRANSLATIONS.put("Firework Rocket", "Фейерверк");
        SETTING_TRANSLATIONS.put("Experience Bottle", "Бутылочка опыта");
        // BetterF3.sections multiselect (sub-set of the F3 overlay).
        // {@code Coordinates}, {@code Biome}, {@code Light},
        // {@code Dimension}, {@code Server}, {@code Memory},
        // {@code Time}, {@code System} are the F3 overlay categories.
        SETTING_TRANSLATIONS.put("Coordinates", "Координаты");
        SETTING_TRANSLATIONS.put("Facing", "Направление");
        SETTING_TRANSLATIONS.put("Biome", "Биом");
        SETTING_TRANSLATIONS.put("Light", "Свет");
        SETTING_TRANSLATIONS.put("Dimension", "Измерение");
        SETTING_TRANSLATIONS.put("Server", "Сервер");
        SETTING_TRANSLATIONS.put("Memory", "Память");
        SETTING_TRANSLATIONS.put("System", "Система");
        SETTING_TRANSLATIONS.put("Left Slide Interpolation", "Интерполяция слайда влево");
        SETTING_TRANSLATIONS.put("Limits", "Лимиты");
        SETTING_TRANSLATIONS.put("Line", "Линия");
        SETTING_TRANSLATIONS.put("Line Width", "Ширина линии");
        SETTING_TRANSLATIONS.put("Lines", "Линии");
        SETTING_TRANSLATIONS.put("List Lines Per Scroll", "Строк за прокрутку");
        SETTING_TRANSLATIONS.put("List Scroll Speed", "Скорость прокрутки списка");
        SETTING_TRANSLATIONS.put("List Smooth Scroll", "Плавная прокрутка списка");
        SETTING_TRANSLATIONS.put("Lists", "Списки");
        SETTING_TRANSLATIONS.put("Local timer for the FunTime трапка / драконья трапка ability use cooldown", "Локальный таймер кд использования трапки / драконьей трапки FunTime");
        SETTING_TRANSLATIONS.put("Logs every picked-up item to the chat", "Пишет в чат каждый поднятый предмет");
        SETTING_TRANSLATIONS.put("Longer Chat History", "Расширенная история чата");
        SETTING_TRANSLATIONS.put("Look around without moving your player", "Свободный обзор без поворота персонажа");
        SETTING_TRANSLATIONS.put("Mace Indicator", "Индикатор булавы");
        SETTING_TRANSLATIONS.put("Main Hand", "Основная рука");
        SETTING_TRANSLATIONS.put("Main Scale", "Основной масштаб");
        SETTING_TRANSLATIONS.put("Main X", "Основная X");
        SETTING_TRANSLATIONS.put("Main Y", "Основная Y");
        SETTING_TRANSLATIONS.put("Main Z", "Основная Z");
        SETTING_TRANSLATIONS.put("Marker Size", "Размер маркера");
        SETTING_TRANSLATIONS.put("Marker Style", "Стиль маркера");
        SETTING_TRANSLATIONS.put("Match", "Совпадение");
        SETTING_TRANSLATIONS.put("Match Your Username", "Совпадение с вашим ником");
        SETTING_TRANSLATIONS.put("Max FPS", "Макс FPS");
        SETTING_TRANSLATIONS.put("Max Render Distance", "Макс дистанция прорисовки");
        SETTING_TRANSLATIONS.put("Max Search Distance", "Макс дистанция поиска");
        SETTING_TRANSLATIONS.put("Max Zoom", "Макс зум");
        SETTING_TRANSLATIONS.put("Maximum", "Максимум");
        SETTING_TRANSLATIONS.put("Mention Color", "Цвет упоминания");
        SETTING_TRANSLATIONS.put("Mention Highlight", "Подсветка упоминаний");
        SETTING_TRANSLATIONS.put("Mention Sound", "Звук упоминания");
        SETTING_TRANSLATIONS.put("Message", "Сообщение");
        SETTING_TRANSLATIONS.put("Message Animation", "Анимация сообщений");
        SETTING_TRANSLATIONS.put("Message Animation Speed", "Скорость анимации сообщений");
        SETTING_TRANSLATIONS.put("Message Animation Type", "Тип анимации сообщений");
        SETTING_TRANSLATIONS.put("Metrics", "Метрики");
        SETTING_TRANSLATIONS.put("Min FPS", "Мин FPS");
        SETTING_TRANSLATIONS.put("Min Length", "Мин длина");
        SETTING_TRANSLATIONS.put("Minimal", "Минимальный");
        SETTING_TRANSLATIONS.put("Minimum", "Минимум");
        SETTING_TRANSLATIONS.put("Misc", "Прочее");
        SETTING_TRANSLATIONS.put("Mobs", "Мобы");
        SETTING_TRANSLATIONS.put("Mode", "Режим");
        SETTING_TRANSLATIONS.put("Mode Type", "Тип режима");
        SETTING_TRANSLATIONS.put("Model Size", "Размер модели");
        SETTING_TRANSLATIONS.put("Modern", "Современный");
        SETTING_TRANSLATIONS.put("Modify vanilla scoreboard with blur, custom colors, position, and scale", "Меняет ваниль scoreboard: блюр, цвета, позиция и масштаб");
        SETTING_TRANSLATIONS.put("MOTD", "MOTD");
        SETTING_TRANSLATIONS.put("Motion Blur", "Размытие в движении");
        SETTING_TRANSLATIONS.put("Motion blur effect", "Эффект motion blur");
        SETTING_TRANSLATIONS.put("N keybinds that send a chat message or slash command", "N биндов, отправляющих сообщение в чат или команду");
        SETTING_TRANSLATIONS.put("Name Overrides", "Переопределение имён");
        SETTING_TRANSLATIONS.put("Names", "Имена");
        SETTING_TRANSLATIONS.put("Nametag", "Тег имени");
        SETTING_TRANSLATIONS.put("Nametag options HUD", "HUD настроек тегов имён");
        SETTING_TRANSLATIONS.put("Nametag Suffix", "Суффикс тега");
        SETTING_TRANSLATIONS.put("Nametag Text Shadow", "Тень текста тега");
        SETTING_TRANSLATIONS.put("Nearest Only", "Только ближайшее");
        SETTING_TRANSLATIONS.put("Never", "Никогда");
        SETTING_TRANSLATIONS.put("Night Color", "Цвет ночи");
        SETTING_TRANSLATIONS.put("No Bobbing", "Без покачивания при ходьбе");
        SETTING_TRANSLATIONS.put("No Glow", "Без свечения");
        SETTING_TRANSLATIONS.put("No Hand Sway", "Без покачивания руки");
        SETTING_TRANSLATIONS.put("No Sway", "Без покачивания");
        SETTING_TRANSLATIONS.put("No Swing", "Без замаха");
        SETTING_TRANSLATIONS.put("None", "Нет");
        SETTING_TRANSLATIONS.put("Notification", "Уведомление");
        SETTING_TRANSLATIONS.put("Notify", "Уведомлять");
        SETTING_TRANSLATIONS.put("Number of Repetitions", "Количество повторов");
        SETTING_TRANSLATIONS.put("Off Hand", "Вторая рука");
        SETTING_TRANSLATIONS.put("Off Scale", "Масштаб 2-й руки");
        SETTING_TRANSLATIONS.put("Off X", "X 2-й руки");
        SETTING_TRANSLATIONS.put("Off Y", "Y 2-й руки");
        SETTING_TRANSLATIONS.put("Off Z", "Z 2-й руки");
        SETTING_TRANSLATIONS.put("Offhand", "Вторая рука");
        SETTING_TRANSLATIONS.put("Ognennyi Smerch Name", "Имя «Огненный смерч»");
        SETTING_TRANSLATIONS.put("On Death", "При смерти");
        SETTING_TRANSLATIONS.put("On Hit", "При ударе");
        SETTING_TRANSLATIONS.put("Only Players", "Только игроки");
        SETTING_TRANSLATIONS.put("Only Rare", "Только редкие");
        SETTING_TRANSLATIONS.put("Only Use Ground Speed", "Только наземная скорость");
        SETTING_TRANSLATIONS.put("Opacity", "Прозрачность");
        SETTING_TRANSLATIONS.put("Open", "Открыть");
        SETTING_TRANSLATIONS.put("Other", "Другое");
        SETTING_TRANSLATIONS.put("Others", "Другие");
        SETTING_TRANSLATIONS.put("Outline", "Контур");
        SETTING_TRANSLATIONS.put("Outline Color", "Цвет обводки");
        SETTING_TRANSLATIONS.put("Outline Thickness", "Толщина обводки");
        SETTING_TRANSLATIONS.put("Outline Width", "Толщина контура");
        SETTING_TRANSLATIONS.put("Output", "Вывод");
        SETTING_TRANSLATIONS.put("Output Mode", "Режим вывода");
        SETTING_TRANSLATIONS.put("Override the world projection's aspect ratio with a preset or manual factor", "Переопределяет соотношение сторон проекции пресетом или вручную");
        SETTING_TRANSLATIONS.put("Padding", "Отступы");
        SETTING_TRANSLATIONS.put("Particle Color", "Цвет частиц");
        SETTING_TRANSLATIONS.put("Particle Density", "Плотность частиц");
        SETTING_TRANSLATIONS.put("Particle Size", "Размер частиц");
        SETTING_TRANSLATIONS.put("Particle Speed", "Скорость частиц");
        SETTING_TRANSLATIONS.put("Particle Types", "Типы частиц");
        SETTING_TRANSLATIONS.put("Particles", "Частицы");
        SETTING_TRANSLATIONS.put("Periodically attacks or uses item based on selected hand", "Периодически атакует или использует предмет в выбранной руке");
        SETTING_TRANSLATIONS.put("Ping HUD", "Пинг HUD");
        SETTING_TRANSLATIONS.put("Ping Number Shadow", "Тень числа пинга");
        SETTING_TRANSLATIONS.put("Pitch", "Тон");
        SETTING_TRANSLATIONS.put("Plast Name", "Имя «Пласт»");
        SETTING_TRANSLATIONS.put("Play Sound", "Воспроизводить звук");
        SETTING_TRANSLATIONS.put("Player Model HUD", "HUD модели игрока");
        SETTING_TRANSLATIONS.put("Player Reach", "Дальность игрока");
        SETTING_TRANSLATIONS.put("Players", "Игроки");
        SETTING_TRANSLATIONS.put("Plays a sound when any armor piece drops below a durability threshold", "Играет звук, когда часть брони опускается ниже порога прочности");
        SETTING_TRANSLATIONS.put("Potion Particles", "Частицы зелий");
        SETTING_TRANSLATIONS.put("Power", "Мощность");
        SETTING_TRANSLATIONS.put("Predict Held", "Предсказывать в руке");
        SETTING_TRANSLATIONS.put("Predictions", "Предсказания");
        SETTING_TRANSLATIONS.put("Prefix", "Префикс");
        SETTING_TRANSLATIONS.put("Press", "Нажать");
        SETTING_TRANSLATIONS.put("Prevent dropping items from chosen hotbar/offhand slots", "Запрещает выкидывать предметы из выбранных слотов хотбара/второй руки");
        SETTING_TRANSLATIONS.put("Privacy", "Приватность");
        SETTING_TRANSLATIONS.put("Projectile Trail", "След снаряда");
        SETTING_TRANSLATIONS.put("Projects the predicted impact path of throwables in your hands and your own projectiles in flight", "Рисует прогноз траектории метаемых предметов в руках и ваших снарядов в полёте");
        SETTING_TRANSLATIONS.put("Provider", "Провайдер");
        SETTING_TRANSLATIONS.put("Pulses healing potions, golden apples, and enchanted gapples in your inventory based on HP, saturation, and a re-eat timer", "Пульсирует зельями лечения, голд. яблоками и зачар. яблоками в инвентаре по HP, сытости и таймеру");
        SETTING_TRANSLATIONS.put("Quality", "Качество");
        SETTING_TRANSLATIONS.put("Radius", "Радиус");
        SETTING_TRANSLATIONS.put("Rain Density", "Плотность дождя");
        SETTING_TRANSLATIONS.put("Rainbow", "Радуга");
        SETTING_TRANSLATIONS.put("Random Colors", "Случайные цвета");
        SETTING_TRANSLATIONS.put("Range", "Дальность");
        SETTING_TRANSLATIONS.put("Reach", "Дальность атаки");
        SETTING_TRANSLATIONS.put("Reach Color", "Цвет реча");
        SETTING_TRANSLATIONS.put("Reach Display", "Отображение дальности");
        SETTING_TRANSLATIONS.put("Reach in Blocks", "Дальность в блоках");
        SETTING_TRANSLATIONS.put("Realistic item physics", "Реалистичная физика предметов");
        SETTING_TRANSLATIONS.put("Recolor Match", "Перекрашивать совпадение");
        SETTING_TRANSLATIONS.put("Recolor the vanilla block outline at your crosshair, optionally fill the faces", "Перекрашивает ванильный контур блока под прицелом, опционально заливает грани");
        SETTING_TRANSLATIONS.put("Records your coords on death so you can return for your dropped items", "Запоминает координаты смерти, чтобы вернуться за выпавшим");
        SETTING_TRANSLATIONS.put("Refresh Rate", "Частота обновления");
        SETTING_TRANSLATIONS.put("Refresh Rate Scaling", "Масштаб частоты обновления");
        SETTING_TRANSLATIONS.put("Removes the fog and screen overlay applied while the camera is submerged in water or lava", "Убирает туман и оверлей при погружении камеры в воду или лаву");
        SETTING_TRANSLATIONS.put("Removes the right-click delay when throwing experience bottles", "Убирает задержку ПКМ при бросании бутылок опыта");
        SETTING_TRANSLATIONS.put("Render Mode", "Режим рендера");
        SETTING_TRANSLATIONS.put("Render-pipeline tweaks: hide glowing outlines, fire overlay, particles", "Твики рендера: скрыть свечение, оверлей огня, частицы");
        SETTING_TRANSLATIONS.put("Renders a 3D mini-version of your skin in the corner", "Рендерит 3D-мини-версию вашего скина в углу");
        SETTING_TRANSLATIONS.put("Renders your 27 storage slots or last-seen ender chest on the HUD", "Рендерит 27 слотов инвентаря или последний эндер-сундук на HUD");
        SETTING_TRANSLATIONS.put("English", "Английский");
        SETTING_TRANSLATIONS.put("Russian", "Русский");
        SETTING_TRANSLATIONS.put("Repeat", "Повторять");
        SETTING_TRANSLATIONS.put("Repeat Delay", "Задержка повтора");
        SETTING_TRANSLATIONS.put("Replace", "Замена");
        SETTING_TRANSLATIONS.put("Replace Message", "Заменять сообщение");
        SETTING_TRANSLATIONS.put("Replace Own Name Color", "Менять цвет своего ника");
        SETTING_TRANSLATIONS.put("Replacement", "Замена");
        SETTING_TRANSLATIONS.put("Replaces the visible FPS counter with a randomised value drawn from your configured range", "Заменяет видимый счётчик FPS случайным значением из заданного диапазона");
        SETTING_TRANSLATIONS.put("Replaces your own username in chat with a custom string", "Заменяет ваш ник в чате на свою строку");
        SETTING_TRANSLATIONS.put("Reset", "Сбросить");
        SETTING_TRANSLATIONS.put("Reset Cooldown", "Сбросить перезарядку");
        SETTING_TRANSLATIONS.put("Reset On Death", "Сброс при смерти");
        SETTING_TRANSLATIONS.put("Resume Zoom", "Продолжить зум");
        SETTING_TRANSLATIONS.put("Reverse Order", "Обратный порядок");
        SETTING_TRANSLATIONS.put("Right Click", "Правый клик");
        SETTING_TRANSLATIONS.put("Right Click CPS", "ПКМ CPS");
        SETTING_TRANSLATIONS.put("Rolling averages of reach, combo, damage, ping, and saturation", "Скользящие средние реча, комбо, урона, пинга и сытости");
        SETTING_TRANSLATIONS.put("Rotation Speed", "Скорость вращения");
        SETTING_TRANSLATIONS.put("Round To", "Округлять до");
        SETTING_TRANSLATIONS.put("Sample Size", "Размер выборки");
        SETTING_TRANSLATIONS.put("Saturation", "Насыщенность");
        SETTING_TRANSLATIONS.put("Save", "Сохранить");
        SETTING_TRANSLATIONS.put("Scale", "Масштаб");
        SETTING_TRANSLATIONS.put("Screencopy", "Снимок экрана");
        SETTING_TRANSLATIONS.put("Scroll Multiplier", "Множитель прокрутки");
        SETTING_TRANSLATIONS.put("Scroll Sensitivity", "Чувствительность прокрутки");
        SETTING_TRANSLATIONS.put("Sections", "Секции");
        SETTING_TRANSLATIONS.put("Self", "Себе");
        SETTING_TRANSLATIONS.put("Sends 'gg' or 'GG' in chat automatically after killing another player", "Автоматически отправляет «gg» или «GG» в чат после убийства игрока");
        SETTING_TRANSLATIONS.put("Sends /ah search <item in main hand> when the bind is pressed", "По биду отправляет /ah search <предмет в основной руке>");
        SETTING_TRANSLATIONS.put("Sensitivity", "Чувствительность");
        SETTING_TRANSLATIONS.put("Server Customizer", "Настройщик сервера");
        SETTING_TRANSLATIONS.put("Server List", "Список серверов");
        SETTING_TRANSLATIONS.put("Session", "Сессия");
        SETTING_TRANSLATIONS.put("Settings", "Настройки");
        SETTING_TRANSLATIONS.put("Shadow", "Тень");
        SETTING_TRANSLATIONS.put("Shadow Color", "Цвет тени");
        SETTING_TRANSLATIONS.put("Shape", "Форма");
        SETTING_TRANSLATIONS.put("Sharpness", "Резкость");
        SETTING_TRANSLATIONS.put("Show AM/PM", "Показывать AM/PM");
        SETTING_TRANSLATIONS.put("Show Axis Signs", "Показывать знаки осей");
        SETTING_TRANSLATIONS.put("Show Bind", "Показывать бинд");
        SETTING_TRANSLATIONS.put("Show Biome", "Показывать биом");
        SETTING_TRANSLATIONS.put("Show Bow", "Показывать лук");
        SETTING_TRANSLATIONS.put("Show Brackets", "Показывать скобки");
        SETTING_TRANSLATIONS.put("Show Break Time", "Показывать время ломки");
        SETTING_TRANSLATIONS.put("Show Chunk", "Показывать чанк");
        SETTING_TRANSLATIONS.put("Show Combo", "Показывать комбо");
        SETTING_TRANSLATIONS.put("Show Coordinates", "Показывать координаты");
        SETTING_TRANSLATIONS.put("Show Correct Tool", "Показывать нужный инструмент");
        SETTING_TRANSLATIONS.put("Show Count", "Показывать количество");
        SETTING_TRANSLATIONS.put("Show CPS Text", "Показывать текст CPS");
        SETTING_TRANSLATIONS.put("Show Current Zoom", "Показывать текущий зум");
        SETTING_TRANSLATIONS.put("Show Damage", "Показывать урон");
        SETTING_TRANSLATIONS.put("Show Damage In Armor", "Показывать урон по броне");
        SETTING_TRANSLATIONS.put("Show Day Phase", "Показывать фазу дня");
        SETTING_TRANSLATIONS.put("Show Death History", "Показывать историю смертей");
        SETTING_TRANSLATIONS.put("Show Decimals", "Показывать десятые");
        SETTING_TRANSLATIONS.put("Show Degree Number", "Показывать градусы");
        SETTING_TRANSLATIONS.put("Show Direction", "Показывать направление");
        SETTING_TRANSLATIONS.put("Show Effect Amplifier", "Показывать усилитель эффекта");
        SETTING_TRANSLATIONS.put("Show Effect Duration", "Показывать длительность эффекта");
        SETTING_TRANSLATIONS.put("Show Effects", "Показывать эффекты");
        SETTING_TRANSLATIONS.put("Show Entities", "Показывать сущности");
        SETTING_TRANSLATIONS.put("Show Eye Line", "Показывать линию взгляда");
        SETTING_TRANSLATIONS.put("Show FPS", "Показывать FPS");
        SETTING_TRANSLATIONS.put("Show Hand", "Показывать руку");
        SETTING_TRANSLATIONS.put("Show Health Bars", "Показывать полоски здоровья");
        SETTING_TRANSLATIONS.put("Show Held Item", "Показывать предмет в руке");
        SETTING_TRANSLATIONS.put("Show Icon", "Показывать иконку");
        SETTING_TRANSLATIONS.put("Show in chat", "Показывать в чате");
        SETTING_TRANSLATIONS.put("Show in Chat", "Показывать в чате");
        SETTING_TRANSLATIONS.put("Show in sprint hud", "Показывать в HUD спринта");
        SETTING_TRANSLATIONS.put("Show In Third Person", "Показывать от 3-го лица");
        SETTING_TRANSLATIONS.put("Show Intermediate", "Показывать промежуточные");
        SETTING_TRANSLATIONS.put("Show Inventory", "Показывать инвентарь");
        SETTING_TRANSLATIONS.put("Show Item", "Показывать предмет");
        SETTING_TRANSLATIONS.put("Show Killer", "Показывать убийцу");
        SETTING_TRANSLATIONS.put("Show Map", "Показывать карту");
        SETTING_TRANSLATIONS.put("Show Marker", "Показывать маркер");
        SETTING_TRANSLATIONS.put("Show Names", "Показывать имена");
        SETTING_TRANSLATIONS.put("Show Numbers", "Показывать числа");
        SETTING_TRANSLATIONS.put("Show On", "Показывать на");
        SETTING_TRANSLATIONS.put("Show Original On Hover", "Оригинал при наведении");
        SETTING_TRANSLATIONS.put("Show Ping", "Показывать пинг");
        SETTING_TRANSLATIONS.put("Show Ping Number", "Показывать число пинга");
        SETTING_TRANSLATIONS.put("Show Player Heads", "Показывать головы игроков");
        SETTING_TRANSLATIONS.put("Show Player Model", "Показывать модель игрока");
        SETTING_TRANSLATIONS.put("Show Raw MOTD", "Показывать сырой MOTD");
        SETTING_TRANSLATIONS.put("Show Reach", "Показывать реч");
        SETTING_TRANSLATIONS.put("Show Saturation", "Показывать насыщение");
        SETTING_TRANSLATIONS.put("Show Seconds", "Показывать секунды");
        SETTING_TRANSLATIONS.put("Show Self", "Показывать себя");
        SETTING_TRANSLATIONS.put("Show Server Name", "Показывать имя сервера");
        SETTING_TRANSLATIONS.put("Show Session Deaths", "Показывать смерти за сессию");
        SETTING_TRANSLATIONS.put("Show Title", "Показывать заголовок");
        SETTING_TRANSLATIONS.put("Show TPS", "Показывать TPS");
        SETTING_TRANSLATIONS.put("Show Trident", "Показывать трезубец");
        SETTING_TRANSLATIONS.put("Show Version", "Показывать версию");
        SETTING_TRANSLATIONS.put("Show X", "Показывать X");
        SETTING_TRANSLATIONS.put("Show Y", "Показывать Y");
        SETTING_TRANSLATIONS.put("Show Z", "Показывать Z");
        SETTING_TRANSLATIONS.put("Shows active potion effects on HUD with optional buff/debuff color coding", "Показывает активные эффекты зелий с опциональной цветовой разметкой бафф/дебафф");
        SETTING_TRANSLATIONS.put("Shows animated cardinal direction strip", "Показывает анимированную полосу сторон света");
        SETTING_TRANSLATIONS.put("Shows armor and durability on HUD", "Показывает броню и прочность в HUD");
        SETTING_TRANSLATIONS.put("Shows block/entity info when targeting", "Показывает инфо блока/сущности при наведении");
        SETTING_TRANSLATIONS.put("Shows current clicks per second on HUD", "Показывает текущий CPS в HUD");
        SETTING_TRANSLATIONS.put("Shows current FPS on HUD", "Показывает текущий FPS в HUD");
        SETTING_TRANSLATIONS.put("Shows current movement speed", "Показывает текущую скорость передвижения");
        SETTING_TRANSLATIONS.put("Shows current session duration", "Показывает длительность текущей сессии");
        SETTING_TRANSLATIONS.put("Shows current sprint, sneak, and flying state", "Показывает текущий спринт, сжатие Shift и полёт");
        SETTING_TRANSLATIONS.put("Shows current world day", "Показывает текущий день мира");
        SETTING_TRANSLATIONS.put("Shows hit distance on HUD", "Показывает дистанцию удара в HUD");
        SETTING_TRANSLATIONS.put("Shows movement and mouse buttons on HUD", "Показывает кнопки движения и мыши в HUD");
        SETTING_TRANSLATIONS.put("Shows position, chunk, biome, and direction", "Показывает позицию, чанк, биом и направление");
        SETTING_TRANSLATIONS.put("Shows RAM usage in HUD", "Показывает использование RAM в HUD");
        SETTING_TRANSLATIONS.put("Shows real local time, optionally with seconds and the in-game day phase", "Показывает реальное локальное время, опционально с секундами и фазой игрового дня");
        SETTING_TRANSLATIONS.put("Shows saturation bar above hunger bar", "Показывает полосу сытости над полосой голода");
        SETTING_TRANSLATIONS.put("Shows server IP address in HUD", "Показывает IP сервера в HUD");
        SETTING_TRANSLATIONS.put("Shows server ping on HUD", "Показывает пинг сервера в HUD");
        SETTING_TRANSLATIONS.put("Shows shulker box contents in a tooltip while hovering it in any inventory", "Показывает содержимое шалкеров в подсказке при наведении в любом инвентаре");
        SETTING_TRANSLATIONS.put("Shows the HP of the player you most recently hit (port of ZakoHealthIndicator)", "Показывает HP последнего ударенного игрока (порт ZakoHealthIndicator)");
        SETTING_TRANSLATIONS.put("Shows the remaining cooldown time as a number above each hotbar slot", "Показывает оставшийся кд числом над каждым слотом хотбара");
        SETTING_TRANSLATIONS.put("Side", "Сторона");
        SETTING_TRANSLATIONS.put("Size", "Размер");
        SETTING_TRANSLATIONS.put("Skip Nicknames", "Пропускать никнеймы");
        SETTING_TRANSLATIONS.put("Skip Same Language", "Пропускать тот же язык");
        SETTING_TRANSLATIONS.put("Sky", "Небо");
        SETTING_TRANSLATIONS.put("Sky Color", "Цвет неба");
        SETTING_TRANSLATIONS.put("Sky Customizer", "Настройщик неба");
        SETTING_TRANSLATIONS.put("Slot", "Слот");
        SETTING_TRANSLATIONS.put("Slot 1", "Слот 1");
        SETTING_TRANSLATIONS.put("Slot 2", "Слот 2");
        SETTING_TRANSLATIONS.put("Slot 3", "Слот 3");
        SETTING_TRANSLATIONS.put("Slot 4", "Слот 4");
        SETTING_TRANSLATIONS.put("Slot 5", "Слот 5");
        SETTING_TRANSLATIONS.put("Slot 6", "Слот 6");
        SETTING_TRANSLATIONS.put("Slot 7", "Слот 7");
        SETTING_TRANSLATIONS.put("Slot 8", "Слот 8");
        SETTING_TRANSLATIONS.put("Slot 9", "Слот 9");
        SETTING_TRANSLATIONS.put("Smooth", "Сглаживание");
        SETTING_TRANSLATIONS.put("Smooth Camera", "Плавная камера");
        SETTING_TRANSLATIONS.put("Smooth Camera (no F5)", "Плавная камера (без F5)");
        SETTING_TRANSLATIONS.put("Smooth Camera Speed", "Скорость плавной камеры");
        SETTING_TRANSLATIONS.put("Smooth F5", "Плавный F5");
        SETTING_TRANSLATIONS.put("Smooth Fade", "Плавное затухание");
        SETTING_TRANSLATIONS.put("Smooth Input Field", "Плавное поле ввода");
        SETTING_TRANSLATIONS.put("Smooth Speed", "Скорость сглаживания");
        SETTING_TRANSLATIONS.put("Smoothness", "Плавность");
        SETTING_TRANSLATIONS.put("Sneak Duration", "Длительность сжатия Shift");
        SETTING_TRANSLATIONS.put("Snezhok Zamorozka Name", "Имя «Снежок заморозки»");
        SETTING_TRANSLATIONS.put("Snow Density", "Плотность снега");
        SETTING_TRANSLATIONS.put("Snowball Tracker", "Отслеживание снежков");
        SETTING_TRANSLATIONS.put("Solid", "Сплошной");
        SETTING_TRANSLATIONS.put("Sound", "Звук");
        SETTING_TRANSLATIONS.put("Sound Volume", "Громкость");
        SETTING_TRANSLATIONS.put("Source", "Источник");
        SETTING_TRANSLATIONS.put("Source Language", "Исходный язык");
        SETTING_TRANSLATIONS.put("Speed", "Скорость");
        SETTING_TRANSLATIONS.put("Sphere Opacity", "Прозрачность сферы");
        SETTING_TRANSLATIONS.put("Sphere Y Offset", "Смещение сферы по Y");
        SETTING_TRANSLATIONS.put("Splash Potion Particles", "Частицы взрывных зелий");
        SETTING_TRANSLATIONS.put("Square", "Квадрат");
        SETTING_TRANSLATIONS.put("State", "Состояние");
        SETTING_TRANSLATIONS.put("Static", "Статичный");
        SETTING_TRANSLATIONS.put("Status Effects", "Эффекты состояния");
        SETTING_TRANSLATIONS.put("Step", "Шаг");
        SETTING_TRANSLATIONS.put("Streamer Mode", "Режим стримера");
        SETTING_TRANSLATIONS.put("Strength", "Сила");
        SETTING_TRANSLATIONS.put("Style", "Стиль");
        SETTING_TRANSLATIONS.put("Sunrise/Sunset Boost", "Усиление рассвета/заката");
        SETTING_TRANSLATIONS.put("Swap chestplate and elytra with a single keybind", "Меняет нагрудник и элитры одним бидом");
        SETTING_TRANSLATIONS.put("Swap Slot", "Слот замены");
        SETTING_TRANSLATIONS.put("Swap Type", "Тип замены");
        SETTING_TRANSLATIONS.put("Sphere -> Totem", "Сфера -> Тотем");
        SETTING_TRANSLATIONS.put("Sphere -> Talisman", "Сфера -> Талисман");
        SETTING_TRANSLATIONS.put("Talisman -> Totem", "Талисман -> Тотем");
        SETTING_TRANSLATIONS.put("Talisman -> Talisman", "Талисман -> Талисман");
        SETTING_TRANSLATIONS.put("Sphere -> Sphere", "Сфера -> Сфера");
        SETTING_TRANSLATIONS.put("Swaps items on button press", "Меняет предметы по нажатию кнопки");
        SETTING_TRANSLATIONS.put("Switch Side", "Сменить сторону");
        SETTING_TRANSLATIONS.put("Tab Animation", "Анимация таба");
        SETTING_TRANSLATIONS.put("Tab Animation Speed", "Скорость анимации таба");
        SETTING_TRANSLATIONS.put("Tab Close Interpolation", "Интерполяция закрытия таба");
        SETTING_TRANSLATIONS.put("Tab Colors", "Цвета таба");
        SETTING_TRANSLATIONS.put("Tab Fade", "Затухание таба");
        SETTING_TRANSLATIONS.put("Tab List", "Tab список");
        SETTING_TRANSLATIONS.put("Tab Open Interpolation", "Интерполяция открытия таба");
        SETTING_TRANSLATIONS.put("Tab overlay settings HUD", "HUD настроек оверлея таба");
        SETTING_TRANSLATIONS.put("Tab Slide", "Анимация таба");
        SETTING_TRANSLATIONS.put("Target Delay", "Задержка цели");
        SETTING_TRANSLATIONS.put("Target Language", "Целевой язык");
        SETTING_TRANSLATIONS.put("Targeting", "Цель");
        SETTING_TRANSLATIONS.put("Temperature", "Температура");
        SETTING_TRANSLATIONS.put("Text Color", "Цвет текста");
        SETTING_TRANSLATIONS.put("Text Shadow", "Тень текста");
        SETTING_TRANSLATIONS.put("Theme", "Тема");
        SETTING_TRANSLATIONS.put("Theme Color", "Цвет темы");
        SETTING_TRANSLATIONS.put("Thickness", "Толщина");
        SETTING_TRANSLATIONS.put("Third Person Nametag", "Тег от 3-го лица");
        SETTING_TRANSLATIONS.put("Threshold (%)", "Порог (%)");
        SETTING_TRANSLATIONS.put("Thunder Strength", "Сила грозы");
        SETTING_TRANSLATIONS.put("Tier", "Уровень");
        SETTING_TRANSLATIONS.put("Tint", "Окрашивание");
        SETTING_TRANSLATIONS.put("Time", "Время");
        SETTING_TRANSLATIONS.put("Time Changer", "Изменение времени");
        SETTING_TRANSLATIONS.put("Timestamp", "Время");
        SETTING_TRANSLATIONS.put("Tints inventory slots holding signature items so they're visible at a glance", "Подкрашивает слоты инвентаря с особыми предметами, чтобы их было видно сразу");
        SETTING_TRANSLATIONS.put("Tints maces in your inventory red/yellow/green based on attack cooldown progress", "Подкрашивает булавы красным/жёлтым/зелёным по прогрессу кд атаки");
        SETTING_TRANSLATIONS.put("Toggle", "Переключатель");
        SETTING_TRANSLATIONS.put("Toggle Message", "Сообщение о переключении");
        SETTING_TRANSLATIONS.put("Total", "Всего");
        SETTING_TRANSLATIONS.put("Totem Indicator", "Индикатор тотема");
        SETTING_TRANSLATIONS.put("TPS HUD", "TPS HUD");
        SETTING_TRANSLATIONS.put("Tracks who pops totems near you - prints to chat and adds \\", "Отслеживает, кто рядом съедает тотемы — пишет в чат и добавляет \\");
        SETTING_TRANSLATIONS.put("Trajectory", "Траектория");
        SETTING_TRANSLATIONS.put("Translate", "Перевести");
        SETTING_TRANSLATIONS.put("Translator", "Переводчик");
        SETTING_TRANSLATIONS.put("Trap Name", "Имя ловушки");
        SETTING_TRANSLATIONS.put("Trap Type", "Тип ловушки");
        SETTING_TRANSLATIONS.put("Trapka Name", "Имя «Трапка»");
        SETTING_TRANSLATIONS.put("Trigger", "Триггер");
        SETTING_TRANSLATIONS.put("Tuning", "Настройка");
        SETTING_TRANSLATIONS.put("Tweaks for chat and screenshots: collapse repeated messages, copy F2 screenshots to clipboard", "Твики чата и скриншотов: сворачивание повторов, копирование F2 в буфер");
        SETTING_TRANSLATIONS.put("Twilight", "Сумерки");
        SETTING_TRANSLATIONS.put("Underline", "Подчёркивание");
        SETTING_TRANSLATIONS.put("Upon Impact", "При ударе");
        SETTING_TRANSLATIONS.put("Use /feed Command", "Использовать /feed");
        SETTING_TRANSLATIONS.put("Use Held Item", "Использовать предмет в руке");
        SETTING_TRANSLATIONS.put("Use Preset", "Использовать пресет");
        SETTING_TRANSLATIONS.put("Vanilla", "Ванилла");
        SETTING_TRANSLATIONS.put("Vertical Layout", "Вертикальный макет");
        SETTING_TRANSLATIONS.put("Vibrance", "Сочность");
        SETTING_TRANSLATIONS.put("Visible", "Видимый");
        SETTING_TRANSLATIONS.put("Visual", "Визуальное");
        SETTING_TRANSLATIONS.put("Volume", "Громкость");
        SETTING_TRANSLATIONS.put("Warns when the held pickaxe is low on durability and can auto-swap to a safe hotbar slot", "Предупреждает при низкой прочности кирки в руке и может авто-сменить на безопасный слот");
        SETTING_TRANSLATIONS.put("Weather Changer", "Изменение погоды");
        SETTING_TRANSLATIONS.put("Weather Type", "Тип погоды");
        SETTING_TRANSLATIONS.put("Whitelist", "Белый список");
        SETTING_TRANSLATIONS.put("Width", "Ширина");
        SETTING_TRANSLATIONS.put("X Offset", "Смещение X");
        SETTING_TRANSLATIONS.put("Y Offset", "Смещение Y");
        SETTING_TRANSLATIONS.put("Yavnaya Pyl Name", "Имя «Явная пыль»");
        SETTING_TRANSLATIONS.put("Zoom", "Зум");
        SETTING_TRANSLATIONS.put("Zoom functionality", "Функция зума");
        SETTING_TRANSLATIONS.put("Zoom In Duration", "Длительность приближения");
        SETTING_TRANSLATIONS.put("Zoom Out Duration", "Длительность отдаления");
        SETTING_TRANSLATIONS.put("Zoom Sensitivity", "Чувствительность зума");
        SETTING_TRANSLATIONS.put("Zoom Speed", "Скорость зума");
    }

    private static void en(String key, String value) { EN_TABLE.put(key, value); }
    private static void ru(String key, String value) { RU_TABLE.put(key, value); }

    private Lang() {}
}
