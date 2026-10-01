# Тактический планшет, часть 1: серверное ядро — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Собрать Fabric-мод 1.20.1 с предметом «Тактический планшет» и полностью рабочей серверной частью удара: валидация, автомат фаз, поэтапное разрушение кратером, сеть, команды и замер производительности.

**Architecture:** Чистая логика (параметры, фазы, профиль кратера, порядок чанков, бюджет тика) не зависит от классов Minecraft и покрыта JUnit. Поверх неё — `DestructionJob` (обработка чанков волной с бюджетом времени и тикетами) и `StrikeManager` (автомат фаз, отмена, лимиты), которые проверяются серверными игровыми тестами. Сеть и команды — тонкие обёртки над менеджером.

**Tech Stack:** Minecraft 1.20.1, Fabric Loader 0.19.5, Fabric API 0.92.12+1.20.1, Fabric Loom 1.17.21 (плагин `net.fabricmc.fabric-loom-remap`), Gradle 9.7.1, Java 17 (сборка на JDK 21 с `release = 17`), официальные маппинги Mojang, JUnit 5.11.4, Fabric GameTest.

**Spec:** `docs/superpowers/specs/2026-10-01-tactical-tablet-design.md`

## Место этого плана

Спека покрывает три независимо проверяемые подсистемы, поэтому планов три:

1. **Серверное ядро (этот план).** После него мод собирается, планшет существует как предмет, удар запускается командой и пакетами, разрушение работает и замерено.
2. **Интерфейс и выбор цели.** Экран планшета, виджеты, прицел, кольцо зоны, клиентское состояние удара. Пишется после выполнения этого плана.
3. **Кат-сцена, эффекты, звуки, генератор текстур.** Пишется после плана 2.

Из спеки в этот план входят разделы 2–5, 7, 10 (серверная сторона), 11 (серверный конфиг и команды), серверная часть 12. Не входят: раздел 6 (интерфейс), 8 (кат-сцена), 9 (эффекты и звуки), клиентский конфиг.

До плана 3 модель предмета использует ванильную текстуру `minecraft:item/recovery_compass_16`; собственную текстуру рисует генератор из плана 3.

## Global Constraints

- Каталог проекта: `C:\Users\pozo\Projects\tactical-tablet`. Ветка для работы: `feature/server-core` (создаётся в задаче 1).
- Идентификатор мода: `tactical_tablet`. Корневой пакет: `moe.dexx.tacticaltablet`. Лицензия: MIT.
- Версии фиксированы в `gradle.properties`: Minecraft `1.20.1`, Loader `0.19.5`, Loom `1.17.21` (1.18 требует Java 25 для Gradle), Fabric API `0.92.12+1.20.1`, Gradle wrapper `9.7.1`.
- Код компилируется с `options.release = 17`. Маппинги — `loom.officialMojangMappings()`.
- `loom.splitEnvironmentSourceSets()`: код в `src/main` не ссылается на клиентские классы (`net.minecraft.client.*`).
- Радиус удара: 1…1000 и не больше серверного `maxRadius`. Значение вне диапазона отклоняется с причиной, а не обрезается.
- Сервер не доверяет клиенту: каждая проверка из раздела 4 спеки повторяется на сервере.
- Разрушение не создаёт сущностей-предметов, не вызывает обновления соседей и не трогает колонки с `r > R`.
- Все строки для игрока идут через ключи перевода; файлы `en_us.json` и `ru_ru.json` всегда содержат одинаковый набор ключей.
- Справочные исходники 1.20.1 (только чтение, имена совпадают с Mojang-маппингами; файл содержит патчи Forge, которых в Fabric нет): `C:\Users\pozo\Projects\mc-1.20.1-src\net\minecraft`.
- Команды сборки запускаются из каталога проекта через `./gradlew` в Git Bash.
- Коммиты заканчиваются строкой `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

Условия, о которых спека молчит, но которые встретятся в реальной игре. Каждое закреплено тестом в указанной задаче.

1. **Враждебные или мусорные значения из пакета клиента** (радиус `Integer.MAX_VALUE`, отрицательные числа) — запуск отклоняется с причиной, сервер не падает. Тест: задача 1, `rejectsHostileValues`.
2. **Испорченный или отредактированный вручную файл конфига** (битый JSON, нелепые числа, `null` в списке режимов) — сервер стартует со значениями по умолчанию или с приведёнными к диапазону. Тест: задача 5, `ServerConfigTest`.
3. **Планшет с произвольным NBT из `/give`** (неверные типы, неизвестный тип удара, радиус 9999) — чтение настроек даёт значения по умолчанию или приведённые к диапазону, без исключений. Тест: задача 6, `garbageNbtFallsBackToDefaults`.
4. **Цель у края мира**: у нижней границы высоты (дно кратера не уходит ниже `minY`) и за границей мира (запуск отклоняется). Тесты: задача 3, `floorIsClampedToWorldBottom`; задача 8, `targetOutsideWorldBorderRejected`.
5. **Два удара по пересекающимся чанкам и остановка посреди работы** — оба задания завершаются, тикеты одного не снимают тикеты другого, после `stop()` тикетов не остаётся. Тесты: задача 7, `overlappingJobsBothFinish` и `stopReleasesEverything`.

---

## Структура файлов

| Файл | Ответственность |
|---|---|
| `settings.gradle`, `build.gradle`, `gradle.properties` | Сборка, наборы исходников, запуски `runGametest` и `runBenchmark` |
| `src/main/resources/fabric.mod.json` | Метаданные мода и точки входа |
| `src/main/java/moe/dexx/tacticaltablet/TacticalTablet.java` | Точка входа: регистрация, жизненный цикл менеджера, замер тиков |
| `…/strike/StrikeType.java` | Перечисление типов удара |
| `…/strike/StrikeParams.java` | Параметры удара и их валидация (чистая логика) |
| `…/strike/StrikePhase.java`, `StrikeTimeline.java` | Фазы и их длительности (чистая логика) |
| `…/strike/AbortReason.java` | Ключи причин отмены |
| `…/strike/Strike.java`, `StrikeSummary.java` | Состояние одного удара и итог завершённого |
| `…/strike/StrikeManager.java` | Автомат фаз, лимиты, отмена, остановка |
| `…/strike/PlayerOwnerProbe.java`, `PlayerChecks.java` | Проверки игрока-владельца |
| `…/destruction/CraterProfile.java` | Форма кратера (чистая логика) |
| `…/destruction/ChunkWaveOrder.java` | Порядок чанков волной (чистая логика) |
| `…/destruction/TickTimeTracker.java`, `TickBudget.java` | Среднее время тика и адаптивный бюджет (чистая логика) |
| `…/destruction/ModTags.java` | Тег защищённых блоков |
| `…/destruction/DestructionJob.java` | Обработка чанков: тикеты, колонки, сущности, оплавление |
| `…/config/ServerConfig.java` | Серверный конфиг (Gson) |
| `…/item/TacticalTabletItem.java`, `ModItems.java`, `TabletSettings.java` | Предмет, регистрация, настройки в NBT |
| `…/net/ModPackets.java`, `StrikeState.java`, `StrikeCodecs.java`, `ServerNetworking.java` | Идентификаторы пакетов, кодеки, серверные обработчики и рассылка |
| `…/command/TabletCommands.java` | `/tacticaltablet stop|status|strike` |
| `…/bench/BenchmarkHook.java` | Автозамер на выделенном сервере по системному свойству |
| `src/client/java/…/client/TacticalTabletClient.java` | Пустая клиентская точка входа (наполняется в плане 2) |
| `src/main/resources/assets/tactical_tablet/…` | Языковые файлы, модель предмета |
| `src/main/resources/data/tactical_tablet/…` | Рецепт, тег блоков |
| `src/test/java/…` | Юнит-тесты JUnit |
| `src/gametest/…` | Серверные игровые тесты и их `fabric.mod.json` |

---

### Task 1: Каркас проекта и параметры удара

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradle.properties`, `LICENSE`
- Create: `src/main/resources/fabric.mod.json`
- Create: `src/main/java/moe/dexx/tacticaltablet/TacticalTablet.java`
- Create: `src/client/java/moe/dexx/tacticaltablet/client/TacticalTabletClient.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/StrikeType.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/StrikeParams.java`
- Test: `src/test/java/moe/dexx/tacticaltablet/strike/StrikeParamsTest.java`

**Interfaces:**
- Consumes: ничего.
- Produces:
  - `TacticalTablet.MOD_ID` (`"tactical_tablet"`), `TacticalTablet.LOGGER`, `TacticalTablet.id(String path) -> ResourceLocation`.
  - `enum StrikeType { ORBITAL_LASER, VISUAL_ONLY }`.
  - `record StrikeParams(boolean hasTarget, int targetX, int targetY, int targetZ, int radius, int power, int salvos, int countdownSeconds, StrikeType type, boolean destroyBlocks, boolean destroyLiquids, boolean damageEntities)` с `DEFAULT`, `validate(int serverMaxRadius, int minBuildHeight, int maxBuildHeight) -> String` (ключ причины или `null`), `validateSettings(int serverMaxRadius) -> String`, `modifiesWorld() -> boolean`, `withTarget(int,int,int)`, `withRadius(int)`, `withPower(int)`, `withType(StrikeType)`, `effectiveMaxRadius(int) -> int`, константы `REJECT_*`.

- [ ] **Step 1: Создать ветку**

```bash
cd /c/Users/pozo/Projects/tactical-tablet
git checkout -b feature/server-core
```

- [ ] **Step 2: Написать файлы сборки**

`settings.gradle`:

```groovy
pluginManagement {
    repositories {
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = 'tactical-tablet'
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

minecraft_version=1.20.1
loader_version=0.19.5
loom_version=1.17.21
fabric_api_version=0.92.12+1.20.1
junit_version=5.11.4

mod_version=0.1.0
maven_group=moe.dexx
archives_base_name=tactical-tablet
```

`build.gradle`:

```groovy
plugins {
    id 'net.fabricmc.fabric-loom-remap' version "${loom_version}"
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

loom {
    splitEnvironmentSourceSets()

    mods {
        "tactical_tablet" {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings loom.officialMojangMappings()
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

    testImplementation platform("org.junit:junit-bom:${project.junit_version}")
    testImplementation "org.junit.jupiter:junit-jupiter"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
}

processResources {
    def version = project.version
    inputs.property "version", version

    filesMatching("fabric.mod.json") {
        expand "version": version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 17
    it.options.encoding = "UTF-8"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

test {
    useJUnitPlatform()
}

jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
```

`LICENSE` — стандартный текст MIT с первой строкой `Copyright (c) 2026 pozo`.

- [ ] **Step 3: Сгенерировать Gradle wrapper из кэшированного дистрибутива 9.7.1**

```bash
cd /c/Users/pozo/Projects/tactical-tablet
"$(ls -d ~/.gradle/wrapper/dists/gradle-9.7.1-bin/*/gradle-9.7.1)/bin/gradle" wrapper --gradle-version 9.7.1 --distribution-type bin
```

Expected: `BUILD SUCCESSFUL`, появились `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`. Первый запуск скачивает Loom и Minecraft — несколько минут.

- [ ] **Step 4: Написать метаданные и точки входа**

`src/main/resources/fabric.mod.json`:

```json
{
  "schemaVersion": 1,
  "id": "tactical_tablet",
  "version": "${version}",
  "name": "Tactical Tablet",
  "description": "A tablet that calls an orbital laser strike with a cinematic launch sequence.",
  "authors": ["pozo"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": ["moe.dexx.tacticaltablet.TacticalTablet"],
    "client": ["moe.dexx.tacticaltablet.client.TacticalTabletClient"]
  },
  "depends": {
    "fabricloader": ">=0.15.0",
    "minecraft": "1.20.1",
    "java": ">=17",
    "fabric-api": "*"
  }
}
```

`src/main/java/moe/dexx/tacticaltablet/TacticalTablet.java`:

```java
package moe.dexx.tacticaltablet;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TacticalTablet implements ModInitializer {
    public static final String MOD_ID = "tactical_tablet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Tactical Tablet loaded");
    }
}
```

`src/client/java/moe/dexx/tacticaltablet/client/TacticalTabletClient.java`:

```java
package moe.dexx.tacticaltablet.client;

import net.fabricmc.api.ClientModInitializer;

public final class TacticalTabletClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
    }
}
```

- [ ] **Step 5: Написать падающий тест параметров**

`src/test/java/moe/dexx/tacticaltablet/strike/StrikeParamsTest.java`:

```java
package moe.dexx.tacticaltablet.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StrikeParamsTest {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private static StrikeParams valid() {
        return StrikeParams.DEFAULT.withTarget(10, 64, -20);
    }

    private static StrikeParams with(int radius, int power, int salvos, int countdown) {
        StrikeParams base = valid();
        return new StrikeParams(true, base.targetX(), base.targetY(), base.targetZ(), radius, power, salvos, countdown,
                base.type(), base.destroyBlocks(), base.destroyLiquids(), base.damageEntities());
    }

    @Test
    void defaultsHaveNoTargetAndAreRejected() {
        assertFalse(StrikeParams.DEFAULT.hasTarget());
        assertEquals(StrikeParams.REJECT_NO_TARGET, StrikeParams.DEFAULT.validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void validParamsPass() {
        assertNull(valid().validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void radiusBounds() {
        assertEquals(StrikeParams.REJECT_RADIUS, with(0, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertNull(with(1, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertNull(with(1000, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_RADIUS, with(1001, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void serverLimitLowersTheMaximum() {
        assertNull(with(500, 5, 3, 10).validate(500, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_RADIUS, with(501, 5, 3, 10).validate(500, MIN_Y, MAX_Y));
    }

    @Test
    void serverLimitCannotRaiseTheMaximumAboveOneThousand() {
        assertEquals(1000, StrikeParams.effectiveMaxRadius(5000));
        assertEquals(1, StrikeParams.effectiveMaxRadius(-3));
        assertEquals(StrikeParams.REJECT_RADIUS, with(1001, 5, 3, 10).validate(5000, MIN_Y, MAX_Y));
    }

    @Test
    void powerSalvosAndCountdownBounds() {
        assertEquals(StrikeParams.REJECT_POWER, with(50, 0, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_POWER, with(50, 11, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_SALVOS, with(50, 5, 0, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_SALVOS, with(50, 5, 11, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_COUNTDOWN, with(50, 5, 3, 2).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_COUNTDOWN, with(50, 5, 3, 61).validate(1000, MIN_Y, MAX_Y));
        assertNull(with(50, 1, 1, 3).validate(1000, MIN_Y, MAX_Y));
        assertNull(with(50, 10, 10, 60).validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void targetHeightMustBeInsideTheWorld() {
        assertEquals(StrikeParams.REJECT_TARGET_HEIGHT, StrikeParams.DEFAULT.withTarget(0, -65, 0).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_TARGET_HEIGHT, StrikeParams.DEFAULT.withTarget(0, 320, 0).validate(1000, MIN_Y, MAX_Y));
        assertNull(StrikeParams.DEFAULT.withTarget(0, -64, 0).validate(1000, MIN_Y, MAX_Y));
        assertNull(StrikeParams.DEFAULT.withTarget(0, 319, 0).validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void rejectsHostileValues() {
        assertEquals(StrikeParams.REJECT_RADIUS, with(Integer.MAX_VALUE, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_RADIUS, with(Integer.MIN_VALUE, 5, 3, 10).validate(1000, MIN_Y, MAX_Y));
        assertEquals(StrikeParams.REJECT_POWER, with(50, Integer.MIN_VALUE, 3, 10).validate(1000, MIN_Y, MAX_Y));
        StrikeParams noType = new StrikeParams(true, 0, 64, 0, 50, 5, 3, 10, null, true, true, true);
        assertEquals(StrikeParams.REJECT_TYPE, noType.validate(1000, MIN_Y, MAX_Y));
    }

    @Test
    void settingsValidationIgnoresTheTarget() {
        assertNull(StrikeParams.DEFAULT.validateSettings(1000));
        assertEquals(StrikeParams.REJECT_RADIUS, StrikeParams.DEFAULT.withRadius(1001).validateSettings(1000));
    }

    @Test
    void onlyDestructiveOrbitalStrikesModifyTheWorld() {
        assertTrue(valid().modifiesWorld());
        assertFalse(valid().withType(StrikeType.VISUAL_ONLY).modifiesWorld());
        StrikeParams keepBlocks = new StrikeParams(true, 0, 64, 0, 50, 5, 3, 10, StrikeType.ORBITAL_LASER, false, true, true);
        assertFalse(keepBlocks.modifiesWorld());
    }

    @Test
    void withersChangeOnlyTheirField() {
        StrikeParams changed = valid().withRadius(77).withPower(9);
        assertEquals(77, changed.radius());
        assertEquals(9, changed.power());
        assertEquals(valid().targetX(), changed.targetX());
        assertEquals(valid().salvos(), changed.salvos());
    }
}
```

- [ ] **Step 6: Убедиться, что тест не компилируется**

Run: `./gradlew test`
Expected: FAIL, ошибка компиляции `cannot find symbol ... StrikeParams`.

- [ ] **Step 7: Реализовать параметры**

`src/main/java/moe/dexx/tacticaltablet/strike/StrikeType.java`:

```java
package moe.dexx.tacticaltablet.strike;

public enum StrikeType {
    ORBITAL_LASER,
    VISUAL_ONLY
}
```

`src/main/java/moe/dexx/tacticaltablet/strike/StrikeParams.java`:

```java
package moe.dexx.tacticaltablet.strike;

/**
 * Everything a player can configure about one strike. Validation returns the translation key of the first
 * violated rule, or null when the parameters are acceptable; out-of-range values are rejected, never clamped.
 */
public record StrikeParams(
        boolean hasTarget, int targetX, int targetY, int targetZ,
        int radius, int power, int salvos, int countdownSeconds,
        StrikeType type, boolean destroyBlocks, boolean destroyLiquids, boolean damageEntities) {

    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 1000;
    public static final int MIN_POWER = 1;
    public static final int MAX_POWER = 10;
    public static final int MIN_SALVOS = 1;
    public static final int MAX_SALVOS = 10;
    public static final int MIN_COUNTDOWN = 3;
    public static final int MAX_COUNTDOWN = 60;

    public static final String REJECT_NO_TARGET = "tactical_tablet.reject.no_target";
    public static final String REJECT_TARGET_HEIGHT = "tactical_tablet.reject.target_height";
    public static final String REJECT_RADIUS = "tactical_tablet.reject.radius";
    public static final String REJECT_POWER = "tactical_tablet.reject.power";
    public static final String REJECT_SALVOS = "tactical_tablet.reject.salvos";
    public static final String REJECT_COUNTDOWN = "tactical_tablet.reject.countdown";
    public static final String REJECT_TYPE = "tactical_tablet.reject.type";

    public static final StrikeParams DEFAULT =
            new StrikeParams(false, 0, 0, 0, 50, 5, 3, 10, StrikeType.ORBITAL_LASER, true, true, true);

    public static int effectiveMaxRadius(int serverMaxRadius) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, serverMaxRadius));
    }

    public String validateSettings(int serverMaxRadius) {
        if (type == null) {
            return REJECT_TYPE;
        }
        if (radius < MIN_RADIUS || radius > effectiveMaxRadius(serverMaxRadius)) {
            return REJECT_RADIUS;
        }
        if (power < MIN_POWER || power > MAX_POWER) {
            return REJECT_POWER;
        }
        if (salvos < MIN_SALVOS || salvos > MAX_SALVOS) {
            return REJECT_SALVOS;
        }
        if (countdownSeconds < MIN_COUNTDOWN || countdownSeconds > MAX_COUNTDOWN) {
            return REJECT_COUNTDOWN;
        }
        return null;
    }

    public String validate(int serverMaxRadius, int minBuildHeight, int maxBuildHeight) {
        if (!hasTarget) {
            return REJECT_NO_TARGET;
        }
        if (targetY < minBuildHeight || targetY >= maxBuildHeight) {
            return REJECT_TARGET_HEIGHT;
        }
        return validateSettings(serverMaxRadius);
    }

    public boolean modifiesWorld() {
        return type == StrikeType.ORBITAL_LASER && destroyBlocks;
    }

    public StrikeParams withTarget(int x, int y, int z) {
        return new StrikeParams(true, x, y, z, radius, power, salvos, countdownSeconds,
                type, destroyBlocks, destroyLiquids, damageEntities);
    }

    public StrikeParams withRadius(int newRadius) {
        return new StrikeParams(hasTarget, targetX, targetY, targetZ, newRadius, power, salvos, countdownSeconds,
                type, destroyBlocks, destroyLiquids, damageEntities);
    }

    public StrikeParams withPower(int newPower) {
        return new StrikeParams(hasTarget, targetX, targetY, targetZ, radius, newPower, salvos, countdownSeconds,
                type, destroyBlocks, destroyLiquids, damageEntities);
    }

    public StrikeParams withType(StrikeType newType) {
        return new StrikeParams(hasTarget, targetX, targetY, targetZ, radius, power, salvos, countdownSeconds,
                newType, destroyBlocks, destroyLiquids, damageEntities);
    }
}
```

