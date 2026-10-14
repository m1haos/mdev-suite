# mdev-suite

Ядро студии и плагины на нём, в одном Gradle-проекте. Архитектура Eventide — `docs/13-eventide-architecture.md`.

| Модуль | Плагин | Что это |
|---|---|---|
| `core` | MDevCore | Общее ядро: палитра и MiniMessage, конфиги с версиями, языки, предметы из конфига, награды, сценарии с сигналами, библиотека эффектов, статистика игроков (SQLite/MySQL), Vault. |
| `eventide` | Eventide | Ивенты из этапов: метеорит, сброс груза, царь горы, осада с боссом. Тайники с ключами, стражи, очки, награды, расписание, случайные ивенты по редкостям, PlaceholderAPI. |

## Версии

Один jar работает на **Paper 1.21.8 и новее, включая 26.x**. Код компилируется против API 1.21.8 под Java 21. Устаревшие методы API не используются, чтобы jar не сломался на 26.x.

## Тестовый сервер

Дважды кликнуть `test-server.bat`. Он собирает оба плагина и поднимает Paper 26.2 в `run/26.2` (без лицензии, `online-mode=false`). Вывод дублируется в `run/launcher.log`.

PlaceholderAPI, Vault и плагин экономики (например, EssentialsX) для проверки интеграций кладутся вручную в `run/26.2/plugins`.

```
gradlew build                     → core/build/libs/MDevCore-*.jar, eventide/build/libs/Eventide-*.jar
gradlew :eventide:runServer       → тестовый сервер 26.2 (папка run/26.2)
gradlew :eventide:runServerLegacy → то же на 1.21.8 (папка run/1.21.8)
```

## Ивенты в комплекте

| Файл | Ивент | Чем заканчивается |
|---|---|---|
| `meteor.yml` | Метеорит: тайники трёх уровней, стражи с ключами | Все тайники открыты или вышло время |
| `airdrop.yml` | Сброс груза: ящик падает с неба, вскрывается, если простоять рядом одному 30 с | Ящик вскрыт или вышло время |
| `koth.yml` | Царь горы: очки за каждую секунду в зоне, лидер на боссбаре | Кто-то набрал `win-score` или вышло время |
| `siege.yml` | Осада: три волны мобов, затем босс с двумя фазами | Босс убит или вышло время |

Быстрая проверка: `/op <ник>`, `/eventide start airdrop`. Чтобы не ждать отсчёт, поменять первое `after:` в файле ивента на `20s` и сделать `/eventide reload`.

## Команды Eventide

Все имена настраиваются в `config.yml` → `commands`: алиасы самой команды (по умолчанию `/ev`, `/events`, `/event`) и каждой подкоманды (например, `/ev key` вместо `/eventide givekey`). `/eventide help` показывает команды, доступные игроку, вместе с их алиасами. У `/mdevcore` тоже есть алиасы: `/mdc`, `/mcore` (`command-aliases` в `MDevCore/config.yml`).

| Команда | Что делает | Право |
|---|---|---|
| `/eventide` (`/ev`) | Расписание и идущие ивенты | все |
| `/eventide help` | Список команд с алиасами | все |
| `/eventide start <ивент>` | Запустить сейчас | `eventide.admin` |
| `/eventide stop <ивент>` | Остановить, раздать награды по текущим очкам | `eventide.admin` |
| `/eventide givekey <игрок> <редкость> [кол-во]` | Выдать ключ | `eventide.admin` |
| `/eventide buykey <редкость> [кол-во]` | Купить ключ за валюту Vault (если включено `modules.keys.shop`) | `eventide.command.buykey` |
| `/eventide loot` | Редактор лута в диалоговых окнах | `eventide.admin` |
| `/eventide reload` | Перечитать конфиги, ивенты, лут | `eventide.admin` |

## Редкости и ключи

`plugins/Eventide/rarities.yml` — все редкости сервера. Можно добавить сколько угодно своих. У каждой редкости:
- название, блок тайника, очки и эффекты;
- ключ: предмет (материал, имя, описание, модель, свечение, чары), цена в `/eventide buykey`;
- `consume: false` — ключ не тратится;
- `also-opens: [common, rare]` — мастер-ключ, открывающий и другие редкости.

Редкость без секции `key` открывается без ключа (так сделан груз сброса). Лут-таблицы только перечисляют, что падает для каждой редкости, поэтому один «редкий» ключ открывает редкие тайники любой таблицы. Смена предмета ключа не ломает уже выданные ключи.

## Редактор лута

`/eventide loot` → таблица → уровень. В окне уровня видны все предметы с весом, шансом за бросок и количеством.

- «+ Предмет из руки»: предмет сохраняется в таблицу целиком: чары, имя, модель, данные других плагинов. В файле это строка `item-data`.
- Кнопка предмета — ползунки веса и количества, удаление с подтверждением.
- «Настройки уровня»: число бросков и очки за открытие в этой таблице. Ключ и его цена меняются в `rarities.yml`.

Изменения сразу пишутся в `loot/<таблица>.yml` и перечитываются. Идущие ивенты доигрывают со старым лутом. Нужны сервер и клиент 1.21.6+ (диалоговые окна).

## Модули