- [ ] **Step 8: Запустить тесты и сборку**

Run: `./gradlew test build`
Expected: `BUILD SUCCESSFUL`; в `build/reports/tests/test/index.html` 11 тестов, 0 провалов; существует `build/libs/tactical-tablet-0.1.0.jar`.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "Scaffold the Fabric project and add strike parameters

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Фазы удара и их длительности

**Files:**
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/StrikePhase.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/StrikeTimeline.java`
- Test: `src/test/java/moe/dexx/tacticaltablet/strike/StrikeTimelineTest.java`

**Interfaces:**
- Consumes: `StrikeParams` (задача 1).
- Produces:
  - `enum StrikePhase { COUNTDOWN, CHARGE, TRAVEL, IMPACT, DESTROYING, DONE }` с `cancellable() -> boolean`.
  - `StrikeTimeline.durationTicks(StrikePhase, StrikeParams) -> int` (−1 для фаз без фиксированной длительности).
  - `StrikeTimeline.next(StrikePhase phase, boolean jobRunning) -> StrikePhase`.
  - Константы `CHARGE_TICKS = 400`, `TRAVEL_TICKS = 80`, `IMPACT_BASE_TICKS = 120`, `IMPACT_TICKS_PER_EXTRA_SALVO = 8`.

- [ ] **Step 1: Написать падающий тест**

`src/test/java/moe/dexx/tacticaltablet/strike/StrikeTimelineTest.java`:

```java
package moe.dexx.tacticaltablet.strike;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StrikeTimelineTest {
    private static StrikeParams params(int salvos, int countdown) {
        return new StrikeParams(true, 0, 64, 0, 50, 5, salvos, countdown, StrikeType.ORBITAL_LASER, true, true, true);
    }

    @Test
    void onlyThePhasesBeforeTheShotAreCancellable() {
        assertTrue(StrikePhase.COUNTDOWN.cancellable());
        assertTrue(StrikePhase.CHARGE.cancellable());
        assertFalse(StrikePhase.TRAVEL.cancellable());
        assertFalse(StrikePhase.IMPACT.cancellable());
        assertFalse(StrikePhase.DESTROYING.cancellable());
        assertFalse(StrikePhase.DONE.cancellable());
    }

    @Test
    void countdownLastsTheConfiguredSeconds() {
        assertEquals(60, StrikeTimeline.durationTicks(StrikePhase.COUNTDOWN, params(1, 3)));
        assertEquals(1200, StrikeTimeline.durationTicks(StrikePhase.COUNTDOWN, params(1, 60)));
    }

    @Test
    void chargeAndTravelAreFixed() {
        assertEquals(400, StrikeTimeline.durationTicks(StrikePhase.CHARGE, params(3, 10)));
        assertEquals(80, StrikeTimeline.durationTicks(StrikePhase.TRAVEL, params(3, 10)));
    }

    @Test
    void impactGrowsWithExtraSalvos() {
        assertEquals(120, StrikeTimeline.durationTicks(StrikePhase.IMPACT, params(1, 10)));
        assertEquals(136, StrikeTimeline.durationTicks(StrikePhase.IMPACT, params(3, 10)));
        assertEquals(192, StrikeTimeline.durationTicks(StrikePhase.IMPACT, params(10, 10)));
    }

    @Test
    void openEndedPhasesHaveNoDuration() {
        assertEquals(-1, StrikeTimeline.durationTicks(StrikePhase.DESTROYING, params(3, 10)));
        assertEquals(-1, StrikeTimeline.durationTicks(StrikePhase.DONE, params(3, 10)));
    }

    @Test
    void phasesFollowTheScenario() {
        assertEquals(StrikePhase.CHARGE, StrikeTimeline.next(StrikePhase.COUNTDOWN, false));
        assertEquals(StrikePhase.TRAVEL, StrikeTimeline.next(StrikePhase.CHARGE, false));
        assertEquals(StrikePhase.IMPACT, StrikeTimeline.next(StrikePhase.TRAVEL, false));
        assertEquals(StrikePhase.DESTROYING, StrikeTimeline.next(StrikePhase.IMPACT, true));
        assertEquals(StrikePhase.DONE, StrikeTimeline.next(StrikePhase.IMPACT, false));
        assertEquals(StrikePhase.DONE, StrikeTimeline.next(StrikePhase.DESTROYING, false));
        assertEquals(StrikePhase.DONE, StrikeTimeline.next(StrikePhase.DONE, true));
    }
}
```

- [ ] **Step 2: Убедиться, что тест не компилируется**

Run: `./gradlew test --tests "*StrikeTimelineTest"`
Expected: FAIL, `cannot find symbol ... StrikePhase`.

- [ ] **Step 3: Реализовать фазы**

`src/main/java/moe/dexx/tacticaltablet/strike/StrikePhase.java`:

```java
package moe.dexx.tacticaltablet.strike;

public enum StrikePhase {
    COUNTDOWN(true),
    CHARGE(true),
    /** The shot has been fired: from here on the strike cannot be cancelled. */
    TRAVEL(false),
    IMPACT(false),
    DESTROYING(false),
    DONE(false);

    private final boolean cancellable;

    StrikePhase(boolean cancellable) {
        this.cancellable = cancellable;
    }

    public boolean cancellable() {
        return cancellable;
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/strike/StrikeTimeline.java`:

```java
package moe.dexx.tacticaltablet.strike;

public final class StrikeTimeline {
    public static final int CHARGE_TICKS = 400;
    public static final int TRAVEL_TICKS = 80;
    public static final int IMPACT_BASE_TICKS = 120;
    public static final int IMPACT_TICKS_PER_EXTRA_SALVO = 8;

    private StrikeTimeline() {
    }

    /** @return the fixed length of the phase in ticks, or -1 when the phase lasts until its work is done */
    public static int durationTicks(StrikePhase phase, StrikeParams params) {
        return switch (phase) {
            case COUNTDOWN -> params.countdownSeconds() * 20;
            case CHARGE -> CHARGE_TICKS;
            case TRAVEL -> TRAVEL_TICKS;
            case IMPACT -> IMPACT_BASE_TICKS + IMPACT_TICKS_PER_EXTRA_SALVO * (params.salvos() - 1);
            case DESTROYING, DONE -> -1;
        };
    }

    /** @param jobRunning whether a destruction job is still working when the phase ends */
    public static StrikePhase next(StrikePhase phase, boolean jobRunning) {
        return switch (phase) {
            case COUNTDOWN -> StrikePhase.CHARGE;
            case CHARGE -> StrikePhase.TRAVEL;
            case TRAVEL -> StrikePhase.IMPACT;
            case IMPACT -> jobRunning ? StrikePhase.DESTROYING : StrikePhase.DONE;
            case DESTROYING, DONE -> StrikePhase.DONE;
        };
    }
}
```

- [ ] **Step 4: Запустить тест**

Run: `./gradlew test --tests "*StrikeTimelineTest"`
Expected: PASS, 6 тестов.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add strike phases and their timeline

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Профиль кратера

**Files:**
- Create: `src/main/java/moe/dexx/tacticaltablet/destruction/CraterProfile.java`
- Test: `src/test/java/moe/dexx/tacticaltablet/destruction/CraterProfileTest.java`

**Interfaces:**
- Consumes: ничего.
- Produces: `CraterProfile(int radius, int power, long seed)` с методами
  - `static maxDepth(int radius, int power) -> int`,
  - `maxDepth() -> int`,
  - `depthAt(int dx, int dz) -> int` — сколько блоков снять ниже опорной высоты; 0 значит «колонку не трогать»,
  - `scorched(int dx, int dz) -> boolean` — колонка внутри `0.35 · R`,
  - `hash(int dx, int dz, int salt) -> int` — детерминированное неотрицательное число,
  - `static floorY(int ground, int depth, int minY) -> int`.

- [ ] **Step 1: Написать падающий тест**

`src/test/java/moe/dexx/tacticaltablet/destruction/CraterProfileTest.java`:

```java
package moe.dexx.tacticaltablet.destruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraterProfileTest {
    @Test
    void maxDepthFollowsTheFormula() {
        assertEquals(1, CraterProfile.maxDepth(1, 5));
        assertEquals(2, CraterProfile.maxDepth(3, 5));
        assertEquals(30, CraterProfile.maxDepth(50, 5));
        assertEquals(20, CraterProfile.maxDepth(1000, 1));
        assertEquals(128, CraterProfile.maxDepth(1000, 10));
    }

    @Test
    void smallCraterIsExact() {
        CraterProfile profile = new CraterProfile(3, 5, 12345L);
        assertEquals(2, profile.depthAt(0, 0));
        assertEquals(2, profile.depthAt(1, 0));
        assertEquals(2, profile.depthAt(1, 1));
        assertEquals(1, profile.depthAt(2, 0));
        assertEquals(1, profile.depthAt(2, 1));
        assertEquals(0, profile.depthAt(2, 2));
        assertEquals(0, profile.depthAt(3, 0));
        assertEquals(0, profile.depthAt(3, 1));
        assertEquals(0, profile.depthAt(4, 0));
    }

    @Test
    void smallCraterIsSymmetric() {
        CraterProfile profile = new CraterProfile(3, 5, 99L);
        assertEquals(profile.depthAt(2, 1), profile.depthAt(-2, -1));
        assertEquals(profile.depthAt(2, 1), profile.depthAt(1, -2));
    }

    @Test
    void radiusOneRemovesOnlyTheCentreBlock() {
        CraterProfile profile = new CraterProfile(1, 10, 7L);
        assertEquals(1, profile.depthAt(0, 0));
        assertEquals(0, profile.depthAt(1, 0));
        assertEquals(0, profile.depthAt(0, -1));
        assertEquals(0, profile.depthAt(1, 1));
    }

    @Test
    void nothingOutsideTheRadiusIsEverTouched() {
        CraterProfile profile = new CraterProfile(1000, 10, 42L);
        for (int d = 1001; d <= 1100; d++) {
            assertEquals(0, profile.depthAt(d, 0));
            assertEquals(0, profile.depthAt(0, -d));
        }
        assertEquals(0, profile.depthAt(708, 708));
        assertEquals(0, profile.depthAt(-1000, 1));
    }

    @Test
    void largeCraterStaysWithinOneBlockOfTheParabola() {
        CraterProfile profile = new CraterProfile(1000, 10, 42L);
        assertEquals(128, profile.maxDepth());
        for (int dx = -1000; dx <= 1000; dx += 37) {
            for (int dz = -1000; dz <= 1000; dz += 41) {
                long distanceSq = (long) dx * dx + (long) dz * dz;
                if (distanceSq > 1_000_000L) {
                    continue;
                }
                long expected = Math.round(128 * (1.0 - distanceSq / 1_000_000.0));
                int actual = profile.depthAt(dx, dz);
                assertTrue(Math.abs(actual - expected) <= 1, "dx=" + dx + " dz=" + dz + " actual=" + actual);
                assertTrue(actual >= 0);
            }
        }
    }

    @Test
    void sameSeedGivesTheSameCrater() {
        CraterProfile a = new CraterProfile(200, 7, 555L);
        CraterProfile b = new CraterProfile(200, 7, 555L);
        for (int dx = -200; dx <= 200; dx += 13) {
            assertEquals(a.depthAt(dx, 17), b.depthAt(dx, 17));
            assertEquals(a.hash(dx, 3, 1), b.hash(dx, 3, 1));
        }
    }

    @Test
    void hashIsNeverNegative() {
        CraterProfile profile = new CraterProfile(50, 5, -1L);
        for (int i = -500; i <= 500; i++) {
            assertTrue(profile.hash(i, -i * 31, i & 3) >= 0);
        }
    }

    @Test
    void scorchedZoneIsTheInnerThirtyFivePercent() {
        CraterProfile profile = new CraterProfile(100, 5, 1L);
        assertTrue(profile.scorched(0, 0));
        assertTrue(profile.scorched(34, 0));
        assertFalse(profile.scorched(35, 0));
        assertFalse(profile.scorched(30, 30));
        assertFalse(profile.scorched(100, 0));
    }

    @Test
    void floorIsClampedToWorldBottom() {
        assertEquals(59, CraterProfile.floorY(64, 5, -64));
        assertEquals(-64, CraterProfile.floorY(-62, 5, -64));
        assertEquals(-64, CraterProfile.floorY(-64, 1, -64));
    }
}
```

- [ ] **Step 2: Убедиться, что тест не компилируется**

Run: `./gradlew test --tests "*CraterProfileTest"`
Expected: FAIL, `cannot find symbol ... CraterProfile`.

- [ ] **Step 3: Реализовать профиль**

`src/main/java/moe/dexx/tacticaltablet/destruction/CraterProfile.java`:

```java
package moe.dexx.tacticaltablet.destruction;

/**
 * Shape of the crater: a paraboloid that is deepest at the target and reaches zero at the radius.
 * Offsets are block columns relative to the target column.
 */
public final class CraterProfile {
    /** Craters shallower than this get no edge noise, so tiny strikes are exact. */
    private static final int NOISE_MIN_DEPTH = 4;

    private final long radiusSq;
    private final int maxDepth;
    private final long seed;
    private final boolean noisy;

    public CraterProfile(int radius, int power, long seed) {
        this.radiusSq = (long) radius * radius;
        this.maxDepth = maxDepth(radius, power);
        this.seed = seed;
        this.noisy = this.maxDepth >= NOISE_MIN_DEPTH;
    }

    public static int maxDepth(int radius, int power) {
        return Math.max(1, Math.min((int) Math.round(0.6 * radius), 8 + 12 * power));
    }

    public static int floorY(int ground, int depth, int minY) {
        return Math.max(minY, ground - depth);
    }

    public int maxDepth() {
        return maxDepth;
    }

    /** @return how many blocks to strip below the column's ground level; 0 means leave the column alone */
    public int depthAt(int dx, int dz) {
        long distanceSq = (long) dx * dx + (long) dz * dz;
        if (distanceSq > radiusSq) {
            return 0;
        }
        int depth = (int) Math.round(maxDepth * (1.0 - (double) distanceSq / (double) radiusSq));
        if (noisy) {
            depth += hash(dx, dz, 0) % 3 - 1;
        }
        return Math.max(0, depth);
    }

    /** @return true for columns inside 35% of the radius, where the crater floor is melted */
    public boolean scorched(int dx, int dz) {
        long distanceSq = (long) dx * dx + (long) dz * dz;
        return distanceSq * 10_000L < 1_225L * radiusSq;
    }

    /** Deterministic non-negative hash of a column, the strike seed and a salt. */
    public int hash(int dx, int dz, int salt) {
        long h = seed ^ (dx * 0x9E3779B97F4A7C15L) ^ (dz * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (int) (h & 0x7FFFFFFFL);
    }
}
```

- [ ] **Step 4: Запустить тест**

Run: `./gradlew test --tests "*CraterProfileTest"`
Expected: PASS, 10 тестов.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add the crater profile

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Порядок чанков и бюджет тика

**Files:**
- Create: `src/main/java/moe/dexx/tacticaltablet/destruction/ChunkWaveOrder.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/destruction/TickTimeTracker.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/destruction/TickBudget.java`
- Test: `src/test/java/moe/dexx/tacticaltablet/destruction/ChunkWaveOrderTest.java`
- Test: `src/test/java/moe/dexx/tacticaltablet/destruction/TickBudgetTest.java`

**Interfaces:**
- Consumes: ничего.
- Produces:
  - `ChunkWaveOrder.compute(int centerBlockX, int centerBlockZ, int radius) -> long[]` — чанки, в которых есть хотя бы одна колонка зоны, по возрастанию расстояния до центра. Упаковка совпадает с `ChunkPos.asLong`.
  - `ChunkWaveOrder.pack(int chunkX, int chunkZ) -> long`, `chunkX(long) -> int`, `chunkZ(long) -> int`.
  - `TickTimeTracker`: `record(long nanos)`, `averageMs() -> double`, `maxMs() -> double`, `resetMax()`. Окно — 20 последних тиков.
  - `TickBudget(int budgetMs)`: `update(double averageTickMs) -> long` (наносекунды на этот тик), `currentNanos() -> long`. Константы `SLOW_MS = 60.0`, `RECOVER_MS = 45.0`, `MIN_NANOS = 2_000_000L`.

- [ ] **Step 1: Написать падающие тесты**

`src/test/java/moe/dexx/tacticaltablet/destruction/ChunkWaveOrderTest.java`:

```java
package moe.dexx.tacticaltablet.destruction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChunkWaveOrderTest {
    private static long nearestDistanceSq(long packed, int centerX, int centerZ) {
        int chunkX = ChunkWaveOrder.chunkX(packed);
        int chunkZ = ChunkWaveOrder.chunkZ(packed);
        long dx = Math.max(chunkX * 16, Math.min(centerX, chunkX * 16 + 15)) - centerX;
        long dz = Math.max(chunkZ * 16, Math.min(centerZ, chunkZ * 16 + 15)) - centerZ;
        return dx * dx + dz * dz;
    }

    @Test
    void packingMatchesTheVanillaLayoutAndSurvivesNegatives() {
        assertEquals(0L, ChunkWaveOrder.pack(0, 0));
        assertEquals(((long) 5 & 0xFFFFFFFFL) | (((long) -7 & 0xFFFFFFFFL) << 32), ChunkWaveOrder.pack(5, -7));
        long packed = ChunkWaveOrder.pack(-123456, 654321);
        assertEquals(-123456, ChunkWaveOrder.chunkX(packed));
        assertEquals(654321, ChunkWaveOrder.chunkZ(packed));
    }

    @Test
    void radiusOneInsideAChunkTouchesOnlyThatChunk() {
        assertArrayEquals(new long[] {ChunkWaveOrder.pack(0, 0)}, ChunkWaveOrder.compute(8, 8, 1));
    }

    @Test
    void radiusOneOnAChunkCornerTouchesThreeChunksButNotTheDiagonal() {
        long[] order = ChunkWaveOrder.compute(0, 0, 1);
        assertEquals(3, order.length);
        assertEquals(ChunkWaveOrder.pack(0, 0), order[0]);
        Set<Long> rest = Set.of(order[1], order[2]);
        assertTrue(rest.contains(ChunkWaveOrder.pack(-1, 0)));
        assertTrue(rest.contains(ChunkWaveOrder.pack(0, -1)));
    }

    @Test
    void negativeCoordinatesUseFloorDivision() {
        long[] order = ChunkWaveOrder.compute(-1, -1, 1);
        assertEquals(ChunkWaveOrder.pack(-1, -1), order[0]);
        assertEquals(3, order.length);
    }

    @Test
    void chunksComeInNonDecreasingDistanceFromTheCentre() {
        long[] order = ChunkWaveOrder.compute(37, -211, 300);
        long previous = -1;
        for (long packed : order) {
            long distance = nearestDistanceSq(packed, 37, -211);
            assertTrue(distance >= previous);
            assertTrue(distance <= 300L * 300L);
            previous = distance;
        }
    }

    @Test
    void maximumRadiusCoversAboutTwelveThousandUniqueChunks() {
        long[] order = ChunkWaveOrder.compute(0, 0, 1000);
        assertTrue(order.length > 12_000 && order.length < 13_200, "count=" + order.length);
        Set<Long> unique = new HashSet<>();
        for (long packed : order) {
            assertTrue(unique.add(packed));
        }
        assertEquals(ChunkWaveOrder.pack(0, 0), order[0]);
    }

    @Test
    void everyChunkWithAColumnInRangeIsIncluded() {
        long[] order = ChunkWaveOrder.compute(100, 100, 40);
        Set<Long> included = new HashSet<>();
        for (long packed : order) {
            included.add(packed);
        }
        for (int x = 60; x <= 140; x++) {
            for (int z = 60; z <= 140; z++) {
                long dx = x - 100;
                long dz = z - 100;
                if (dx * dx + dz * dz <= 1600) {
                    assertTrue(included.contains(ChunkWaveOrder.pack(Math.floorDiv(x, 16), Math.floorDiv(z, 16))));
                }
            }
        }
    }
}
```

`src/test/java/moe/dexx/tacticaltablet/destruction/TickBudgetTest.java`:

```java
package moe.dexx.tacticaltablet.destruction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TickBudgetTest {
    @Test
    void trackerAveragesTheLastTwentyTicks() {
        TickTimeTracker tracker = new TickTimeTracker();
        assertEquals(0.0, tracker.averageMs(), 1e-9);
        tracker.record(10_000_000L);
        tracker.record(30_000_000L);
        assertEquals(20.0, tracker.averageMs(), 1e-9);
        for (int i = 0; i < 20; i++) {
            tracker.record(50_000_000L);
        }
        assertEquals(50.0, tracker.averageMs(), 1e-9);
    }

    @Test
    void trackerRemembersTheWorstTickUntilReset() {
        TickTimeTracker tracker = new TickTimeTracker();
        tracker.record(10_000_000L);
        tracker.record(90_000_000L);
        tracker.record(20_000_000L);
        assertEquals(90.0, tracker.maxMs(), 1e-9);
        tracker.resetMax();
        assertEquals(0.0, tracker.maxMs(), 1e-9);
    }

    @Test
    void budgetStartsFull() {
        assertEquals(15_000_000L, new TickBudget(15).currentNanos());
    }

    @Test
    void budgetHalvesWhileTheServerIsSlowDownToTheFloor() {
        TickBudget budget = new TickBudget(16);
        assertEquals(8_000_000L, budget.update(61.0));
        assertEquals(4_000_000L, budget.update(75.0));
        assertEquals(2_000_000L, budget.update(75.0));
        assertEquals(2_000_000L, budget.update(200.0));
    }

    @Test
    void budgetHoldsBetweenTheThresholdsAndRecoversBelowTheLowerOne() {
        TickBudget budget = new TickBudget(16);
        budget.update(61.0);
        assertEquals(8_000_000L, budget.update(50.0));
        assertEquals(8_000_000L, budget.update(45.0));
        assertEquals(16_000_000L, budget.update(44.9));
    }

    @Test
    void budgetNeverGoesBelowTheFloorEvenWhenConfiguredLower() {
        assertEquals(2_000_000L, new TickBudget(0).currentNanos());
    }
}
```

- [ ] **Step 2: Убедиться, что тесты не компилируются**

Run: `./gradlew test --tests "*ChunkWaveOrderTest" --tests "*TickBudgetTest"`
Expected: FAIL, `cannot find symbol ... ChunkWaveOrder`.

- [ ] **Step 3: Реализовать**

`src/main/java/moe/dexx/tacticaltablet/destruction/ChunkWaveOrder.java`:

```java
package moe.dexx.tacticaltablet.destruction;

import java.util.Arrays;

/** Lists the chunks a strike touches, nearest to the target first, so destruction spreads as a wave. */
public final class ChunkWaveOrder {
    private ChunkWaveOrder() {
    }

    /** Same layout as ChunkPos.asLong. */
    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static int chunkX(long packed) {
        return (int) packed;
    }

    public static int chunkZ(long packed) {
        return (int) (packed >> 32);
    }

    public static long[] compute(int centerBlockX, int centerBlockZ, int radius) {
        int minChunkX = Math.floorDiv(centerBlockX - radius, 16);
        int maxChunkX = Math.floorDiv(centerBlockX + radius, 16);
        int minChunkZ = Math.floorDiv(centerBlockZ - radius, 16);
        int maxChunkZ = Math.floorDiv(centerBlockZ + radius, 16);
        int width = maxChunkX - minChunkX + 1;
        int depth = maxChunkZ - minChunkZ + 1;
        long radiusSq = (long) radius * radius;

        // Sort key: distance in the high bits, grid index in the low 24 bits (the grid has at most 127 * 127 cells).
        long[] keys = new long[width * depth];
        int count = 0;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                long dx = Math.max(chunkX * 16, Math.min(centerBlockX, chunkX * 16 + 15)) - centerBlockX;
                long dz = Math.max(chunkZ * 16, Math.min(centerBlockZ, chunkZ * 16 + 15)) - centerBlockZ;
                long distanceSq = dx * dx + dz * dz;
                if (distanceSq > radiusSq) {
                    continue;
                }
                int index = (chunkZ - minChunkZ) * width + (chunkX - minChunkX);
                keys[count++] = (distanceSq << 24) | index;
            }
        }
        long[] sorted = Arrays.copyOf(keys, count);
        Arrays.sort(sorted);

        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            int index = (int) (sorted[i] & 0xFFFFFFL);
            result[i] = pack(minChunkX + index % width, minChunkZ + index / width);
        }
        return result;
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/destruction/TickTimeTracker.java`:

```java
package moe.dexx.tacticaltablet.destruction;

/** Rolling average of the last 20 server tick durations, plus the worst tick since the last reset. */
public final class TickTimeTracker {
    private static final int WINDOW = 20;

    private final long[] samples = new long[WINDOW];
    private int count;
    private int index;
    private long sum;
    private long max;

    public void record(long nanos) {
        if (count == WINDOW) {
            sum -= samples[index];
        } else {
            count++;
        }
        samples[index] = nanos;
        sum += nanos;
        index = (index + 1) % WINDOW;
        max = Math.max(max, nanos);
    }

    public double averageMs() {
        return count == 0 ? 0.0 : sum / (double) count / 1_000_000.0;
    }

    public double maxMs() {
        return max / 1_000_000.0;
    }

    public void resetMax() {
        max = 0;
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/destruction/TickBudget.java`:

```java
package moe.dexx.tacticaltablet.destruction;

/** Time the destruction jobs may spend per tick; shrinks while the server is slow and recovers afterwards. */
public final class TickBudget {
    public static final double SLOW_MS = 60.0;
    public static final double RECOVER_MS = 45.0;
    public static final long MIN_NANOS = 2_000_000L;

    private final long fullNanos;
    private long currentNanos;

    public TickBudget(int budgetMs) {
        this.fullNanos = Math.max(MIN_NANOS, budgetMs * 1_000_000L);
        this.currentNanos = fullNanos;
    }

    public long update(double averageTickMs) {
        if (averageTickMs > SLOW_MS) {
            currentNanos = Math.max(MIN_NANOS, currentNanos / 2);
        } else if (averageTickMs < RECOVER_MS) {
            currentNanos = fullNanos;
        }
        return currentNanos;
    }

    public long currentNanos() {
        return currentNanos;
    }
}
```

- [ ] **Step 4: Запустить тесты**

Run: `./gradlew test --tests "*ChunkWaveOrderTest" --tests "*TickBudgetTest"`
Expected: PASS, 7 + 6 тестов.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add chunk wave ordering and the adaptive tick budget

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Серверный конфиг

**Files:**
- Create: `src/main/java/moe/dexx/tacticaltablet/config/ServerConfig.java`
- Test: `src/test/java/moe/dexx/tacticaltablet/config/ServerConfigTest.java`

**Interfaces:**
- Consumes: ничего.
- Produces: `ServerConfig` с публичными полями `maxRadius` (1000), `maxConcurrentStrikes` (2), `tickBudgetMs` (15), `maxForcedChunks` (16), `generateMissingChunks` (true), `allowedGameModes` (`survival`, `creative`, `adventure`) и методами
  - `static load(Path file) -> ServerConfig` — никогда не бросает исключений; создаёт файл с умолчаниями, если его нет,
  - `sanitize()`,
  - `allows(String gameModeName) -> boolean`.

- [ ] **Step 1: Написать падающий тест**

`src/test/java/moe/dexx/tacticaltablet/config/ServerConfigTest.java`:

```java
package moe.dexx.tacticaltablet.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerConfigTest {
    @TempDir
    Path dir;

    private ServerConfig loadFrom(String json) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return ServerConfig.load(file);
    }

    @Test
    void missingFileGivesDefaultsAndIsCreated() {
        Path file = dir.resolve("nested").resolve("config.json");
        ServerConfig config = ServerConfig.load(file);
        assertEquals(1000, config.maxRadius);
        assertEquals(2, config.maxConcurrentStrikes);
        assertEquals(15, config.tickBudgetMs);
        assertEquals(16, config.maxForcedChunks);
        assertTrue(config.generateMissingChunks);
        assertTrue(config.allows("survival"));
        assertTrue(config.allows("creative"));
        assertTrue(config.allows("adventure"));
        assertFalse(config.allows("spectator"));
        assertTrue(Files.exists(file));
    }

    @Test
    void valuesFromTheFileAreUsed() throws IOException {
        ServerConfig config = loadFrom("""
                {"maxRadius": 250, "maxConcurrentStrikes": 4, "tickBudgetMs": 8, "maxForcedChunks": 32,
                 "generateMissingChunks": false, "allowedGameModes": ["creative"]}
                """);
        assertEquals(250, config.maxRadius);
        assertEquals(4, config.maxConcurrentStrikes);
        assertEquals(8, config.tickBudgetMs);
        assertEquals(32, config.maxForcedChunks);
        assertFalse(config.generateMissingChunks);
        assertTrue(config.allows("creative"));
        assertFalse(config.allows("survival"));
    }

    @Test
    void absurdValuesAreBroughtIntoRange() throws IOException {
        ServerConfig config = loadFrom("""
                {"maxRadius": 5000, "maxConcurrentStrikes": 0, "tickBudgetMs": 900, "maxForcedChunks": -5}
                """);
        assertEquals(1000, config.maxRadius);
        assertEquals(1, config.maxConcurrentStrikes);
        assertEquals(40, config.tickBudgetMs);
        assertEquals(1, config.maxForcedChunks);
    }

    @Test
    void negativeRadiusBecomesOne() throws IOException {
        assertEquals(1, loadFrom("{\"maxRadius\": -10}").maxRadius);
    }

    @Test
    void brokenJsonGivesDefaults() throws IOException {
        ServerConfig config = loadFrom("{ this is not json");
        assertEquals(1000, config.maxRadius);
        assertTrue(config.allows("survival"));
    }

    @Test
    void wrongTypesGiveDefaults() throws IOException {
        ServerConfig config = loadFrom("{\"maxRadius\": \"lots\"}");
        assertEquals(1000, config.maxRadius);
    }

    @Test
    void nullOrEmptyGameModeListFallsBackToDefaults() throws IOException {
        assertTrue(loadFrom("{\"allowedGameModes\": null}").allows("survival"));
        assertTrue(loadFrom("{\"allowedGameModes\": [null, \"CREATIVE\"]}").allows("creative"));
    }

    @Test
    void emptyFileGivesDefaults() throws IOException {
        assertEquals(1000, loadFrom("").maxRadius);
    }
}
```

- [ ] **Step 2: Убедиться, что тест не компилируется**

Run: `./gradlew test --tests "*ServerConfigTest"`
Expected: FAIL, `cannot find symbol ... ServerConfig`.

- [ ] **Step 3: Реализовать конфиг**

`src/main/java/moe/dexx/tacticaltablet/config/ServerConfig.java`:

```java
package moe.dexx.tacticaltablet.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side limits. Loading never throws: a missing, broken or absurd file falls back to safe values. */
public final class ServerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("tactical_tablet");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int maxRadius = 1000;
    public int maxConcurrentStrikes = 2;
    public int tickBudgetMs = 15;
    public int maxForcedChunks = 16;
    public boolean generateMissingChunks = true;
    public List<String> allowedGameModes = defaultGameModes();

    private static List<String> defaultGameModes() {
        return new ArrayList<>(List.of("survival", "creative", "adventure"));
    }

    public static ServerConfig load(Path file) {
        ServerConfig config = null;
        if (Files.exists(file)) {
            try {
                config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ServerConfig.class);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not read {}, using defaults: {}", file, e.toString());
            }
        }
        boolean writeBack = config == null && !Files.exists(file);
        if (config == null) {
            config = new ServerConfig();
        }
        config.sanitize();
        if (writeBack) {
            config.save(file);
        }
        return config;
    }

    public void sanitize() {
        maxRadius = clamp(maxRadius, 1, 1000);
        maxConcurrentStrikes = clamp(maxConcurrentStrikes, 1, 16);
        tickBudgetMs = clamp(tickBudgetMs, 2, 40);
        maxForcedChunks = clamp(maxForcedChunks, 1, 256);
        List<String> modes = new ArrayList<>();
        if (allowedGameModes != null) {
            for (String mode : allowedGameModes) {
                if (mode != null && !mode.isBlank()) {
                    modes.add(mode.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        allowedGameModes = modes.isEmpty() ? defaultGameModes() : modes;
    }

    public boolean allows(String gameModeName) {
        return gameModeName != null && allowedGameModes.contains(gameModeName.toLowerCase(Locale.ROOT));
    }

    private void save(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
```

- [ ] **Step 4: Запустить тест**

Run: `./gradlew test --tests "*ServerConfigTest"`
Expected: PASS, 8 тестов.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add the server config with safe loading

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Предмет, ресурсы и инфраструктура игровых тестов

**Files:**
- Modify: `build.gradle` (набор исходников `gametest`, запуск `runGametest`)
- Modify: `src/main/java/moe/dexx/tacticaltablet/TacticalTablet.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/item/TacticalTabletItem.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/item/ModItems.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/item/TabletSettings.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/destruction/ModTags.java`
- Create: `src/main/resources/assets/tactical_tablet/lang/en_us.json`, `ru_ru.json`
- Create: `src/main/resources/assets/tactical_tablet/models/item/tactical_tablet.json`
- Create: `src/main/resources/data/tactical_tablet/recipes/tactical_tablet.json`
- Create: `src/main/resources/data/tactical_tablet/tags/blocks/strike_protected.json`
- Create: `src/gametest/resources/fabric.mod.json`
- Test: `src/gametest/java/moe/dexx/tacticaltablet/gametest/ItemGameTests.java`

**Interfaces:**
- Consumes: `TacticalTablet.id`, `StrikeParams`, `StrikeType` (задача 1).
- Produces:
  - `ModItems.TACTICAL_TABLET` (`Item`), `ModItems.TAB_KEY` (`ResourceKey<CreativeModeTab>`), `ModItems.register()`.
  - `TacticalTabletItem.clientUseHandler` — `public static Consumer<InteractionHand>`, по умолчанию пустой; план 2 присваивает сюда открытие экрана.
  - `TabletSettings.read(ItemStack) -> StrikeParams` (никогда не бросает исключений), `TabletSettings.write(ItemStack, StrikeParams)`, `TabletSettings.ROOT = "TabletSettings"`.
  - `ModTags.STRIKE_PROTECTED` (`TagKey<Block>`).
  - Задача Gradle `runGametest`: запускает сервер игровых тестов и завершается с ненулевым кодом при провале любого теста.

- [ ] **Step 1: Добавить набор исходников и запуск игровых тестов в `build.gradle`**

Вставить сразу после блока `base { … }` и перед блоком `loom { … }`:

```groovy
sourceSets {
    gametest {
        compileClasspath += sourceSets.main.compileClasspath + sourceSets.main.output
        runtimeClasspath += sourceSets.main.runtimeClasspath + sourceSets.main.output
    }
}
```

Блок `loom { … }` заменить целиком на:

```groovy
loom {
    splitEnvironmentSourceSets()

    mods {
        "tactical_tablet" {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
        "tactical_tablet_gametest" {
            sourceSet sourceSets.gametest
        }
    }

    runs {
        gametest {
            server()
            name "Game Test"
            source sourceSets.gametest
            vmArg "-Dfabric-api.gametest"
            vmArg "-Dfabric-api.gametest.report-file=reports/junit.xml"
            runDir "build/gametest"
        }
    }
}
```

- [ ] **Step 2: Написать метаданные тестового мода и падающие тесты**

`src/gametest/resources/fabric.mod.json`:

```json
{
  "schemaVersion": 1,
  "id": "tactical_tablet_gametest",
  "version": "1.0.0",
  "name": "Tactical Tablet Game Tests",
  "environment": "*",
  "entrypoints": {
    "fabric-gametest": [
      "moe.dexx.tacticaltablet.gametest.ItemGameTests"
    ]
  },
  "depends": {
    "tactical_tablet": "*",
    "fabric-gametest-api-v1": "*"
  }
}
```

`src/gametest/java/moe/dexx/tacticaltablet/gametest/ItemGameTests.java`:

```java
package moe.dexx.tacticaltablet.gametest;

import moe.dexx.tacticaltablet.destruction.ModTags;
import moe.dexx.tacticaltablet.item.ModItems;
import moe.dexx.tacticaltablet.item.TabletSettings;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class ItemGameTests implements FabricGameTest {
    private static final ResourceLocation TABLET_ID = new ResourceLocation("tactical_tablet", "tactical_tablet");

    @GameTest(template = EMPTY_STRUCTURE)
    public void itemIsRegisteredAndDoesNotStack(GameTestHelper helper) {
        Item item = BuiltInRegistries.ITEM.get(TABLET_ID);
        helper.assertTrue(item == ModItems.TACTICAL_TABLET, "the tablet item is not registered");
        helper.assertTrue(new ItemStack(item).getMaxStackSize() == 1, "the tablet must not stack");
        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.get(ModItems.TAB_KEY) != null, "the creative tab is missing");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeIsLoaded(GameTestHelper helper) {
        boolean present = helper.getLevel().getServer().getRecipeManager().byKey(TABLET_ID).isPresent();
        helper.assertTrue(present, "the crafting recipe did not load");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void protectedTagIsLoaded(GameTestHelper helper) {
        helper.assertTrue(Blocks.BEDROCK.defaultBlockState().is(ModTags.STRIKE_PROTECTED), "bedrock must be protected");
        helper.assertTrue(Blocks.END_PORTAL_FRAME.defaultBlockState().is(ModTags.STRIKE_PROTECTED), "end portal frame must be protected");
        helper.assertTrue(Blocks.END_GATEWAY.defaultBlockState().is(ModTags.STRIKE_PROTECTED), "end gateway must be protected");
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(ModTags.STRIKE_PROTECTED), "stone must not be protected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void freshTabletHasDefaultSettings(GameTestHelper helper) {
        StrikeParams params = TabletSettings.read(new ItemStack(ModItems.TACTICAL_TABLET));
        helper.assertTrue(params.equals(StrikeParams.DEFAULT), "a fresh tablet must report the defaults");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void settingsSurviveARoundTrip(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.TACTICAL_TABLET);
        StrikeParams written = new StrikeParams(true, -120, 71, 3456, 777, 9, 7, 42, StrikeType.VISUAL_ONLY, false, false, false);
        TabletSettings.write(stack, written);
        helper.assertTrue(written.equals(TabletSettings.read(stack)), "settings changed in a round trip");
        helper.assertTrue(written.equals(TabletSettings.read(stack.copy())), "settings were lost when the stack was copied");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void garbageNbtFallsBackToDefaults(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.TACTICAL_TABLET);
        CompoundTag tag = stack.getOrCreateTagElement(TabletSettings.ROOT);
        tag.putString("Radius", "huge");
        tag.putInt("Power", 9999);
        tag.putInt("Salvos", -4);
        tag.putString("Type", "NUKE");
        tag.putString("DestroyBlocks", "maybe");
        StrikeParams params = TabletSettings.read(stack);
        helper.assertTrue(params.radius() == 50, "a non-numeric radius must fall back to the default");
        helper.assertTrue(params.power() == 10, "power must be brought into range");
        helper.assertTrue(params.salvos() == 1, "salvos must be brought into range");
        helper.assertTrue(params.type() == StrikeType.ORBITAL_LASER, "an unknown type must fall back to the default");
        helper.assertTrue(params.destroyBlocks(), "a non-numeric flag must fall back to the default");
        helper.assertTrue(params.countdownSeconds() == 10, "a missing key must fall back to the default");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void oversizedRadiusInNbtIsBroughtIntoRange(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.TACTICAL_TABLET);
        stack.getOrCreateTagElement(TabletSettings.ROOT).putInt("Radius", 99_999);
        helper.assertTrue(TabletSettings.read(stack).radius() == 1000, "radius must not exceed 1000");
        helper.succeed();
    }
}
```

- [ ] **Step 3: Убедиться, что тесты не компилируются**

Run: `./gradlew compileGametestJava`
Expected: FAIL, `cannot find symbol ... ModItems`.

- [ ] **Step 4: Реализовать предмет, настройки и тег**

`src/main/java/moe/dexx/tacticaltablet/destruction/ModTags.java`:

```java
package moe.dexx.tacticaltablet.destruction;

import moe.dexx.tacticaltablet.TacticalTablet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Blocks a strike never removes. Extend it with a data pack. */
    public static final TagKey<Block> STRIKE_PROTECTED = TagKey.create(Registries.BLOCK, TacticalTablet.id("strike_protected"));

    private ModTags() {
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/item/TacticalTabletItem.java`:

```java
package moe.dexx.tacticaltablet.item;

import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TacticalTabletItem extends Item {
    /** Set by the client entrypoint; opens the tablet screen. Stays a no-op on a dedicated server. */
    public static Consumer<InteractionHand> clientUseHandler = hand -> {
    };

    public TacticalTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            clientUseHandler.accept(hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/item/ModItems.java`:

```java
package moe.dexx.tacticaltablet.item;

import moe.dexx.tacticaltablet.TacticalTablet;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    public static final Item TACTICAL_TABLET = new TacticalTabletItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TacticalTablet.id("main"));

    private ModItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, TacticalTablet.id("tactical_tablet"), TACTICAL_TABLET);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.tactical_tablet.main"))
                .icon(() -> new ItemStack(TACTICAL_TABLET))
                .displayItems((parameters, output) -> output.accept(TACTICAL_TABLET))
                .build());
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/item/TabletSettings.java`:

```java
package moe.dexx.tacticaltablet.item;

import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Strike settings stored on the tablet stack. Reading tolerates any NBT a player can produce with /give:
 * wrong types and missing keys fall back to the defaults, numbers are brought into range.
 */
public final class TabletSettings {
    public static final String ROOT = "TabletSettings";

    private TabletSettings() {
    }

    public static StrikeParams read(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(ROOT);
        if (tag == null) {
            return StrikeParams.DEFAULT;
        }
        StrikeParams defaults = StrikeParams.DEFAULT;
        return new StrikeParams(
                readBoolean(tag, "HasTarget", false),
                tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"),
                readInt(tag, "Radius", defaults.radius(), StrikeParams.MIN_RADIUS, StrikeParams.MAX_RADIUS),
                readInt(tag, "Power", defaults.power(), StrikeParams.MIN_POWER, StrikeParams.MAX_POWER),
                readInt(tag, "Salvos", defaults.salvos(), StrikeParams.MIN_SALVOS, StrikeParams.MAX_SALVOS),
                readInt(tag, "Countdown", defaults.countdownSeconds(), StrikeParams.MIN_COUNTDOWN, StrikeParams.MAX_COUNTDOWN),
                readType(tag, defaults.type()),
                readBoolean(tag, "DestroyBlocks", defaults.destroyBlocks()),
                readBoolean(tag, "DestroyLiquids", defaults.destroyLiquids()),
                readBoolean(tag, "DamageEntities", defaults.damageEntities()));
    }

    public static void write(ItemStack stack, StrikeParams params) {
        CompoundTag tag = stack.getOrCreateTagElement(ROOT);
        tag.putBoolean("HasTarget", params.hasTarget());
        tag.putInt("X", params.targetX());
        tag.putInt("Y", params.targetY());
        tag.putInt("Z", params.targetZ());
        tag.putInt("Radius", params.radius());
        tag.putInt("Power", params.power());
        tag.putInt("Salvos", params.salvos());
        tag.putInt("Countdown", params.countdownSeconds());
        tag.putString("Type", params.type().name());
        tag.putBoolean("DestroyBlocks", params.destroyBlocks());
        tag.putBoolean("DestroyLiquids", params.destroyLiquids());
        tag.putBoolean("DamageEntities", params.damageEntities());
    }

    private static int readInt(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    private static boolean readBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }

    private static StrikeType readType(CompoundTag tag, StrikeType fallback) {
        if (!tag.contains("Type", Tag.TAG_STRING)) {
            return fallback;
        }
        try {
            return StrikeType.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
```

В `TacticalTablet.onInitialize()` первой строкой добавить `ModItems.register();` и импорт `moe.dexx.tacticaltablet.item.ModItems`.

- [ ] **Step 5: Написать ресурсы**

`src/main/resources/assets/tactical_tablet/models/item/tactical_tablet.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/recovery_compass_16"
  }
}
```

`src/main/resources/data/tactical_tablet/recipes/tactical_tablet.json`:

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "equipment",
  "pattern": [
    "IGI",
    "RER",
    "IDI"
  ],
  "key": {
    "I": {"item": "minecraft:iron_ingot"},
    "G": {"item": "minecraft:glass_pane"},
    "R": {"item": "minecraft:redstone"},
    "E": {"item": "minecraft:ender_eye"},
    "D": {"item": "minecraft:diamond"}
  },
  "result": {
    "item": "tactical_tablet:tactical_tablet"
  }
}
```

`src/main/resources/data/tactical_tablet/tags/blocks/strike_protected.json`:

```json
{
  "replace": false,
  "values": [
    "minecraft:bedrock",
    "minecraft:barrier",
    "minecraft:command_block",
    "minecraft:chain_command_block",
    "minecraft:repeating_command_block",
    "minecraft:structure_block",
    "minecraft:jigsaw",
    "minecraft:end_portal_frame",
    "minecraft:end_portal",
    "minecraft:nether_portal",
    "minecraft:end_gateway"
  ]
}
```

`src/main/resources/assets/tactical_tablet/lang/en_us.json`:

```json
{
  "item.tactical_tablet.tactical_tablet": "Tactical Tablet",
  "itemGroup.tactical_tablet.main": "Tactical Tablet",
  "tactical_tablet.reject.no_target": "No target selected",
  "tactical_tablet.reject.target_height": "The target is outside the world height limits",
  "tactical_tablet.reject.target_border": "The target is outside the world border",
  "tactical_tablet.reject.radius": "The radius is outside the allowed range",
  "tactical_tablet.reject.power": "Strike power must be between 1 and 10",
  "tactical_tablet.reject.salvos": "The number of salvos must be between 1 and 10",
  "tactical_tablet.reject.countdown": "The countdown must be between 3 and 60 seconds",
  "tactical_tablet.reject.type": "Unknown strike type",
  "tactical_tablet.reject.already_active": "You already have an active strike",
  "tactical_tablet.reject.server_busy": "Too many strikes are in progress on this server",
  "tactical_tablet.reject.no_tablet": "Hold the Tactical Tablet to do this",
  "tactical_tablet.reject.gamemode": "Your game mode does not allow launching a strike",
  "tactical_tablet.reject.cannot_cancel": "The launch can no longer be cancelled",
  "tactical_tablet.reject.nothing_to_stop": "There is nothing to stop",
  "tactical_tablet.abort.cancelled": "Launch cancelled",
  "tactical_tablet.abort.player_left": "Launch aborted: the operator left the world",
  "tactical_tablet.abort.player_unavailable": "Launch aborted: the operator died or changed dimension",
  "tactical_tablet.abort.target_unavailable": "Launch aborted: the target is no longer available",
  "tactical_tablet.abort.gamemode": "Launch aborted: the game mode no longer allows the strike",
  "tactical_tablet.abort.server_stopping": "Launch aborted: the server is stopping",
  "tactical_tablet.abort.resources": "Launch aborted: the cinematic resources failed to load",
  "tactical_tablet.abort.stopped_by_operator": "Launch aborted by a server operator"
}
```

`src/main/resources/assets/tactical_tablet/lang/ru_ru.json`:

```json
{
  "item.tactical_tablet.tactical_tablet": "Тактический планшет",
  "itemGroup.tactical_tablet.main": "Тактический планшет",
  "tactical_tablet.reject.no_target": "Цель не выбрана",
  "tactical_tablet.reject.target_height": "Цель вне диапазона высот мира",
  "tactical_tablet.reject.target_border": "Цель за границей мира",
  "tactical_tablet.reject.radius": "Радиус вне допустимого диапазона",
  "tactical_tablet.reject.power": "Сила удара должна быть от 1 до 10",
  "tactical_tablet.reject.salvos": "Число залпов должно быть от 1 до 10",
  "tactical_tablet.reject.countdown": "Обратный отсчёт должен быть от 3 до 60 секунд",
  "tactical_tablet.reject.type": "Неизвестный тип удара",
  "tactical_tablet.reject.already_active": "У вас уже есть активный удар",
  "tactical_tablet.reject.server_busy": "На сервере выполняется слишком много ударов",
  "tactical_tablet.reject.no_tablet": "Для этого нужно держать тактический планшет",
  "tactical_tablet.reject.gamemode": "Ваш режим игры не позволяет запустить удар",
  "tactical_tablet.reject.cannot_cancel": "Запуск уже нельзя отменить",
  "tactical_tablet.reject.nothing_to_stop": "Останавливать нечего",
  "tactical_tablet.abort.cancelled": "Запуск отменён",
  "tactical_tablet.abort.player_left": "Запуск отменён: оператор вышел из мира",
  "tactical_tablet.abort.player_unavailable": "Запуск отменён: оператор погиб или сменил измерение",
  "tactical_tablet.abort.target_unavailable": "Запуск отменён: цель стала недоступна",
  "tactical_tablet.abort.gamemode": "Запуск отменён: режим игры больше не позволяет удар",
  "tactical_tablet.abort.server_stopping": "Запуск отменён: сервер останавливается",
  "tactical_tablet.abort.resources": "Запуск отменён: не удалось загрузить ресурсы кат-сцены",
  "tactical_tablet.abort.stopped_by_operator": "Запуск отменён оператором сервера"
}
```

- [ ] **Step 6: Запустить игровые тесты**

Run: `./gradlew runGametest`
Expected: в выводе `All 7 required tests passed :)`, задача завершается успешно. Первый запуск создаёт тестовый мир в `build/gametest`.

- [ ] **Step 7: Проверить, что набор ключей в языковых файлах совпадает**

```bash
cd /c/Users/pozo/Projects/tactical-tablet
python -c "import json,io; a=json.load(io.open('src/main/resources/assets/tactical_tablet/lang/en_us.json',encoding='utf-8')); b=json.load(io.open('src/main/resources/assets/tactical_tablet/lang/ru_ru.json',encoding='utf-8')); print(sorted(set(a)^set(b)))"
```

Expected: `[]`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Add the tablet item, its settings, recipe, tag and game test setup

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Задание разрушения

**Files:**
- Create: `src/main/java/moe/dexx/tacticaltablet/destruction/DestructionJob.java`
- Create: `src/gametest/java/moe/dexx/tacticaltablet/gametest/TestArena.java`
- Test: `src/gametest/java/moe/dexx/tacticaltablet/gametest/DestructionGameTests.java`
- Modify: `src/gametest/resources/fabric.mod.json` (добавить класс в `fabric-gametest`)

**Interfaces:**
- Consumes: `CraterProfile` (задача 3), `ChunkWaveOrder` (задача 4), `ServerConfig` (задача 5), `ModTags.STRIKE_PROTECTED` (задача 6), `StrikeParams` (задача 1).
- Produces: `DestructionJob(ServerLevel level, UUID strikeId, BlockPos target, StrikeParams params, ServerConfig config)` с методами
  - `tick(long deadlineNanos) -> boolean` — работает до дедлайна (значение `System.nanoTime()`), возвращает `true`, когда делать больше нечего; вызывается только из потока сервера,
  - `stop()` — снимает все тикеты и завершает задание,
  - `isFinished() -> boolean`, `chunksDone() -> int`, `chunksTotal() -> int`, `pendingChunks() -> int`, `blocksRemoved() -> long`.
  - `TestArena` (только в тестах): `buildSlab(GameTestHelper)`, `target(GameTestHelper) -> BlockPos`, `assertSlabIntact(GameTestHelper)`, `isMelted(Block) -> boolean`.

Почему запись сделана именно так (проверено по исходникам 1.20.1):
- `LevelChunk.setBlockState` обновляет карты высот, источники небесного света и ставит пересчёт света в очередь, но не трогает соседей и не уведомляет клиентов.
- Он вызывает `onRemove` старого блока, а контейнеры в `onRemove` выбрасывают содержимое. Поэтому перед заменой содержимое очищается через `Clearable.tryClear` — как это делают `/fill` и `/setblock`.
- Клиенты узнают об изменении через `ServerChunkCache.blockChanged`; свет клиент 1.20 пересчитывает сам. `blockChanged` работает только для тикающих чанков, поэтому не тикающим, но видимым чанкам отправляется чанк целиком.
- `onPlace` магмы и огня сохраняет позицию в планировщике тиков, поэтому в запись передаётся неизменяемая копия позиции.

- [ ] **Step 1: Написать тестовую арену и падающие тесты**

`src/gametest/java/moe/dexx/tacticaltablet/gametest/TestArena.java`:

```java
package moe.dexx.tacticaltablet.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** An 8x8 stone slab four blocks thick (relative y 1..4) with the strike target on top of its centre. */
final class TestArena {
    static final int BOTTOM = 1;
    static final int TOP = 4;
    static final int CENTER = 4;

    private TestArena() {
    }

    static void buildSlab(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                for (int y = BOTTOM; y <= TOP; y++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
    }

    static BlockPos target(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(CENTER, TOP, CENTER));
    }

    static void assertSlabIntact(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                for (int y = BOTTOM; y <= TOP; y++) {
                    helper.assertBlockPresent(Blocks.STONE, x, y, z);
                }
            }
        }
    }

    static boolean isMelted(Block block) {
        return block == Blocks.BLACKSTONE || block == Blocks.BASALT || block == Blocks.MAGMA_BLOCK;
    }
}
```

`src/gametest/java/moe/dexx/tacticaltablet/gametest/DestructionGameTests.java`:

```java
package moe.dexx.tacticaltablet.gametest;

import java.util.UUID;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.destruction.DestructionJob;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class DestructionGameTests implements FabricGameTest {
    private static final long FIVE_SECONDS = 5_000_000_000L;

    private static StrikeParams params(BlockPos target, boolean destroyLiquids, boolean damageEntities) {
        return new StrikeParams(true, target.getX(), target.getY(), target.getZ(), 3, 5, 1, 3,
                StrikeType.ORBITAL_LASER, true, destroyLiquids, damageEntities);
    }

    private static DestructionJob job(GameTestHelper helper, boolean destroyLiquids, boolean damageEntities) {
        BlockPos target = TestArena.target(helper);
        return new DestructionJob(helper.getLevel(), UUID.randomUUID(), target,
                params(target, destroyLiquids, damageEntities), new ServerConfig());
    }

    /** Ticks the job every game tick until it reports completion, then runs the assertions. */
    private static void whenFinished(GameTestHelper helper, DestructionJob job, Runnable assertions) {
        helper.succeedWhen(() -> {
            if (!job.tick(System.nanoTime() + FIVE_SECONDS)) {
                throw new GameTestAssertException("the destruction job is still running");
            }
            assertions.run();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void craterMatchesTheProfile(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            // Centre and its four neighbours: two blocks removed, a melted floor block underneath.
            for (int[] offset : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = 4 + offset[0];
                int z = 4 + offset[1];
                helper.assertBlockPresent(Blocks.AIR, x, 4, z);
                helper.assertBlock(new BlockPos(x, 3, z), block -> block == Blocks.AIR || block == Blocks.FIRE,
                        "expected air or fire above the melted floor");
                helper.assertBlock(new BlockPos(x, 2, z), TestArena::isMelted, "expected a melted floor block");
                helper.assertBlockPresent(Blocks.STONE, x, 1, z);
            }
            // Diagonal neighbour: depth 2, outside the melted zone.
            helper.assertBlockPresent(Blocks.AIR, 5, 4, 5);
            helper.assertBlockPresent(Blocks.AIR, 5, 3, 5);
            helper.assertBlockPresent(Blocks.STONE, 5, 2, 5);
            // Distance 2 and (2,1): depth 1.
            helper.assertBlockPresent(Blocks.AIR, 6, 4, 4);
            helper.assertBlockPresent(Blocks.STONE, 6, 3, 4);
            helper.assertBlockPresent(Blocks.AIR, 6, 4, 5);
            helper.assertBlockPresent(Blocks.STONE, 6, 3, 5);
            // (2,2), distance 3 and the far corner: untouched.
            helper.assertBlockPresent(Blocks.STONE, 6, 4, 6);
            helper.assertBlockPresent(Blocks.STONE, 7, 4, 4);
            helper.assertBlockPresent(Blocks.STONE, 1, 4, 4);
            helper.assertBlockPresent(Blocks.STONE, 0, 4, 0);
            helper.assertTrue(job.chunksDone() == job.chunksTotal(), "not every chunk was processed");
            helper.assertTrue(job.blocksRemoved() > 0, "no blocks were counted as removed");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void protectedBlocksSurvive(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        helper.setBlock(4, 4, 4, Blocks.BEDROCK);
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            helper.assertBlockPresent(Blocks.BEDROCK, 4, 4, 4);
            helper.assertBlockPresent(Blocks.AIR, 4, 3, 4);
            helper.assertBlockPresent(Blocks.AIR, 5, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void liquidsAreKeptWhenDisabled(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        BlockState waterlogged = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        helper.setBlock(4, 5, 4, waterlogged);
        DestructionJob job = job(helper, false, true);
        whenFinished(helper, job, () -> {
            helper.assertBlockPresent(Blocks.OAK_SLAB, 4, 5, 4);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void liquidsAreRemovedWhenEnabled(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        BlockState waterlogged = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        helper.setBlock(4, 5, 4, waterlogged);
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            helper.assertBlockPresent(Blocks.AIR, 4, 5, 4);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void entitiesInsideTheZoneVanishWithoutDrops(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 5, 4));
        ItemEntity inside = helper.spawnItem(Items.DIAMOND, 4.5F, 5.0F, 5.5F);
        ItemEntity outside = helper.spawnItem(Items.EMERALD, 0.5F, 5.0F, 0.5F);
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            helper.assertTrue(pig.isRemoved(), "the pig inside the zone must vanish");
            helper.assertTrue(inside.isRemoved(), "the item inside the zone must vanish");
            helper.assertFalse(outside.isRemoved(), "the item outside the radius must stay");
            helper.assertItemEntityNotPresent(Items.PORKCHOP, new BlockPos(4, 4, 4), 8.0);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void entitiesAreSparedWhenDisabled(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 5, 4));
        DestructionJob job = job(helper, true, false);
        whenFinished(helper, job, () -> helper.assertFalse(pig.isRemoved(), "entities must be spared when damage is off"));
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void containersDoNotSpillTheirContents(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        helper.setBlock(4, 4, 4, Blocks.CHEST);
        ((net.minecraft.world.Container) helper.getBlockEntity(new BlockPos(4, 4, 4)))
                .setItem(0, new net.minecraft.world.item.ItemStack(Items.GOLD_INGOT, 64));
        DestructionJob job = job(helper, true, true);
        whenFinished(helper, job, () -> {
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
            helper.assertItemEntityNotPresent(Items.GOLD_INGOT, new BlockPos(4, 4, 4), 8.0);
            helper.assertItemEntityNotPresent(Items.CHEST, new BlockPos(4, 4, 4), 8.0);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void skyLightReachesTheCraterFloor(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        DestructionJob job = job(helper, true, true);
        helper.assertTrue(job.tick(System.nanoTime() + FIVE_SECONDS), "a four-chunk job must finish in one tick");
        BlockPos floorAir = helper.absolutePos(new BlockPos(4, 3, 4));
        helper.runAfterDelay(40, () -> {
            int light = helper.getLevel().getBrightness(LightLayer.SKY, floorAir);
            helper.assertTrue(light == 15, "sky light above the crater floor is " + light + ", expected 15");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void overlappingJobsBothFinish(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        DestructionJob first = job(helper, true, true);
        DestructionJob second = job(helper, true, true);
        helper.succeedWhen(() -> {
            boolean firstDone = first.tick(System.nanoTime() + FIVE_SECONDS);
            boolean secondDone = second.tick(System.nanoTime() + FIVE_SECONDS);
            if (!firstDone || !secondDone) {
                throw new GameTestAssertException("a job is still running");
            }
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
            helper.assertTrue(first.pendingChunks() == 0 && second.pendingChunks() == 0, "a job still holds chunks");
            helper.assertTrue(first.chunksDone() == first.chunksTotal(), "the first job skipped chunks");
            helper.assertTrue(second.chunksDone() == second.chunksTotal(), "the second job skipped chunks");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void stopReleasesEverything(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        // Far from every test structure and never generated, so the first tick only requests chunks.
        BlockPos target = TestArena.target(helper).offset(100_000, 0, 0);
        StrikeParams params = new StrikeParams(true, target.getX(), target.getY(), target.getZ(), 200, 5, 1, 3,
                StrikeType.ORBITAL_LASER, true, true, true);
        DestructionJob job = new DestructionJob(helper.getLevel(), UUID.randomUUID(), target, params, config);
        helper.assertFalse(job.tick(System.nanoTime()), "a 200-block job cannot finish in one tick");
        helper.assertTrue(job.pendingChunks() == config.maxForcedChunks, "the window must be filled up to the limit");
        job.stop();
        helper.assertTrue(job.pendingChunks() == 0, "stop() must release every chunk");
        helper.assertTrue(job.isFinished(), "a stopped job must report completion");
        helper.assertTrue(job.tick(System.nanoTime() + FIVE_SECONDS), "ticking a stopped job must be a no-op");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void ungeneratedChunksAreSkippedWhenGenerationIsOff(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        config.generateMissingChunks = false;
        BlockPos target = TestArena.target(helper).offset(200_000, 0, 0);
        StrikeParams params = params(target, true, true);
        DestructionJob job = new DestructionJob(helper.getLevel(), UUID.randomUUID(), target, params, config);
        whenFinished(helper, job, () -> {
            helper.assertTrue(job.blocksRemoved() == 0, "nothing may be removed in chunks that were never generated");
            helper.assertTrue(job.chunksDone() == job.chunksTotal(), "skipped chunks must still be counted as done");
        });
    }
}
```

В `src/gametest/resources/fabric.mod.json` список `fabric-gametest` становится:

```json
    "fabric-gametest": [
      "moe.dexx.tacticaltablet.gametest.ItemGameTests",
      "moe.dexx.tacticaltablet.gametest.DestructionGameTests"
    ]
```

- [ ] **Step 2: Убедиться, что тесты не компилируются**

Run: `./gradlew compileGametestJava`
Expected: FAIL, `cannot find symbol ... DestructionJob`.

- [ ] **Step 3: Реализовать задание**

`src/main/java/moe/dexx/tacticaltablet/destruction/DestructionJob.java`:

```java
package moe.dexx.tacticaltablet.destruction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Digs the crater chunk by chunk, nearest to the target first. Call tick() once per server tick with a deadline;
 * the job resumes where it stopped. At most maxForcedChunks chunks are held by tickets at any time.
 * Server thread only.
 */
public final class DestructionJob {
    public static final TicketType<UUID> TICKET =
            TicketType.create("tactical_tablet_strike", Comparator.<UUID>naturalOrder());
    public static final int MAX_FIRES = 512;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    private final UUID strikeId;
    private final BlockPos target;
    private final StrikeParams params;
    private final ServerConfig config;
    private final CraterProfile profile;
    private final long[] order;
    private final int minY;
    private final List<Slot> window = new ArrayList<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private int nextToQueue;
    private int chunksDone;
    private int fires;
    private long blocksRemoved;
    private boolean stopped;

    private static final class Slot {
        final ChunkPos pos;
        boolean ticketed;
        /** Pending "does this chunk exist on disk" lookup; only used when generation of new chunks is off. */
        CompletableFuture<Boolean> existsProbe;
        boolean entitiesDone;
        boolean changed;
        int nextColumn;

        Slot(ChunkPos pos) {
            this.pos = pos;
        }
    }

    public DestructionJob(ServerLevel level, UUID strikeId, BlockPos target, StrikeParams params, ServerConfig config) {
        this.level = level;
        this.strikeId = strikeId;
        this.target = target.immutable();
        this.params = params;
        this.config = config;
        this.profile = new CraterProfile(params.radius(), params.power(),
                strikeId.getMostSignificantBits() ^ strikeId.getLeastSignificantBits());
        this.order = ChunkWaveOrder.compute(target.getX(), target.getZ(), params.radius());
        this.minY = level.getMinBuildHeight();
    }

    /**
     * @param deadlineNanos a System.nanoTime() value after which the job yields
     * @return true when there is nothing left to do
     */
    public boolean tick(long deadlineNanos) {
        if (stopped) {
            return true;
        }
        refillWindow();
        Iterator<Slot> iterator = window.iterator();
        while (iterator.hasNext()) {
            Slot slot = iterator.next();
            if (advance(slot, deadlineNanos)) {
                release(slot);
                iterator.remove();
                chunksDone++;
            }
            if (expired(deadlineNanos)) {
                break;
            }
        }
        refillWindow();
        return isFinished();
    }

    public void stop() {
        stopped = true;
        for (Slot slot : window) {
            release(slot);
        }
        window.clear();
    }

    public boolean isFinished() {
        return stopped || (nextToQueue >= order.length && window.isEmpty());
    }

    public int chunksDone() {
        return chunksDone;
    }

    public int chunksTotal() {
        return order.length;
    }

    public int pendingChunks() {
        return window.size();
    }

    public long blocksRemoved() {
        return blocksRemoved;
    }

    private static boolean expired(long deadlineNanos) {
        return System.nanoTime() - deadlineNanos >= 0;
    }

    private void refillWindow() {
        while (window.size() < config.maxForcedChunks && nextToQueue < order.length) {
            long packed = order[nextToQueue++];
            ChunkPos pos = new ChunkPos(ChunkWaveOrder.chunkX(packed), ChunkWaveOrder.chunkZ(packed));
            if (!level.getWorldBorder().isWithinBounds(pos)) {
                chunksDone++;
                continue;
            }
            Slot slot = new Slot(pos);
            if (config.generateMissingChunks || level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                addTicket(slot);
            } else {
                slot.existsProbe = level.getChunkSource().chunkMap.read(pos)
                        .thenApply(tag -> tag.isPresent()
                                && ChunkSerializer.getChunkTypeFromTag(tag.get()) == ChunkStatus.ChunkType.LEVELCHUNK)
                        .exceptionally(error -> false);
            }
            window.add(slot);
        }
    }

    private void addTicket(Slot slot) {
        level.getChunkSource().addRegionTicket(TICKET, slot.pos, 0, strikeId);
        slot.ticketed = true;
    }

    private void release(Slot slot) {
        if (slot.ticketed) {
            level.getChunkSource().removeRegionTicket(TICKET, slot.pos, 0, strikeId);
            slot.ticketed = false;
        }
    }

    /** @return true when the slot is finished and can leave the window */
    private boolean advance(Slot slot, long deadlineNanos) {
        if (slot.existsProbe != null) {
            if (!slot.existsProbe.isDone()) {
                return false;
            }
            boolean exists = slot.existsProbe.getNow(false);
            slot.existsProbe = null;
            if (!exists) {
                return true;
            }
            addTicket(slot);
            return false;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(slot.pos.x, slot.pos.z);
        if (chunk == null) {
            return false;
        }
        if (!slot.entitiesDone) {
            if (params.damageEntities()) {
                hurtEntities(chunk);
            }
            slot.entitiesDone = true;
        }
        while (slot.nextColumn < 256) {
            processColumn(chunk, slot, slot.nextColumn & 15, slot.nextColumn >> 4);
            slot.nextColumn++;
            if ((slot.nextColumn & 15) == 0 && expired(deadlineNanos)) {
                break;
            }
        }
        if (slot.nextColumn < 256) {
            return false;
        }
        finishChunk(chunk, slot);
        return true;
    }

    private void processColumn(LevelChunk chunk, Slot slot, int localX, int localZ) {
        int x = slot.pos.getMinBlockX() + localX;
        int z = slot.pos.getMinBlockZ() + localZ;
        int dx = x - target.getX();
        int dz = z - target.getZ();
        int depth = profile.depthAt(dx, dz);
        if (depth < 1) {
            return;
        }
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        int ground = findGround(chunk, x, z, top);
        if (ground < minY) {
            return;
        }
        int floor = CraterProfile.floorY(ground, depth, minY);
        for (int y = top; y > floor; y--) {
            cursor.set(x, y, z);
            BlockState old = chunk.getBlockState(cursor);
            if (old.isAir() || old.is(ModTags.STRIKE_PROTECTED)) {
                continue;
            }
            if (!params.destroyLiquids() && !old.getFluidState().isEmpty()) {
                continue;
            }
            replace(chunk, cursor.immutable(), old, AIR);
            slot.changed = true;
            blocksRemoved++;
        }
        if (profile.scorched(dx, dz)) {
            scorch(chunk, slot, x, floor, z, dx, dz);
        }
    }

    /** @return y of the highest block that blocks motion and is not a leaf or a log, or minY - 1 when there is none */
    private int findGround(LevelChunk chunk, int x, int z, int top) {
        for (int y = top; y >= minY; y--) {
            BlockState state = chunk.getBlockState(cursor.set(x, y, z));
            if (state.blocksMotion() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
                return y;
            }
        }
        return minY - 1;
    }

    private void replace(LevelChunk chunk, BlockPos pos, BlockState old, BlockState replacement) {
        if (old.hasBlockEntity()) {
            // Containers spill their contents from onRemove; empty them first so no item entities appear.
            Clearable.tryClear(chunk.getBlockEntity(pos));
        }
        chunk.setBlockState(pos, replacement, false);
        level.getChunkSource().blockChanged(pos);
        if (PoiTypes.hasPoi(old)) {
            level.onBlockStateChange(pos, old, replacement);
        }
    }

    private void scorch(LevelChunk chunk, Slot slot, int x, int floor, int z, int dx, int dz) {
        BlockPos floorPos = new BlockPos(x, floor, z);
        BlockState base = chunk.getBlockState(floorPos);
        if (base.isAir() || base.is(ModTags.STRIKE_PROTECTED) || !base.getFluidState().isEmpty() || !base.blocksMotion()) {
            return;
        }
        int roll = profile.hash(dx, dz, 1) % 100;
        Block melted = roll < 60 ? Blocks.BLACKSTONE : roll < 85 ? Blocks.BASALT : Blocks.MAGMA_BLOCK;
        replace(chunk, floorPos, base, melted.defaultBlockState());
        slot.changed = true;

        if (fires < MAX_FIRES && profile.hash(dx, dz, 2) % 40 == 0 && floor + 1 < level.getMaxBuildHeight()) {
            BlockPos firePos = floorPos.above();
            if (chunk.getBlockState(firePos).isAir()) {
                chunk.setBlockState(firePos, Blocks.FIRE.defaultBlockState(), false);
                level.getChunkSource().blockChanged(firePos);
                fires++;
            }
        }
    }

    private void hurtEntities(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        AABB box = new AABB(pos.getMinBlockX(), minY, pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxBuildHeight() + 64, pos.getMaxBlockZ() + 1);
        List<Entity> entities = level.getEntities((Entity) null, box, entity -> true);
        for (Entity entity : entities) {
            int x = entity.getBlockX();
            int z = entity.getBlockZ();
            if ((x >> 4) != pos.x || (z >> 4) != pos.z) {
                continue; // handled by the chunk that owns its column
            }
            int dx = x - target.getX();
            int dz = z - target.getZ();
            int depth = profile.depthAt(dx, dz);
            if (depth < 1) {
                continue;
            }
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            int ground = Math.max(minY, findGround(chunk, x, z, top));
            int floor = CraterProfile.floorY(ground, depth, minY);
            if (entity.getY() < floor - 1) {
                continue; // deep below the future crater floor
            }
            if (entity instanceof Player player) {
                double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
                float damage = (float) (40.0 * (1.0 - distance / params.radius()));
                if (damage > 0.0F) {
                    player.hurt(level.damageSources().explosion(null, null), damage);
                }
            } else {
                entity.discard();
            }
        }
    }

    private void finishChunk(LevelChunk chunk, Slot slot) {
        if (!slot.changed) {
            return;
        }
        // The queued light updates fix the light while the chunk stays loaded. If it unloads before the light
        // thread catches up, this flag makes the game relight it from scratch on the next load.
        chunk.setLightCorrect(false);
        chunk.setUnsaved(true);
        if (!level.shouldTickBlocksAt(slot.pos.toLong())) {
            // blockChanged() only reaches clients for ticking chunks; edge-of-view chunks get the whole chunk.
            List<ServerPlayer> watchers = level.getChunkSource().chunkMap.getPlayers(slot.pos, false);
            if (!watchers.isEmpty()) {
                ClientboundLevelChunkWithLightPacket packet =
                        new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
                for (ServerPlayer watcher : watchers) {
                    watcher.connection.send(packet);
                }
            }
        }
    }
}
```

- [ ] **Step 4: Запустить игровые тесты**

Run: `./gradlew runGametest`
Expected: `All 18 required tests passed :)` (7 из задачи 6 и 11 новых).

Если `skyLightReachesTheCraterFloor` падает со значением меньше 15, это означает, что очередь света не дошла до чанка: проверить в `LevelChunk.setBlockState` справочных исходников условие `LightEngine.hasDifferentLightProperties` и убедиться, что `replace` вызывается с настоящим старым состоянием блока.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add the chunk-by-chunk destruction job

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Менеджер ударов

**Files:**
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/AbortReason.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/Strike.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/StrikeSummary.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/StrikeManager.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/PlayerOwnerProbe.java`
- Modify: `src/main/java/moe/dexx/tacticaltablet/TacticalTablet.java`
- Test: `src/gametest/java/moe/dexx/tacticaltablet/gametest/StrikeGameTests.java`
- Modify: `src/gametest/resources/fabric.mod.json`

**Interfaces:**
- Consumes: `StrikeParams`, `StrikePhase`, `StrikeTimeline`, `DestructionJob`, `TickTimeTracker`, `TickBudget`, `ServerConfig`, `TestArena`.
- Produces:
  - `AbortReason`: строковые константы `CANCELLED`, `PLAYER_LEFT`, `PLAYER_UNAVAILABLE`, `TARGET_UNAVAILABLE`, `GAMEMODE`, `SERVER_STOPPING`, `RESOURCES`, `STOPPED_BY_OPERATOR` (ключи `tactical_tablet.abort.*`).
  - `Strike` с геттерами `id()`, `owner()`, `dimension()`, `target()`, `params()`, `phase()`, `phaseStartTick()`, `job()`.
  - `record StrikeSummary(UUID owner, int chunksDone, int chunksTotal, long blocksRemoved, long wallMillis)`.
  - `StrikeManager(MinecraftServer, ServerConfig, OwnerProbe, Listener, TickTimeTracker)`:
    - `launch(UUID owner, ServerLevel level, StrikeParams params) -> String` (ключ отказа или `null`),
    - `launch(UUID owner, ServerLevel level, StrikeParams params, StrikePhase initialPhase) -> String`,
    - `cancel(UUID owner, String reasonKey) -> boolean`,
    - `emergencyStop(UUID owner) -> boolean`,
    - `forceStop(UUID owner) -> boolean`, `forceStopAll() -> int`,
    - `tick()`, `shutdown()`,
    - `strikeOf(UUID owner) -> Strike` (или `null`), `active() -> Collection<Strike>`, `lastSummary() -> StrikeSummary` (или `null`),
    - константы `REJECT_ALREADY_ACTIVE`, `REJECT_SERVER_BUSY`, `REJECT_TARGET_BORDER`.
  - `StrikeManager.Listener { onState(Strike); onAbort(Strike, String reasonKey); onProgress(Strike, int done, int total); }` и `Listener.NONE`.
  - `StrikeManager.OwnerProbe { String abortReason(MinecraftServer server, Strike strike); }`.
  - `PlayerOwnerProbe(ServerConfig)` — реализация `OwnerProbe` для настоящих игроков.
  - `TacticalTablet.manager() -> StrikeManager` (`null`, пока сервер не запущен), `TacticalTablet.config() -> ServerConfig`, `TacticalTablet.tickTimes() -> TickTimeTracker`.

Тайминги в тестах (запуск на тике 0 теста, отсчёт 3 с, 1 залп): `COUNTDOWN` 0–60, `CHARGE` 60–460, `TRAVEL` 460–540, `IMPACT` 540–660.

- [ ] **Step 1: Написать падающие тесты**

`src/gametest/java/moe/dexx/tacticaltablet/gametest/StrikeGameTests.java`:

```java
package moe.dexx.tacticaltablet.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.destruction.TickTimeTracker;
import moe.dexx.tacticaltablet.item.ModItems;
import moe.dexx.tacticaltablet.strike.AbortReason;
import moe.dexx.tacticaltablet.strike.PlayerOwnerProbe;
import moe.dexx.tacticaltablet.strike.Strike;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class StrikeGameTests implements FabricGameTest {
    private static final class Recorder implements StrikeManager.Listener {
        final List<StrikePhase> phases = new ArrayList<>();
        final List<String> aborts = new ArrayList<>();

        @Override
        public void onState(Strike strike) {
            phases.add(strike.phase());
        }

        @Override
        public void onAbort(Strike strike, String reasonKey) {
            aborts.add(reasonKey);
        }

        @Override
        public void onProgress(Strike strike, int done, int total) {
        }
    }

    private static final StrikeManager.OwnerProbe ALWAYS_AVAILABLE = (server, strike) -> null;

    private static StrikeManager manager(GameTestHelper helper, ServerConfig config, StrikeManager.OwnerProbe probe, Recorder recorder) {
        return new StrikeManager(helper.getLevel().getServer(), config, probe, recorder, new TickTimeTracker());
    }

    private static StrikeParams quick(GameTestHelper helper, StrikeType type) {
        BlockPos target = TestArena.target(helper);
        return new StrikeParams(true, target.getX(), target.getY(), target.getZ(), 3, 5, 1, 3, type, true, true, true);
    }

    private static void assertLaunched(GameTestHelper helper, String reject) {
        helper.assertTrue(reject == null, "the launch was rejected: " + reject);
    }

    private static void whenStrikeIsOver(GameTestHelper helper, StrikeManager manager, UUID owner, Runnable assertions) {
        helper.succeedWhen(() -> {
            if (manager.strikeOf(owner) != null) {
                throw new GameTestAssertException("the strike is still active");
            }
            assertions.run();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void visualOnlyStrikeLeavesTheWorldUntouched(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.VISUAL_ONLY)));
        helper.onEachTick(manager::tick);
        whenStrikeIsOver(helper, manager, owner, () -> {
            List<StrikePhase> expected = List.of(StrikePhase.COUNTDOWN, StrikePhase.CHARGE, StrikePhase.TRAVEL,
                    StrikePhase.IMPACT, StrikePhase.DONE);
            helper.assertTrue(recorder.phases.equals(expected), "phases were " + recorder.phases);
            helper.assertTrue(recorder.aborts.isEmpty(), "unexpected aborts " + recorder.aborts);
            TestArena.assertSlabIntact(helper);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void orbitalStrikeDigsTheCraterOnlyAtImpact(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(500, () -> TestArena.assertSlabIntact(helper));
        whenStrikeIsOver(helper, manager, owner, () -> {
            helper.assertTrue(recorder.phases.contains(StrikePhase.IMPACT), "phases were " + recorder.phases);
            helper.assertTrue(recorder.phases.get(recorder.phases.size() - 1) == StrikePhase.DONE, "phases were " + recorder.phases);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
            helper.assertTrue(manager.lastSummary() != null && manager.lastSummary().blocksRemoved() > 0, "no summary was recorded");
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void cancelBeforeTheShotLeavesTheWorldUntouched(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(manager.strikeOf(owner).phase() == StrikePhase.CHARGE, "expected the charge phase");
            helper.assertTrue(manager.cancel(owner, AbortReason.CANCELLED), "cancel before the shot must succeed");
            helper.assertTrue(recorder.aborts.equals(List.of(AbortReason.CANCELLED)), "aborts were " + recorder.aborts);
            helper.assertTrue(manager.strikeOf(owner) == null, "a cancelled strike must be gone");
        });
        helper.runAtTickTime(700, () -> {
            TestArena.assertSlabIntact(helper);
            helper.assertFalse(recorder.phases.contains(StrikePhase.TRAVEL), "a cancelled strike must never fire");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void cancelAfterTheShotIsRefused(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(480, () -> {
            helper.assertTrue(manager.strikeOf(owner).phase() == StrikePhase.TRAVEL, "expected the travel phase");
            helper.assertFalse(manager.cancel(owner, AbortReason.CANCELLED), "cancel after the shot must be refused");
            helper.assertFalse(manager.emergencyStop(owner), "there is no destruction to stop during travel");
            helper.assertTrue(manager.strikeOf(owner) != null, "the strike must still be active");
        });
        whenStrikeIsOver(helper, manager, owner, () -> {
            helper.assertTrue(recorder.aborts.isEmpty(), "unexpected aborts " + recorder.aborts);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void secondLaunchAndServerLimitAreRejected(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        config.maxConcurrentStrikes = 1;
        StrikeManager manager = manager(helper, config, ALWAYS_AVAILABLE, new Recorder());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        StrikeParams params = quick(helper, StrikeType.VISUAL_ONLY);
        assertLaunched(helper, manager.launch(first, helper.getLevel(), params));
        helper.assertTrue(StrikeManager.REJECT_ALREADY_ACTIVE.equals(manager.launch(first, helper.getLevel(), params)),
                "a second launch by the same owner must be rejected");
        helper.assertTrue(StrikeManager.REJECT_SERVER_BUSY.equals(manager.launch(second, helper.getLevel(), params)),
                "a launch above the server limit must be rejected");
        helper.assertTrue(manager.active().size() == 1, "exactly one strike must be active");
        helper.assertTrue(manager.cancel(first, AbortReason.CANCELLED), "cancel must succeed");
        assertLaunched(helper, manager.launch(second, helper.getLevel(), params));
        helper.assertTrue(manager.cancel(second, AbortReason.CANCELLED), "cancel must succeed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void radiusAboveTheLimitIsRejected(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        StrikeManager manager = manager(helper, config, ALWAYS_AVAILABLE, new Recorder());
        UUID owner = UUID.randomUUID();
        StrikeParams params = quick(helper, StrikeType.VISUAL_ONLY);
        helper.assertTrue(StrikeParams.REJECT_RADIUS.equals(manager.launch(owner, helper.getLevel(), params.withRadius(1001))),
                "radius 1001 must be rejected");
        config.maxRadius = 100;
        helper.assertTrue(StrikeParams.REJECT_RADIUS.equals(manager.launch(owner, helper.getLevel(), params.withRadius(101))),
                "a radius above the server limit must be rejected");
        helper.assertTrue(manager.active().isEmpty(), "rejected launches must not create strikes");
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), params.withRadius(100)));
        helper.assertTrue(manager.cancel(owner, AbortReason.CANCELLED), "cancel must succeed");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void targetOutsideWorldBorderRejected(GameTestHelper helper) {
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, new Recorder());
        StrikeParams params = quick(helper, StrikeType.VISUAL_ONLY).withTarget(40_000_000, 64, 0);
        helper.assertTrue(StrikeManager.REJECT_TARGET_BORDER.equals(manager.launch(UUID.randomUUID(), helper.getLevel(), params)),
                "a target outside the world border must be rejected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void ownerLeavingBeforeTheShotAbortsTheStrike(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        AtomicReference<String> problem = new AtomicReference<>();
        StrikeManager manager = manager(helper, new ServerConfig(), (server, strike) -> problem.get(), recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(100, () -> problem.set(AbortReason.PLAYER_LEFT));
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(recorder.aborts.equals(List.of(AbortReason.PLAYER_LEFT)), "aborts were " + recorder.aborts);
            helper.assertTrue(manager.strikeOf(owner) == null, "an aborted strike must be gone");
        });
        helper.runAtTickTime(700, () -> {
            TestArena.assertSlabIntact(helper);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void ownerLeavingAfterTheShotDoesNotStopTheStrike(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        AtomicReference<String> problem = new AtomicReference<>();
        StrikeManager manager = manager(helper, new ServerConfig(), (server, strike) -> problem.get(), recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(480, () -> problem.set(AbortReason.PLAYER_LEFT));
        whenStrikeIsOver(helper, manager, owner, () -> {
            helper.assertTrue(recorder.aborts.isEmpty(), "a fired strike must not abort: " + recorder.aborts);
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800)
    public void operatorStopDuringTravelPreventsDestruction(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.onEachTick(manager::tick);
        helper.runAtTickTime(480, () -> {
            helper.assertTrue(manager.forceStop(owner), "an operator stop must find the strike");
            helper.assertTrue(manager.strikeOf(owner) == null, "a stopped strike must be gone");
            helper.assertFalse(manager.forceStop(owner), "there is nothing left to stop");
        });
        helper.runAtTickTime(700, () -> {
            TestArena.assertSlabIntact(helper);
            helper.assertTrue(recorder.phases.get(recorder.phases.size() - 1) == StrikePhase.DONE, "phases were " + recorder.phases);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void immediateStrikeStartsAtImpact(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER), StrikePhase.IMPACT));
        manager.tick();
        helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
        helper.assertTrue(recorder.phases.get(0) == StrikePhase.IMPACT, "phases were " + recorder.phases);
        helper.assertTrue(manager.forceStopAll() == 1, "one strike must be stopped");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void shutdownAbortsPendingStrikes(GameTestHelper helper) {
        Recorder recorder = new Recorder();
        StrikeManager manager = manager(helper, new ServerConfig(), ALWAYS_AVAILABLE, recorder);
        UUID owner = UUID.randomUUID();
        assertLaunched(helper, manager.launch(owner, helper.getLevel(), quick(helper, StrikeType.ORBITAL_LASER)));
        helper.assertFalse(manager.emergencyStop(owner), "there is no destruction to stop during the countdown");
        manager.shutdown();
        helper.assertTrue(recorder.aborts.equals(List.of(AbortReason.SERVER_STOPPING)), "aborts were " + recorder.aborts);
        helper.assertTrue(manager.active().isEmpty(), "shutdown must clear every strike");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void probeReportsOwnerProblems(GameTestHelper helper) {
        ServerConfig config = new ServerConfig();
        PlayerOwnerProbe probe = new PlayerOwnerProbe(config);
        StrikeManager manager = manager(helper, config, probe, new Recorder());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.TACTICAL_TABLET));
            assertLaunched(helper, manager.launch(player.getUUID(), helper.getLevel(), quick(helper, StrikeType.VISUAL_ONLY)));
            Strike strike = manager.strikeOf(player.getUUID());
            helper.assertTrue(probe.abortReason(helper.getLevel().getServer(), strike) == null, "a healthy owner must pass");
            player.setGameMode(GameType.SPECTATOR);
            helper.assertTrue(AbortReason.GAMEMODE.equals(probe.abortReason(helper.getLevel().getServer(), strike)),
                    "a spectator owner must abort the strike");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        Strike orphan = manager.strikeOf(player.getUUID());
        helper.assertTrue(AbortReason.PLAYER_LEFT.equals(probe.abortReason(helper.getLevel().getServer(), orphan)),
                "a missing owner must abort the strike");
        helper.succeed();
    }
}
```

В `src/gametest/resources/fabric.mod.json` добавить в список `fabric-gametest` строку `"moe.dexx.tacticaltablet.gametest.StrikeGameTests"`.

- [ ] **Step 2: Убедиться, что тесты не компилируются**

Run: `./gradlew compileGametestJava`
Expected: FAIL, `cannot find symbol ... StrikeManager`.

- [ ] **Step 3: Реализовать состояние удара**

`src/main/java/moe/dexx/tacticaltablet/strike/AbortReason.java`:

```java
package moe.dexx.tacticaltablet.strike;

/** Translation keys shown to the owner when a launch is cancelled before the shot. */
public final class AbortReason {
    public static final String CANCELLED = "tactical_tablet.abort.cancelled";
    public static final String PLAYER_LEFT = "tactical_tablet.abort.player_left";
    public static final String PLAYER_UNAVAILABLE = "tactical_tablet.abort.player_unavailable";
    public static final String TARGET_UNAVAILABLE = "tactical_tablet.abort.target_unavailable";
    public static final String GAMEMODE = "tactical_tablet.abort.gamemode";
    public static final String SERVER_STOPPING = "tactical_tablet.abort.server_stopping";
    public static final String RESOURCES = "tactical_tablet.abort.resources";
    public static final String STOPPED_BY_OPERATOR = "tactical_tablet.abort.stopped_by_operator";

    private AbortReason() {
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/strike/StrikeSummary.java`:

```java
package moe.dexx.tacticaltablet.strike;

import java.util.UUID;

public record StrikeSummary(UUID owner, int chunksDone, int chunksTotal, long blocksRemoved, long wallMillis) {
}
```

`src/main/java/moe/dexx/tacticaltablet/strike/Strike.java`:

```java
package moe.dexx.tacticaltablet.strike;

import java.util.UUID;
import moe.dexx.tacticaltablet.destruction.DestructionJob;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** One strike in progress. Mutated only by StrikeManager on the server thread. */
public final class Strike {
    private final UUID id;
    private final UUID owner;
    private final ResourceKey<Level> dimension;
    private final BlockPos target;
    private final StrikeParams params;

    StrikePhase phase = StrikePhase.COUNTDOWN;
    long phaseStartTick;
    long lastProgressTick;
    long jobStartNanos;
    DestructionJob job;

    Strike(UUID id, UUID owner, ResourceKey<Level> dimension, BlockPos target, StrikeParams params) {
        this.id = id;
        this.owner = owner;
        this.dimension = dimension;
        this.target = target;
        this.params = params;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos target() {
        return target;
    }

    public StrikeParams params() {
        return params;
    }

    public StrikePhase phase() {
        return phase;
    }

    /** Game time (Level.getGameTime) at which the current phase began. */
    public long phaseStartTick() {
        return phaseStartTick;
    }

    /** The destruction job, or null before impact and for strikes that do not modify the world. */
    public DestructionJob job() {
        return job;
    }
}
```

- [ ] **Step 4: Реализовать менеджер и проверку владельца**

`src/main/java/moe/dexx/tacticaltablet/strike/StrikeManager.java`:

```java
package moe.dexx.tacticaltablet.strike;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.destruction.DestructionJob;
import moe.dexx.tacticaltablet.destruction.TickBudget;
import moe.dexx.tacticaltablet.destruction.TickTimeTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Owns every strike on the server: phases, limits, cancellation and the destruction jobs. Server thread only. */
public final class StrikeManager {
    public static final String REJECT_ALREADY_ACTIVE = "tactical_tablet.reject.already_active";
    public static final String REJECT_SERVER_BUSY = "tactical_tablet.reject.server_busy";
    public static final String REJECT_TARGET_BORDER = "tactical_tablet.reject.target_border";

    private static final int PROGRESS_INTERVAL_TICKS = 20;

    public interface Listener {
        Listener NONE = new Listener() {
            @Override
            public void onState(Strike strike) {
            }

            @Override
            public void onAbort(Strike strike, String reasonKey) {
            }

            @Override
            public void onProgress(Strike strike, int done, int total) {
            }
        };

        /** A strike was created or entered a new phase. */
        void onState(Strike strike);

        /** A strike was cancelled before the shot. */
        void onAbort(Strike strike, String reasonKey);

        void onProgress(Strike strike, int done, int total);
    }

    public interface OwnerProbe {
        /** @return an AbortReason key when the owner can no longer run this strike, otherwise null */
        String abortReason(MinecraftServer server, Strike strike);
    }

    private final MinecraftServer server;
    private final ServerConfig config;
    private final OwnerProbe probe;
    private final Listener listener;
    private final TickTimeTracker tickTimes;
    private final TickBudget budget;
    private final Map<UUID, Strike> strikes = new LinkedHashMap<>();
    private StrikeSummary lastSummary;

    public StrikeManager(MinecraftServer server, ServerConfig config, OwnerProbe probe, Listener listener, TickTimeTracker tickTimes) {
        this.server = server;
        this.config = config;
        this.probe = probe;
        this.listener = listener;
        this.tickTimes = tickTimes;
        this.budget = new TickBudget(config.tickBudgetMs);
    }

    public String launch(UUID owner, ServerLevel level, StrikeParams params) {
        return launch(owner, level, params, StrikePhase.COUNTDOWN);
    }

    /** @return the translation key of the reason the launch was refused, or null when the strike started */
    public String launch(UUID owner, ServerLevel level, StrikeParams params, StrikePhase initialPhase) {
        String reject = params.validate(config.maxRadius, level.getMinBuildHeight(), level.getMaxBuildHeight());
        if (reject != null) {
            return reject;
        }
        BlockPos target = new BlockPos(params.targetX(), params.targetY(), params.targetZ());
        if (!level.getWorldBorder().isWithinBounds(target)) {
            return REJECT_TARGET_BORDER;
        }
        if (strikes.containsKey(owner)) {
            return REJECT_ALREADY_ACTIVE;
        }
        if (strikes.size() >= config.maxConcurrentStrikes) {
            return REJECT_SERVER_BUSY;
        }
        Strike strike = new Strike(UUID.randomUUID(), owner, level.dimension(), target, params);
        strikes.put(owner, strike);
        enter(strike, level, initialPhase, level.getGameTime());
        return null;
    }

    /** Cancels a strike that has not fired yet. */
    public boolean cancel(UUID owner, String reasonKey) {
        Strike strike = strikes.get(owner);
        if (strike == null || !strike.phase.cancellable()) {
            return false;
        }
        strikes.remove(owner);
        listener.onAbort(strike, reasonKey);
        return true;
    }

    /** Lets the owner halt a destruction job that is still running. What is already destroyed stays destroyed. */
    public boolean emergencyStop(UUID owner) {
        Strike strike = strikes.get(owner);
        if (strike == null || strike.job == null || strike.job.isFinished()) {
            return false;
        }
        strikes.remove(owner);
        complete(strike);
        return true;
    }

    /** Operator stop: works in any phase. */
    public boolean forceStop(UUID owner) {
        Strike strike = strikes.remove(owner);
        if (strike == null) {
            return false;
        }
        if (strike.phase.cancellable()) {
            listener.onAbort(strike, AbortReason.STOPPED_BY_OPERATOR);
        } else {
            complete(strike);
        }
        return true;
    }

    public int forceStopAll() {
        List<UUID> owners = new ArrayList<>(strikes.keySet());
        int stopped = 0;
        for (UUID owner : owners) {
            if (forceStop(owner)) {
                stopped++;
            }
        }
        return stopped;
    }

    /** Server is stopping: strikes that have not fired are aborted, running jobs are dropped. */
    public void shutdown() {
        for (Strike strike : new ArrayList<>(strikes.values())) {
            if (strike.phase.cancellable()) {
                listener.onAbort(strike, AbortReason.SERVER_STOPPING);
            } else {
                complete(strike);
            }
        }
        strikes.clear();
    }

    public Strike strikeOf(UUID owner) {
        return strikes.get(owner);
    }

    public Collection<Strike> active() {
        return Collections.unmodifiableCollection(strikes.values());
    }

    public StrikeSummary lastSummary() {
        return lastSummary;
    }

    public void tick() {
        if (strikes.isEmpty()) {
            return;
        }
        long budgetNanos = budget.update(tickTimes.averageMs());
        int runningJobs = 0;
        for (Strike strike : strikes.values()) {
            if (strike.job != null && !strike.job.isFinished()) {
                runningJobs++;
            }
        }
        long share = budgetNanos / Math.max(1, runningJobs);

        Iterator<Strike> iterator = strikes.values().iterator();
        while (iterator.hasNext()) {
            Strike strike = iterator.next();
            ServerLevel level = server.getLevel(strike.dimension());
            if (strike.phase.cancellable()) {
                String reason = level == null ? AbortReason.TARGET_UNAVAILABLE : abortReason(level, strike);
                if (reason != null) {
                    iterator.remove();
                    listener.onAbort(strike, reason);
                    continue;
                }
            }
            if (level == null) {
                iterator.remove();
                complete(strike);
                continue;
            }
            advance(strike, level);
            if (strike.job != null && !strike.job.isFinished()) {
                strike.job.tick(System.nanoTime() + share);
                long now = level.getGameTime();
                if (now - strike.lastProgressTick >= PROGRESS_INTERVAL_TICKS) {
                    strike.lastProgressTick = now;
                    listener.onProgress(strike, strike.job.chunksDone(), strike.job.chunksTotal());
                }
            }
            if (strike.phase == StrikePhase.DESTROYING && strike.job.isFinished()) {
                enter(strike, level, StrikePhase.DONE, level.getGameTime());
            }
            if (strike.phase == StrikePhase.DONE) {
                iterator.remove();
            }
        }
    }

    private String abortReason(ServerLevel level, Strike strike) {
        if (!level.getWorldBorder().isWithinBounds(strike.target())) {
            return AbortReason.TARGET_UNAVAILABLE;
        }
        return probe.abortReason(server, strike);
    }

    private void advance(Strike strike, ServerLevel level) {
        long now = level.getGameTime();
        int duration = StrikeTimeline.durationTicks(strike.phase, strike.params());
        while (duration >= 0 && now - strike.phaseStartTick >= duration) {
            boolean jobRunning = strike.job != null && !strike.job.isFinished();
            StrikePhase next = StrikeTimeline.next(strike.phase, jobRunning);
            enter(strike, level, next, strike.phaseStartTick + duration);
            duration = StrikeTimeline.durationTicks(strike.phase, strike.params());
        }
    }

    private void enter(Strike strike, ServerLevel level, StrikePhase phase, long startTick) {
        strike.phase = phase;
        strike.phaseStartTick = startTick;
        if (phase == StrikePhase.IMPACT && strike.params().modifiesWorld() && strike.job == null) {
            strike.job = new DestructionJob(level, strike.id(), strike.target(), strike.params(), config);
            strike.jobStartNanos = System.nanoTime();
        }
        if (phase == StrikePhase.DONE) {
            closeJob(strike);
        }
        listener.onState(strike);
    }

    /** Ends a strike that is no longer in the map: stops its job and announces DONE. */
    private void complete(Strike strike) {
        strike.phase = StrikePhase.DONE;
        closeJob(strike);
        listener.onState(strike);
    }

    private void closeJob(Strike strike) {
        if (strike.job == null) {
            return;
        }
        strike.job.stop();
        long wallMillis = (System.nanoTime() - strike.jobStartNanos) / 1_000_000L;
        lastSummary = new StrikeSummary(strike.owner(), strike.job.chunksDone(), strike.job.chunksTotal(),
                strike.job.blocksRemoved(), wallMillis);
        TacticalTablet.LOGGER.info("Strike by {} finished: {}/{} chunks, {} blocks removed, {} ms",
                strike.owner(), lastSummary.chunksDone(), lastSummary.chunksTotal(), lastSummary.blocksRemoved(), wallMillis);
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/strike/PlayerOwnerProbe.java`:

```java
package moe.dexx.tacticaltablet.strike;

import moe.dexx.tacticaltablet.config.ServerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Checks every tick that the player who launched a strike can still own it. */
public final class PlayerOwnerProbe implements StrikeManager.OwnerProbe {
    private final ServerConfig config;

    public PlayerOwnerProbe(ServerConfig config) {
        this.config = config;
    }

    @Override
    public String abortReason(MinecraftServer server, Strike strike) {
        ServerPlayer player = server.getPlayerList().getPlayer(strike.owner());
        if (player == null) {
            return AbortReason.PLAYER_LEFT;
        }
        if (!player.isAlive() || player.level().dimension() != strike.dimension()) {
            return AbortReason.PLAYER_UNAVAILABLE;
        }
        if (!config.allows(player.gameMode.getGameModeForPlayer().getName())) {
            return AbortReason.GAMEMODE;
        }
        return null;
    }
}
```

- [ ] **Step 5: Подключить менеджер к жизненному циклу сервера**

`src/main/java/moe/dexx/tacticaltablet/TacticalTablet.java` целиком:

```java
package moe.dexx.tacticaltablet;

import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.destruction.TickTimeTracker;
import moe.dexx.tacticaltablet.item.ModItems;
import moe.dexx.tacticaltablet.strike.PlayerOwnerProbe;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TacticalTablet implements ModInitializer {
    public static final String MOD_ID = "tactical_tablet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final TickTimeTracker TICK_TIMES = new TickTimeTracker();
    private static ServerConfig config = new ServerConfig();
    private static StrikeManager manager;
    private static long tickStartNanos;

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static ServerConfig config() {
        return config;
    }

    /** @return the manager of the running server, or null while no server is running */
    public static StrikeManager manager() {
        return manager;
    }

    public static TickTimeTracker tickTimes() {
        return TICK_TIMES;
    }

    @Override
    public void onInitialize() {
        ModItems.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            config = ServerConfig.load(FabricLoader.getInstance().getConfigDir().resolve("tactical_tablet-server.json"));
            manager = new StrikeManager(server, config, new PlayerOwnerProbe(config), StrikeManager.Listener.NONE, TICK_TIMES);
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (manager != null) {
                manager.tick();
            }
            TICK_TIMES.record(System.nanoTime() - tickStartNanos);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (manager != null) {
                manager.shutdown();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> manager = null);

        LOGGER.info("Tactical Tablet loaded");
    }
}
```

- [ ] **Step 6: Запустить все тесты**

Run: `./gradlew test runGametest`
Expected: юнит-тесты проходят; `All 31 required tests passed :)` (18 прежних и 13 новых).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Add the strike manager with phases, limits and cancellation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Сеть

**Files:**
- Create: `src/main/java/moe/dexx/tacticaltablet/net/ModPackets.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/net/StrikeState.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/net/StrikeCodecs.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/net/ServerNetworking.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/strike/PlayerChecks.java`
- Modify: `src/main/java/moe/dexx/tacticaltablet/TacticalTablet.java`
- Test: `src/test/java/moe/dexx/tacticaltablet/net/StrikeCodecsTest.java`
- Test: `src/gametest/java/moe/dexx/tacticaltablet/gametest/NetworkGameTests.java`
- Modify: `src/gametest/resources/fabric.mod.json`

**Interfaces:**
- Consumes: `StrikeManager`, `Strike`, `StrikeParams`, `StrikePhase`, `StrikeTimeline`, `TabletSettings`, `ModItems`, `ServerConfig`, `AbortReason`.
- Produces:
  - `ModPackets`: `UPDATE_SETTINGS`, `LAUNCH_REQUEST`, `CANCEL_REQUEST`, `EMERGENCY_STOP` (клиент → сервер); `LAUNCH_REJECTED`, `STRIKE_STATE`, `STRIKE_ABORT`, `STRIKE_PROGRESS` (сервер → клиент) — все `ResourceLocation`.
  - `record StrikeState(UUID id, UUID owner, StrikePhase phase, long phaseStartTick, int durationTicks, BlockPos target, int radius, int salvos, StrikeType type, boolean modifiesWorld)` и `StrikeState.of(Strike)`.
  - `StrikeCodecs.writeParams/readParams(FriendlyByteBuf, …)`, `writeState/readState`.
  - `PlayerChecks.heldTablet(Player) -> ItemStack` (пустой стак, если планшета в руках нет), `PlayerChecks.launchReject(ServerPlayer, ServerConfig) -> String`, константы `REJECT_NO_TABLET`, `REJECT_GAMEMODE`.
  - `ServerNetworking.register()`, `ServerNetworking.listener(MinecraftServer) -> StrikeManager.Listener`, `ServerNetworking.handleLaunch(ServerPlayer, StrikeParams)`, `handleUpdateSettings(ServerPlayer, StrikeParams)`, `handleCancel(ServerPlayer, boolean resourceFailure)`, `handleEmergencyStop(ServerPlayer)`.

Формат пакетов (план 2 читает их на клиенте теми же кодеками):

| Пакет | Содержимое |
|---|---|
| `update_settings`, `launch_request` | `StrikeCodecs.writeParams` |
| `cancel_request` | `boolean resourceFailure` |
| `emergency_stop` | пусто |
| `launch_rejected` | `String` ключ причины |
| `strike_state` | `StrikeCodecs.writeState` |
| `strike_abort` | `UUID` удара, `String` ключ причины |
| `strike_progress` | `UUID` удара, `varint` обработано, `varint` всего |

- [ ] **Step 1: Написать падающие тесты**

`src/test/java/moe/dexx/tacticaltablet/net/StrikeCodecsTest.java`:

```java
package moe.dexx.tacticaltablet.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class StrikeCodecsTest {
    @Test
    void paramsSurviveARoundTrip() {
        StrikeParams params = new StrikeParams(true, -30_000_000, -64, 29_999_999, 1000, 10, 10, 60,
                StrikeType.VISUAL_ONLY, false, true, false);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        StrikeCodecs.writeParams(buf, params);
        assertEquals(params, StrikeCodecs.readParams(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void outOfRangeNumbersAreCarriedThroughForTheValidatorToReject() {
        StrikeParams hostile = new StrikeParams(true, 0, 64, 0, Integer.MAX_VALUE, -5, 0, 100_000,
                StrikeType.ORBITAL_LASER, true, true, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        StrikeCodecs.writeParams(buf, hostile);
        StrikeParams read = StrikeCodecs.readParams(buf);
        assertEquals(hostile, read);
        assertEquals(StrikeParams.REJECT_RADIUS, read.validate(1000, -64, 320));
    }

    @Test
    void stateSurvivesARoundTrip() {
        StrikeState state = new StrikeState(UUID.randomUUID(), UUID.randomUUID(), StrikePhase.DESTROYING, 123_456_789_012L, -1,
                new BlockPos(-1234, 70, 5678), 1000, 7, StrikeType.ORBITAL_LASER, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        StrikeCodecs.writeState(buf, state);
        assertEquals(state, StrikeCodecs.readState(buf));
        assertEquals(0, buf.readableBytes());
    }
}
```

`src/gametest/java/moe/dexx/tacticaltablet/gametest/NetworkGameTests.java`:

```java
package moe.dexx.tacticaltablet.gametest;

import java.util.List;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.item.ModItems;
import moe.dexx.tacticaltablet.item.TabletSettings;
import moe.dexx.tacticaltablet.net.ServerNetworking;
import moe.dexx.tacticaltablet.strike.PlayerChecks;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

public class NetworkGameTests implements FabricGameTest {
    private static void remove(GameTestHelper helper, ServerPlayer player) {
        if (TacticalTablet.manager() != null) {
            TacticalTablet.manager().forceStop(player.getUUID());
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static StrikeParams aimed(GameTestHelper helper) {
        BlockPos target = TestArena.target(helper);
        return new StrikeParams(true, target.getX(), target.getY(), target.getZ(), 3, 5, 1, 3,
                StrikeType.VISUAL_ONLY, true, true, true);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void launchRequiresTheTabletAndAnAllowedGameMode(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ServerConfig config = new ServerConfig();
            player.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(PlayerChecks.REJECT_NO_TABLET.equals(PlayerChecks.launchReject(player, config)),
                    "a launch without the tablet must be rejected");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.TACTICAL_TABLET));
            helper.assertTrue(PlayerChecks.launchReject(player, config) == null, "the off hand must count");
            config.allowedGameModes = List.of("creative");
            helper.assertTrue(PlayerChecks.REJECT_GAMEMODE.equals(PlayerChecks.launchReject(player, config)),
                    "a disallowed game mode must be rejected");
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(PlayerChecks.launchReject(player, config) == null, "an allowed game mode must pass");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void launchRequestStartsAStrikeAndSavesTheSettings(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            ItemStack tablet = new ItemStack(ModItems.TACTICAL_TABLET);
            player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
            StrikeParams params = aimed(helper);
            ServerNetworking.handleLaunch(player, params);
            helper.assertTrue(TacticalTablet.manager().strikeOf(player.getUUID()) != null, "the strike did not start");
            helper.assertTrue(TacticalTablet.manager().strikeOf(player.getUUID()).phase() == StrikePhase.COUNTDOWN,
                    "a launched strike must begin with the countdown");
            helper.assertTrue(params.equals(TabletSettings.read(tablet)), "the settings were not saved on the tablet");

            ServerNetworking.handleLaunch(player, params);
            helper.assertTrue(TacticalTablet.manager().active().stream().filter(s -> s.owner().equals(player.getUUID())).count() == 1,
                    "a second request must not start a second strike");

            ServerNetworking.handleCancel(player, false);
            helper.assertTrue(TacticalTablet.manager().strikeOf(player.getUUID()) == null, "the cancel request was ignored");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void requestsWithoutTheTabletDoNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            ServerNetworking.handleLaunch(player, aimed(helper));
            helper.assertTrue(TacticalTablet.manager().strikeOf(player.getUUID()) == null,
                    "a launch without the tablet must not start a strike");
            ServerNetworking.handleUpdateSettings(player, aimed(helper));
            ServerNetworking.handleCancel(player, true);
            ServerNetworking.handleEmergencyStop(player);
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void invalidSettingsAreNotSaved(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack tablet = new ItemStack(ModItems.TACTICAL_TABLET);
            player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
            ServerNetworking.handleUpdateSettings(player, StrikeParams.DEFAULT.withRadius(5000));
            helper.assertTrue(StrikeParams.DEFAULT.equals(TabletSettings.read(tablet)), "invalid settings must not be stored");
            ServerNetworking.handleUpdateSettings(player, StrikeParams.DEFAULT.withRadius(250));
            helper.assertTrue(TabletSettings.read(tablet).radius() == 250, "valid settings must be stored");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }
}
```

В `src/gametest/resources/fabric.mod.json` добавить в список `fabric-gametest` строку `"moe.dexx.tacticaltablet.gametest.NetworkGameTests"`.

- [ ] **Step 2: Убедиться, что тесты не компилируются**

Run: `./gradlew compileTestJava compileGametestJava`
Expected: FAIL, `cannot find symbol ... StrikeCodecs`.

- [ ] **Step 3: Реализовать идентификаторы и кодеки**

`src/main/java/moe/dexx/tacticaltablet/net/ModPackets.java`:

```java
package moe.dexx.tacticaltablet.net;

import moe.dexx.tacticaltablet.TacticalTablet;
import net.minecraft.resources.ResourceLocation;

public final class ModPackets {
    // client -> server
    public static final ResourceLocation UPDATE_SETTINGS = TacticalTablet.id("update_settings");
    public static final ResourceLocation LAUNCH_REQUEST = TacticalTablet.id("launch_request");
    public static final ResourceLocation CANCEL_REQUEST = TacticalTablet.id("cancel_request");
    public static final ResourceLocation EMERGENCY_STOP = TacticalTablet.id("emergency_stop");
    // server -> client
    public static final ResourceLocation LAUNCH_REJECTED = TacticalTablet.id("launch_rejected");
    public static final ResourceLocation STRIKE_STATE = TacticalTablet.id("strike_state");
    public static final ResourceLocation STRIKE_ABORT = TacticalTablet.id("strike_abort");
    public static final ResourceLocation STRIKE_PROGRESS = TacticalTablet.id("strike_progress");

    private ModPackets() {
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/net/StrikeState.java`:

```java
package moe.dexx.tacticaltablet.net;

import java.util.UUID;
import moe.dexx.tacticaltablet.strike.Strike;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeTimeline;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.core.BlockPos;

/** What a client needs to know about a strike. phaseStartTick is Level.getGameTime, which clients share. */
public record StrikeState(UUID id, UUID owner, StrikePhase phase, long phaseStartTick, int durationTicks,
                          BlockPos target, int radius, int salvos, StrikeType type, boolean modifiesWorld) {
    public static StrikeState of(Strike strike) {
        return new StrikeState(strike.id(), strike.owner(), strike.phase(), strike.phaseStartTick(),
                StrikeTimeline.durationTicks(strike.phase(), strike.params()), strike.target(),
                strike.params().radius(), strike.params().salvos(), strike.params().type(), strike.params().modifiesWorld());
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/net/StrikeCodecs.java`:

```java
package moe.dexx.tacticaltablet.net;

import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.minecraft.network.FriendlyByteBuf;

/** Wire format shared by the server and the client. Numbers are sent as-is; the server validates them. */
public final class StrikeCodecs {
    private StrikeCodecs() {
    }

    public static void writeParams(FriendlyByteBuf buf, StrikeParams params) {
        buf.writeBoolean(params.hasTarget());
        buf.writeInt(params.targetX());
        buf.writeInt(params.targetY());
        buf.writeInt(params.targetZ());
        buf.writeInt(params.radius());
        buf.writeInt(params.power());
        buf.writeInt(params.salvos());
        buf.writeInt(params.countdownSeconds());
        buf.writeEnum(params.type());
        buf.writeBoolean(params.destroyBlocks());
        buf.writeBoolean(params.destroyLiquids());
        buf.writeBoolean(params.damageEntities());
    }

    public static StrikeParams readParams(FriendlyByteBuf buf) {
        return new StrikeParams(
                buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readEnum(StrikeType.class),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void writeState(FriendlyByteBuf buf, StrikeState state) {
        buf.writeUUID(state.id());
        buf.writeUUID(state.owner());
        buf.writeEnum(state.phase());
        buf.writeLong(state.phaseStartTick());
        buf.writeInt(state.durationTicks());
        buf.writeBlockPos(state.target());
        buf.writeInt(state.radius());
        buf.writeInt(state.salvos());
        buf.writeEnum(state.type());
        buf.writeBoolean(state.modifiesWorld());
    }

    public static StrikeState readState(FriendlyByteBuf buf) {
        return new StrikeState(buf.readUUID(), buf.readUUID(), buf.readEnum(StrikePhase.class), buf.readLong(), buf.readInt(),
                buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readEnum(StrikeType.class), buf.readBoolean());
    }
}
```

- [ ] **Step 4: Реализовать проверки игрока и серверные обработчики**

`src/main/java/moe/dexx/tacticaltablet/strike/PlayerChecks.java`:

```java
package moe.dexx.tacticaltablet.strike;

import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlayerChecks {
    public static final String REJECT_NO_TABLET = "tactical_tablet.reject.no_tablet";
    public static final String REJECT_GAMEMODE = "tactical_tablet.reject.gamemode";

    private PlayerChecks() {
    }

    /** @return the tablet the player holds (main hand first), or ItemStack.EMPTY */
    public static ItemStack heldTablet(Player player) {
        if (player.getMainHandItem().is(ModItems.TACTICAL_TABLET)) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(ModItems.TACTICAL_TABLET)) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    /** @return the translation key of the reason this player may not launch, or null */
    public static String launchReject(ServerPlayer player, ServerConfig config) {
        if (heldTablet(player).isEmpty()) {
            return REJECT_NO_TABLET;
        }
        if (!config.allows(player.gameMode.getGameModeForPlayer().getName())) {
            return REJECT_GAMEMODE;
        }
        return null;
    }
}
```

`src/main/java/moe/dexx/tacticaltablet/net/ServerNetworking.java`:

```java
package moe.dexx.tacticaltablet.net;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.item.TabletSettings;
import moe.dexx.tacticaltablet.strike.AbortReason;
import moe.dexx.tacticaltablet.strike.PlayerChecks;
import moe.dexx.tacticaltablet.strike.Strike;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server side of the protocol. Every handler repeats the full validation: the client is never trusted. */
public final class ServerNetworking {
    public static final String REJECT_CANNOT_CANCEL = "tactical_tablet.reject.cannot_cancel";
    public static final String REJECT_NOTHING_TO_STOP = "tactical_tablet.reject.nothing_to_stop";

    private ServerNetworking() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.UPDATE_SETTINGS, (server, player, handler, buf, sender) -> {
            StrikeParams params = StrikeCodecs.readParams(buf);
            server.execute(() -> handleUpdateSettings(player, params));
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.LAUNCH_REQUEST, (server, player, handler, buf, sender) -> {
            StrikeParams params = StrikeCodecs.readParams(buf);
            server.execute(() -> handleLaunch(player, params));
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.CANCEL_REQUEST, (server, player, handler, buf, sender) -> {
            boolean resourceFailure = buf.readBoolean();
            server.execute(() -> handleCancel(player, resourceFailure));
        });
        ServerPlayNetworking.registerGlobalReceiver(ModPackets.EMERGENCY_STOP, (server, player, handler, buf, sender) ->
                server.execute(() -> handleEmergencyStop(player)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendActiveStrikes(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> sendActiveStrikes(player));
    }

    public static void handleUpdateSettings(ServerPlayer player, StrikeParams params) {
        ItemStack tablet = PlayerChecks.heldTablet(player);
        if (tablet.isEmpty()) {
            sendRejected(player, PlayerChecks.REJECT_NO_TABLET);
            return;
        }
        String reject = params.validateSettings(TacticalTablet.config().maxRadius);
        if (reject != null) {
            sendRejected(player, reject);
            return;
        }
        TabletSettings.write(tablet, params);
    }

    public static void handleLaunch(ServerPlayer player, StrikeParams params) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return;
        }
        String reject = PlayerChecks.launchReject(player, TacticalTablet.config());
        if (reject == null) {
            reject = manager.launch(player.getUUID(), player.serverLevel(), params);
        }
        if (reject != null) {
            sendRejected(player, reject);
            return;
        }
        TabletSettings.write(PlayerChecks.heldTablet(player), params);
    }

    public static void handleCancel(ServerPlayer player, boolean resourceFailure) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return;
        }
        String reason = resourceFailure ? AbortReason.RESOURCES : AbortReason.CANCELLED;
        if (!manager.cancel(player.getUUID(), reason)) {
            sendRejected(player, REJECT_CANNOT_CANCEL);
        }
    }

    public static void handleEmergencyStop(ServerPlayer player) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return;
        }
        if (!manager.emergencyStop(player.getUUID())) {
            sendRejected(player, REJECT_NOTHING_TO_STOP);
        }
    }

    public static StrikeManager.Listener listener(MinecraftServer server) {
        return new StrikeManager.Listener() {
            @Override
            public void onState(Strike strike) {
                broadcast(server, strike, ModPackets.STRIKE_STATE, buf -> StrikeCodecs.writeState(buf, StrikeState.of(strike)));
            }

            @Override
            public void onAbort(Strike strike, String reasonKey) {
                broadcast(server, strike, ModPackets.STRIKE_ABORT, buf -> {
                    buf.writeUUID(strike.id());
                    buf.writeUtf(reasonKey);
                });
            }

            @Override
            public void onProgress(Strike strike, int done, int total) {
                ServerPlayer owner = server.getPlayerList().getPlayer(strike.owner());
                if (owner != null) {
                    send(owner, ModPackets.STRIKE_PROGRESS, buf -> {
                        buf.writeUUID(strike.id());
                        buf.writeVarInt(done);
                        buf.writeVarInt(total);
                    });
                }
            }
        };
    }

    /** Everyone in the strike's dimension sees it; the owner is told wherever they are. */
    private static void broadcast(MinecraftServer server, Strike strike, ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        Set<ServerPlayer> recipients = new LinkedHashSet<>();
        ServerLevel level = server.getLevel(strike.dimension());
        if (level != null) {
            recipients.addAll(level.players());
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(strike.owner());
        if (owner != null) {
            recipients.add(owner);
        }
        for (ServerPlayer recipient : recipients) {
            send(recipient, channel, writer);
        }
    }

    private static void sendActiveStrikes(ServerPlayer player) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return;
        }
        for (Strike strike : manager.active()) {
            if (strike.dimension() == player.level().dimension() || strike.owner().equals(player.getUUID())) {
                send(player, ModPackets.STRIKE_STATE, buf -> StrikeCodecs.writeState(buf, StrikeState.of(strike)));
            }
        }
    }

    private static void sendRejected(ServerPlayer player, String reasonKey) {
        send(player, ModPackets.LAUNCH_REJECTED, buf -> buf.writeUtf(reasonKey));
    }

    private static void send(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writer.accept(buf);
        ServerPlayNetworking.send(player, channel, buf);
    }
}
```

- [ ] **Step 5: Подключить сеть в точке входа**

В `TacticalTablet.java`:

- добавить импорт `moe.dexx.tacticaltablet.net.ServerNetworking`;
- в `onInitialize()` после `ModItems.register();` добавить `ServerNetworking.register();`;
- в обработчике `SERVER_STARTING` заменить `StrikeManager.Listener.NONE` на `ServerNetworking.listener(server)`.

- [ ] **Step 6: Запустить все тесты**

Run: `./gradlew test runGametest`
Expected: юнит-тесты проходят (включая 3 теста `StrikeCodecsTest`); `All 35 required tests passed :)`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Add the server side of the strike protocol

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Команды, выделенный сервер и замер

**Files:**
- Create: `src/main/java/moe/dexx/tacticaltablet/command/TabletCommands.java`
- Create: `src/main/java/moe/dexx/tacticaltablet/bench/BenchmarkHook.java`
- Modify: `src/main/java/moe/dexx/tacticaltablet/TacticalTablet.java`
- Modify: `build.gradle` (запуск `runBenchmark`)
- Modify: `src/main/resources/assets/tactical_tablet/lang/en_us.json`, `ru_ru.json`
- Test: `src/gametest/java/moe/dexx/tacticaltablet/gametest/CommandGameTests.java`
- Modify: `src/gametest/resources/fabric.mod.json`
- Create: `docs/perf/2026-10-destruction-baseline.md`

**Interfaces:**
- Consumes: `TacticalTablet.manager()`, `config()`, `tickTimes()`, `StrikeManager`, `StrikeParams`, `StrikePhase`, `CraterProfile`.
- Produces:
  - `TabletCommands.register()` и `TabletCommands.CONSOLE_OWNER` (`new UUID(0L, 0L)`) — владелец ударов, запущенных не игроком.
  - Команды уровня 2: `/tacticaltablet status`, `/tacticaltablet stop`, `/tacticaltablet stop all`, `/tacticaltablet stop <игрок>`, `/tacticaltablet strike <pos> <radius> [power]`.
  - `BenchmarkHook.register()` — активен только при системном свойстве `tactical_tablet.benchmark=<радиус>,<сила>`; после замера пишет строку `[bench] …` в лог и останавливает сервер.
  - Задача Gradle `runBenchmark -Pbench=<радиус>,<сила>`.

- [ ] **Step 1: Написать падающие тесты команд**

`src/gametest/java/moe/dexx/tacticaltablet/gametest/CommandGameTests.java`:

```java
package moe.dexx.tacticaltablet.gametest;

import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.command.TabletCommands;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class CommandGameTests implements FabricGameTest {
    private static int run(GameTestHelper helper, int permissionLevel, String command) {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPermission(permissionLevel).withSuppressedOutput();
        return helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "commands")
    public void strikeCommandDigsACraterAndStopAllClearsIt(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        BlockPos target = TestArena.target(helper);
        String position = target.getX() + " " + target.getY() + " " + target.getZ();
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 3") == 1, "the strike command failed");
        helper.assertTrue(TacticalTablet.manager().strikeOf(TabletCommands.CONSOLE_OWNER) != null, "no console strike is active");
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 3") == 0,
                "a second console strike must be refused while the first is active");
        helper.assertTrue(run(helper, 2, "tacticaltablet status") == 1, "status must report one strike");
        helper.runAfterDelay(2, () -> {
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
            helper.assertTrue(run(helper, 2, "tacticaltablet stop all") == 1, "stop all must stop one strike");
            helper.assertTrue(TacticalTablet.manager().strikeOf(TabletCommands.CONSOLE_OWNER) == null, "the strike is still active");
            helper.assertTrue(run(helper, 2, "tacticaltablet stop all") == 0, "there is nothing left to stop");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "commands")
    public void commandsRequireOperatorPermission(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        BlockPos target = TestArena.target(helper);
        String position = target.getX() + " " + target.getY() + " " + target.getZ();
        helper.assertTrue(run(helper, 0, "tacticaltablet strike " + position + " 3") == 0, "a non-operator must be refused");
        helper.assertTrue(run(helper, 0, "tacticaltablet stop all") == 0, "a non-operator must be refused");
        helper.runAfterDelay(2, () -> {
            TestArena.assertSlabIntact(helper);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "commands")
    public void strikeCommandRejectsARadiusOutsideTheRange(GameTestHelper helper) {
        BlockPos target = TestArena.target(helper);
        String position = target.getX() + " " + target.getY() + " " + target.getZ();
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 1001") == 0, "radius 1001 must be refused");
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 0") == 0, "radius 0 must be refused");
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 3 11") == 0, "power 11 must be refused");
        helper.succeed();
    }
}
```

Команды без игрока запускают удар от имени общего владельца `CONSOLE_OWNER` в глобальном менеджере сервера, поэтому класс вынесен в отдельную партию `commands`. Тесты внутри партии идут одновременно, поэтому активный удар создаёт только первый тест, а два других удар не запускают.

В `src/gametest/resources/fabric.mod.json` добавить в список `fabric-gametest` строку `"moe.dexx.tacticaltablet.gametest.CommandGameTests"`.

- [ ] **Step 2: Убедиться, что тесты не компилируются**

Run: `./gradlew compileGametestJava`
Expected: FAIL, `cannot find symbol ... TabletCommands`.

- [ ] **Step 3: Реализовать команды**

`src/main/java/moe/dexx/tacticaltablet/command/TabletCommands.java`:

```java
package moe.dexx.tacticaltablet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.UUID;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.strike.Strike;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TabletCommands {
    /** Owner of strikes started from the console or a command block. */
    public static final UUID CONSOLE_OWNER = new UUID(0L, 0L);

    private TabletCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("tacticaltablet")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("stop")
                                .executes(context -> stop(context.getSource(), ownerOf(context.getSource())))
                                .then(Commands.literal("all").executes(context -> stopAll(context.getSource())))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> stop(context.getSource(),
                                                EntityArgument.getPlayer(context, "player").getUUID()))))
                        .then(Commands.literal("strike")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(StrikeParams.MIN_RADIUS, StrikeParams.MAX_RADIUS))
                                                .executes(context -> strike(context, StrikeParams.DEFAULT.power()))
                                                .then(Commands.argument("power", IntegerArgumentType.integer(StrikeParams.MIN_POWER, StrikeParams.MAX_POWER))
                                                        .executes(context -> strike(context, IntegerArgumentType.getInteger(context, "power")))))))));
    }

    private static UUID ownerOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? player.getUUID() : CONSOLE_OWNER;
    }

    private static int status(CommandSourceStack source) {
        StrikeManager manager = TacticalTablet.manager();
        String averageTick = String.format(Locale.ROOT, "%.1f", TacticalTablet.tickTimes().averageMs());
        source.sendSuccess(() -> Component.translatable("tactical_tablet.command.status.server", averageTick), false);
        if (manager == null || manager.active().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("tactical_tablet.command.status.none"), false);
            return 0;
        }
        for (Strike strike : manager.active()) {
            BlockPos target = strike.target();
            int done = strike.job() == null ? 0 : strike.job().chunksDone();
            int total = strike.job() == null ? 0 : strike.job().chunksTotal();
            source.sendSuccess(() -> Component.translatable("tactical_tablet.command.status.line",
                    strike.owner().toString(), strike.phase().name(), target.getX(), target.getY(), target.getZ(),
                    strike.params().radius(), done, total), false);
        }
        return manager.active().size();
    }

    private static int stop(CommandSourceStack source, UUID owner) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null || !manager.forceStop(owner)) {
            source.sendFailure(Component.translatable("tactical_tablet.reject.nothing_to_stop"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("tactical_tablet.command.stop.done", 1), true);
        return 1;
    }

    private static int stopAll(CommandSourceStack source) {
        StrikeManager manager = TacticalTablet.manager();
        int stopped = manager == null ? 0 : manager.forceStopAll();
        if (stopped == 0) {
            source.sendFailure(Component.translatable("tactical_tablet.reject.nothing_to_stop"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("tactical_tablet.command.stop.done", stopped), true);
        return stopped;
    }

    private static int strike(CommandContext<CommandSourceStack> context, int power) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return 0;
        }
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        StrikeParams params = StrikeParams.DEFAULT.withTarget(pos.getX(), pos.getY(), pos.getZ()).withRadius(radius).withPower(power);
        // Skips the countdown and the cinematic: the strike starts at impact.
        String reject = manager.launch(ownerOf(source), source.getLevel(), params, StrikePhase.IMPACT);
        if (reject != null) {
            source.sendFailure(Component.translatable(reject));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("tactical_tablet.command.strike.started",
                pos.getX(), pos.getY(), pos.getZ(), radius, power), true);
        return 1;
    }
}
```

Добавить в оба языковых файла (перед закрывающей скобкой, с запятой после предыдущей строки):

`en_us.json`:

```json
  "tactical_tablet.command.status.server": "Average server tick: %s ms",
  "tactical_tablet.command.status.none": "No active strikes",
  "tactical_tablet.command.status.line": "Strike by %s: %s at %s %s %s, radius %s, chunks %s/%s",
  "tactical_tablet.command.stop.done": "Stopped strikes: %s",
  "tactical_tablet.command.strike.started": "Immediate strike at %s %s %s, radius %s, power %s"
```

`ru_ru.json`:

```json
  "tactical_tablet.command.status.server": "Среднее время тика сервера: %s мс",
  "tactical_tablet.command.status.none": "Активных ударов нет",
  "tactical_tablet.command.status.line": "Удар игрока %s: %s по %s %s %s, радиус %s, чанков %s/%s",
  "tactical_tablet.command.stop.done": "Остановлено ударов: %s",
  "tactical_tablet.command.strike.started": "Немедленный удар по %s %s %s, радиус %s, сила %s"
```

В `TacticalTablet.onInitialize()` после `ServerNetworking.register();` добавить `TabletCommands.register();` и импорт `moe.dexx.tacticaltablet.command.TabletCommands`.

- [ ] **Step 4: Запустить все тесты**

Run: `./gradlew test runGametest`
Expected: `All 38 required tests passed :)`. Затем повторить проверку совпадения ключей из задачи 6, шаг 7 — ожидается `[]`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add operator commands for strikes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 6: Реализовать автозамер**

`src/main/java/moe/dexx/tacticaltablet/bench/BenchmarkHook.java`:

```java
package moe.dexx.tacticaltablet.bench;

import java.util.Locale;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.command.TabletCommands;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeSummary;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Unattended measurement for a dedicated server: with -Dtactical_tablet.benchmark=RADIUS,POWER the mod fires an
 * immediate strike at the world spawn, logs one "[bench]" line when it is over and stops the server.
 */
public final class BenchmarkHook {
    public static final String PROPERTY = "tactical_tablet.benchmark";
    private static final int SETTLE_TICKS = 100;
    private static final int[][] LIGHT_SAMPLES = {{0, 0}, {16, 0}, {0, 16}, {-16, -16}, {32, 0}};

    private static int radius;
    private static int power;
    private static BlockPos target;
    private static boolean started;
    private static int settleTicks = -1;
    private static double peakAverageTickMs;

    private BenchmarkHook() {
    }

    public static void register() {
        String spec = System.getProperty(PROPERTY);
        if (spec == null || spec.isBlank()) {
            return;
        }
        String[] parts = spec.split(",");
        radius = Integer.parseInt(parts[0].trim());
        power = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : StrikeParams.DEFAULT.power();
        ServerLifecycleEvents.SERVER_STARTED.register(BenchmarkHook::start);
        ServerTickEvents.END_SERVER_TICK.register(BenchmarkHook::tick);
    }

    private static void start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, spawn.getX(), spawn.getZ()) - 1;
        target = new BlockPos(spawn.getX(), Math.max(level.getMinBuildHeight(), surface), spawn.getZ());
        StrikeParams params = StrikeParams.DEFAULT.withTarget(target.getX(), target.getY(), target.getZ())
                .withRadius(radius).withPower(power);
        String reject = TacticalTablet.manager().launch(TabletCommands.CONSOLE_OWNER, level, params, StrikePhase.IMPACT);
        if (reject != null) {
            TacticalTablet.LOGGER.error("[bench] launch rejected: {}", reject);
            server.halt(false);
            return;
        }
        TacticalTablet.tickTimes().resetMax();
        started = true;
        TacticalTablet.LOGGER.info("[bench] started radius={} power={} target={}", radius, power, target.toShortString());
    }

    private static void tick(MinecraftServer server) {
        if (!started) {
            return;
        }
        StrikeManager manager = TacticalTablet.manager();
        if (settleTicks < 0) {
            peakAverageTickMs = Math.max(peakAverageTickMs, TacticalTablet.tickTimes().averageMs());
            if (manager.strikeOf(TabletCommands.CONSOLE_OWNER) == null) {
                settleTicks = 0;
            }
            return;
        }
        if (++settleTicks < SETTLE_TICKS) {
            return;
        }
        started = false;
        report(server, manager.lastSummary());
        server.halt(false);
    }

    private static void report(MinecraftServer server, StrikeSummary summary) {
        ServerLevel level = server.overworld();
        int lit = 0;
        int sampled = 0;
        for (int[] offset : LIGHT_SAMPLES) {
            if (Math.abs(offset[0]) > radius / 2 || Math.abs(offset[1]) > radius / 2) {
                continue;
            }
            int x = target.getX() + offset[0];
            int z = target.getZ() + offset[1];
            BlockPos above = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            sampled++;
            if (level.getBrightness(LightLayer.SKY, above) == 15) {
                lit++;
            }
        }
        TacticalTablet.LOGGER.info(String.format(Locale.ROOT,
                "[bench] radius=%d power=%d chunks=%d/%d blocks=%d wallMs=%d peakAvgTickMs=%.1f maxTickMs=%.1f skyLight=%d/%d",
                radius, power, summary.chunksDone(), summary.chunksTotal(), summary.blocksRemoved(), summary.wallMillis(),
                peakAverageTickMs, TacticalTablet.tickTimes().maxMs(), lit, sampled));
    }
}
```

В `TacticalTablet.onInitialize()` после `TabletCommands.register();` добавить `BenchmarkHook.register();` и импорт `moe.dexx.tacticaltablet.bench.BenchmarkHook`.

В `build.gradle` в блок `loom { runs { … } }` после запуска `gametest` добавить:

```groovy
        benchmark {
            server()
            name "Benchmark"
            vmArg "-Dtactical_tablet.benchmark=${project.findProperty('bench') ?: '100,5'}"
            programArg "nogui"
            runDir "build/benchmark"
        }
```

- [ ] **Step 7: Подготовить каталог замера**

Выделенный сервер требует принятого лицензионного соглашения Mojang. Каталог `build/` не попадает в git.

```bash
cd /c/Users/pozo/Projects/tactical-tablet
mkdir -p build/benchmark
printf 'eula=true\n' > build/benchmark/eula.txt
printf 'level-seed=tactical-tablet-bench\nonline-mode=false\nmax-tick-time=-1\nview-distance=10\n' > build/benchmark/server.properties
```

- [ ] **Step 8: Замер радиуса 100**

```bash
rm -rf build/benchmark/world
./gradlew runBenchmark -Pbench=100,5
```

Expected: сервер стартует без `ClassNotFoundException` и `NoClassDefFoundError` (это же проверка запуска выделенного сервера из раздела 12 спеки), в логе появляется строка `[bench] radius=100 power=5 chunks=N/N blocks=… wallMs=… peakAvgTickMs=… maxTickMs=… skyLight=5/5`, после чего сервер останавливается сам.

- [ ] **Step 9: Замер радиуса 500**

```bash
rm -rf build/benchmark/world
./gradlew runBenchmark -Pbench=500,5
```

Expected: строка `[bench] radius=500 …`. Здесь большинство чанков зоны ещё не сгенерированы, поэтому замер включает генерацию.

- [ ] **Step 10: Записать результаты и сверить с порогами**

Создать `docs/perf/2026-10-destruction-baseline.md`: заголовок, дата, версия мода, процессор и объём памяти машины, затем таблица с колонками «радиус», «сила», «чанков», «блоков», `wallMs`, `peakAvgTickMs`, `maxTickMs`, `skyLight` — по одной строке на замер, значения дословно из строк `[bench]`. Ниже — вывод «пороги выполнены» или «пороги не выполнены» с перечислением нарушенных.

Пороги:

| Замер | `wallMs` | `peakAvgTickMs` | `skyLight` | `chunks` |
|---|---|---|---|---|
| Радиус 100 | не больше 20 000 | не больше 60 | все образцы | `N/N` |
| Радиус 500 | не больше 600 000 | не больше 65 | все образцы | `N/N` |

Если любой порог нарушен: остановиться, не начинать план 2, сообщить заказчику цифры и предложить отдельный план записи на уровне секций чанка. Если пороги выполнены — продолжать.

- [ ] **Step 11: Финальная проверка и commit**

Run: `./gradlew clean build test runGametest`
Expected: `BUILD SUCCESSFUL`, все юнит-тесты и `All 38 required tests passed :)`.

```bash
git add -A
git commit -m "Add the benchmark hook and record the destruction baseline

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Соответствие спеке

| Раздел спеки | Где реализовано |
|---|---|
| 2. Платформа и проект | Задача 1 |
| 3. Предмет | Задача 6 (экран по ПКМ — план 2, через `TacticalTabletItem.clientUseHandler`) |
| 4. Параметры и валидация | Задачи 1, 8 (`StrikeManager.launch`), 9 (`PlayerChecks`) |
| 5. Ход удара, ограничения, автоотмена | Задачи 2, 8 |
| 7. Разрушение | Задачи 3, 4, 7; аварийная остановка — задачи 8, 9, 10 |
| 10. Сеть (серверная сторона) | Задача 9 |
| 11. Серверный конфиг и команды | Задачи 5, 10 |
| 12. Проверка (юнит-тесты, игровые тесты, выделенный сервер) | Все задачи; запуск выделенного сервера — задача 10, шаг 8 |
| 14. Риск скорости записи | Задача 10, шаги 8–10 |
| 6, 8, 9, клиентский конфиг | Планы 2 и 3 |