`plugins/Eventide/config.yml`, секция `modules`: `scheduler`, `countdown-bar`, `guards`, `keys` (`enabled`, `from-guards`, `as-reward`, `shop`, `captain`), `leaderboard`, `rewards`, `effects`, `cache-glow`, `mob-glow`, `marker`, `stats`, `placeholders`. Любой можно выключить, файлы ивентов менять не нужно.

## Действия этапов

- `captain`: NPC-капитан даёт ключ редкости `rarity` за задание. `task: {type: kill, count: 5, entities: [zombie]}` (убить мобов рядом с ивентом, без `entities` любых монстров), `{type: bring, item: iron_ingot, count: 16}` (принести предметы) или без `task` (ключ за разговор). `per-player` — сколько ключей даст одному игроку, `dialog` — реплики при первом разговоре, `entity` и `name` как у стражей. Выключается `modules.keys.captain`.
- Ядро: `broadcast`, `title`, `actionbar`, `sound`, `command`, `effect`.
- Eventide: `countdown`, `marker`, `spawn-loot`, `spawn-guards`, `airdrop`, `capture-zone`, `waves`, `boss`, `captain`, `collect`.
- `collect`: блоки вокруг места (тыквы, подарки), игрок берёт их кликом или ударом и получает очки; новые появляются по таймеру, часть может быть «проклятой» (больше очков, из блока вылезают мобы). `goal` даёт сигнал `collect-won`.
- `capture-zone` принимает `offset: [x, y, z]`: зона не на месте ивента, а со сдвигом, например на вершине постройки.
- `marker` держит место видимым до конца ивента: эффект `event-marker` раз в 2 с и `<расстояние> м ↗` в строке действий у игроков в радиусе 160 блоков (`compass: {radius, hide-within}`).
- Стражи, волны и босс светятся сквозь стены: `glow: red` (любой цвет Minecraft), `true` или `false`. В `waves` есть `glow-when-left: 3` — подсветить только трёх последних мобов волны.
- Этап может ждать сигнала вместо таймера: `wait-for: boss-killed`, `timeout: 15m`. Сигналы: `caches-opened`, `guards-killed`, `airdrop-captured`, `zone-won`, `collect-won`, `wave-cleared`, `waves-cleared`, `boss-killed`.
- `finish-on: [сигнал]` в файле ивента — завершить ивент сразу, когда пришёл сигнал.

## Награды

Строки `тип: значение`: `command`, `player-command`, `message`, `broadcast`, `xp`, `item`, `sound`, `effect`, `money` (Vault), `key` (`key: rare 2`, от Eventide — работает в любом плагине на MDevCore).

## Статистика и плейсхолдеры

MDevCore хранит счётчики игроков: `plugins/MDevCore/data.db` (SQLite) или MySQL/MariaDB (`storage` в `MDevCore/config.yml`). Eventide пишет туда победы, очки, участие, открытые тайники, вскрытые грузы и убитых боссов.

| Плейсхолдер | Что показывает |
|---|---|
| `%eventide_next%`, `%eventide_next_time%`, `%eventide_next_in%` | Ближайший ивент по расписанию, время старта, сколько осталось |
| `%eventide_next_random%` | Сколько осталось до броска случайного ивента; пусто, если они выключены или на паузе |
| `%eventide_active%`, `%eventide_active_x%` / `_y` / `_z` / `_world` | Идущий ивент и где он |
| `%eventide_points%`, `%eventide_leader%`, `%eventide_leader_points%` | Очки игрока и лидер в идущем ивенте |
| `%eventide_wins%`, `%eventide_played%`, `%eventide_total_points%`, `%eventide_caches%`, `%eventide_airdrops%`, `%eventide_bosses%` | Статистика игрока за всё время |
| `%eventide_top_wins_1_name%`, `%eventide_top_wins_1_value%`, `%eventide_top_points_3_name%` … | Топ-10 по победам и очкам |

## Эффекты

Анимации лежат в `plugins/MDevCore/effects/*.yml` и берутся по имени. Готовые пресеты из jar доступны всегда, файлы на диске их переопределяют. Типы элементов: `burst`, `ring`, `sphere`, `helix`, `shockwave`, `column`, `fall`, `sound`, `firework`, `lightning`. Кольца, волны, сферы и спирали растягиваются под размер зоны (так рисуется граница в царе горы).

- `/mdevcore effect <имя>` — посмотреть эффект там, где стоишь.
- `/mdevcore effects` — список, `/mdevcore reload` — перечитать после правки.

## API для аддонов

- `EventideStartEvent` (отменяемое) и `EventideFinishEvent` (места и очки).
- Свои действия этапов: `MDevCore.actions().register("my-action", ...)`, свои награды: `MDevCore.rewards().register(...)`.
- Статистика: `MDevCore.stats()`, экономика: `MDevCore.money()`.

## Известные ограничения

- При падении сервера во время ивента занятые блоки остаются в мире. Нужна запись идущего ивента на диск и уборка при старте.
- Редактор меняет список `loot:` уровня целиком, поэтому комментарии внутри этого списка после правки пропадают. Комментарии в остальном файле остаются.
- MythicMobs, предметы ItemsAdder/Nexo и NPC из Citizens/FancyNpcs — следующие этапы.
