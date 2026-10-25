# CopeCore

The shared core for every **Cope Studios** plugin (Cope Bosses, Cope Nights, CSKoth, CZRTP, CSSPVP, CSCombat,
CopePortraits, CSNumbers, CSCommands, CSIcons, ...).

Instead of every plugin writing its own color parser, menus, chat prompts, region code and config loading,
they all use this one, tested version. Plugins also use it to **talk to each other**: shared player states
("this player is in combat"), shared regions and the selection wand, one PlaceholderAPI expansion, and services.

| | |
|---|---|
| **Minecraft** | 1.20.1 → 1.21.x (one jar for all of them) |
| **Servers** | Spigot, Paper, Purpur, Arclight |
| **Java** | 17 (MC 1.20–1.20.4) or 21 (MC 1.20.5+) |
| **Soft dependency** | PlaceholderAPI (optional) |
| **API version** | 1 (`CopeCore.API_VERSION`) |

---

## Contents

1. [Installing on a server](#1-installing-on-a-server)
2. [Building](#2-building)
3. [Using CopeCore in a plugin](#3-using-copecore-in-a-plugin) (includes a pom.xml template)
4. [Feature tour with examples](#4-feature-tour)
5. [Commands and permissions](#5-commands-and-permissions)
6. [Configuration files](#6-configuration-files)
7. [How it stays compatible from 1.20 to 1.21](#7-compatibility-notes)
8. [Moving a plugin to CopeCore: checklist](#8-moving-a-plugin-to-copecore-checklist)
9. [Project layout](#9-project-layout)

---

## 1. Installing on a server

1. Put `CopeCore-1.0.0.jar` in `plugins/`.
2. Put the Cope plugins that use it in `plugins/` as well.
3. Start the server. CopeCore creates `plugins/CopeCore/config.yml` and `messages.yml`.

The console shows which plugins use CopeCore, and warns if two plugins claim the same command:

```
[CopeCore] CopeCore 1.0.0 enabled in 41ms (API 1).
[CopeCore] Plugins using CopeCore (2): CopeBosses 0.5.0, CSKoth 2.0.0
[CopeCore] Command conflict: /cb is claimed by CSCombat and CopeBosses. ...
```

---

## 2. Building

**IntelliJ:** open the project → Maven tab (right side) → `CopeCore` → `Lifecycle` → double-click **`install`**.

**Terminal:** `mvn clean install`

`install` (not just `package`) matters: besides building `target/CopeCore-1.0.0.jar`, it copies CopeCore into
your local Maven repository (`~/.m2`), which is where your other plugins look for it when **they** build.

> **Rule of thumb:** after changing CopeCore, run `install` on CopeCore **first**, then build the plugins.

---

## 3. Using CopeCore in a plugin

### 3.1 pom.xml template

Every Cope plugin is moving to Maven. This is the template to use. It targets the same versions as CopeCore,
so every plugin built from it works on 1.20 → 1.21.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>us.copestudios</groupId>
    <artifactId>cskoth</artifactId>          <!-- lowercase plugin name -->
    <version>2.0.0</version>
    <name>CSKoth</name>

    <properties>
        <java.version>17</java.version>       <!-- runs on Java 17 AND 21 -->
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>spigotmc-repo</id>
            <url>https://hub.spigotmc.org/nexus/content/repositories/snapshots/</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- Lowest version we support. Everything that compiles here runs on 1.20.1 - 1.21.x -->
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot-api</artifactId>
            <version>1.20.1-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
        <!-- CopeCore: "provided" = it's on the server, never bundle it -->
        <dependency>
            <groupId>us.copestudios</groupId>
            <artifactId>copecore</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.name}-${project.version}</finalName>
        <resources>
            <!-- Only plugin.yml gets ${project.version} filled in -->
            <resource>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>
                <includes><include>plugin.yml</include></includes>
            </resource>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>false</filtering>
                <excludes><exclude>plugin.yml</exclude></excludes>
            </resource>
        </resources>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>${java.version}</release>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

No shade plugin is needed: colors, MiniMessage and everything else come from CopeCore at runtime.
(Add the shade plugin back only if a plugin bundles its own library.)

### 3.2 plugin.yml

```yaml
name: CSKoth
version: '${project.version}'
main: us.copestudios.cskoth.CSKoth
api-version: '1.20'
depend: [CopeCore]          # load CopeCore first. Required.
softdepend: [PlaceholderAPI]
```

### 3.3 onEnable

```java
@Override
public void onEnable() {
    if (!CopeCore.requireApi(this, 1)) {
        return;                                   // CopeCore too old: clear message, plugin disabled
    }
    this.config = new ConfigFile(this, "config.yml");
    this.messages = CopeCore.messages(this);      // plugins/CSKoth/messages.yml
    this.koths = CopeCore.regions().store(this, "koths.yml");
    // commands, menus, placeholders... see below
}
```

---

## 4. Feature tour

Every class is commented in detail. Ctrl+Click any CopeCore method in IntelliJ to read its documentation
(the `-sources.jar` that `install` creates makes that work).

### 4.1 Text and colors: `Text`

Accepts **MiniMessage and every old format your plugins used**, even mixed:

| Format | Example | Used by |
|---|---|---|
| MiniMessage | `<gradient:#FF8800:#8B0000>Blood Moon</gradient>` | Cope Bosses |
| Classic | `&a&lHi` | everyone |
| Hex | `&#FF8800` | CSSPVP, CSCombat, CSKoth |
| Spigot hex | `&x&F&F&8&8&0&0` | CZRTP |
| Section sign | `§c` | CSCommands, CSIcons |

```java
Text.send(player, "<gold>Hello <click:run_command:'/spawn'><u>click here</u></click>");
Text.broadcast("&c&lBlood Moon &7is rising...");
Text.actionBar(player, "<yellow>Capturing: <white>45%");
Text.title(player, "<red>BOSS", "<gray>The Hunter appeared", 10, 60, 20);
meta.setDisplayName(Text.itemName("<gold>Hunter's Bow"));   // never italic
String safe = Text.escape(chatMessage);  // ALWAYS escape text typed by players
```

Click and hover work on Spigot too. The message goes out as JSON through the BungeeCord chat API that every
server has.

### 4.2 Messages: `Messages`

```yaml
# plugins/CSKoth/messages.yml
prefix: "<gold>[KOTH] "
koth:
  started: "<yellow>KOTH <white><koth></white> started! Capture in <green><time>"
```
```java
messages.send(player, "koth.started", "koth", "castle", "time", "5m");   // with prefix
messages.broadcast("koth.started", "koth", "castle", "time", "5m");
messages.sendRaw(player, "koth.help");                                  // no prefix
messages.title(player, "koth.title", "koth.subtitle", 10, 60, 20, "koth", "castle");
List<String> lore = messages.itemLore("menu.start-lore");
```

* Placeholders work as `<koth>`, `{koth}` or `%koth%`, so old configs keep working.
* PlaceholderAPI placeholders are filled in automatically for players.
* A missing key shows `[Missing message: CSKoth → koth.started]` in-game, so typos are easy to spot.
* **Shared texts**: if a key isn't in the plugin's file, CopeCore's `messages.yml` is checked. That's where
  `generic.no-permission`, `generic.players-only`, the help page layout and prompt texts live. A plugin can
  override any of them by adding the same key to its own messages.yml.
* `""` (empty) disables a message.

### 4.3 Config files: `ConfigFile`

```java
ConfigFile config = new ConfigFile(this, "config.yml");
int time = config.get().getInt("default-capture-time", 300);
config.get().set("default-capture-time", 600);
config.save();
config.reload();

// Data files: never merge example content from the jar into them
ConfigFile stats = new ConfigFile(this, "stats.yml", false);
```

* Created from the jar's copy (comments kept) the first time.
* **New settings are added to existing files automatically** when you update a plugin.
* **Broken or emptied files are repaired**: the bad file is kept as `config.yml.broken-<date>`, a fresh one
  is written, and the console says what happened. No more plugins starting with no settings.

### 4.4 Commands: `CopeCommand`

```java
CopeCommand koth = new CopeCommand(this, "cskoth", messages);
koth.defaultAction(ctx -> new KothMainMenu(this, ctx.player()).open());   // just "/koth"

koth.sub("start")
        .permission("cskoth.admin").usage("<koth>").minArgs(1)
        .description("Start a KOTH event")
        .tab(ctx -> koths.names())
        .executes(ctx -> {
            String name = ctx.arg(0);
            if (!koths.contains(name)) {
                throw new CommandFailure("koth.not-found", "koth", name);   // sends the message, stops
            }
            int minutes = ctx.has(1) ? ctx.integer(1, 1, 120) : 10;          // validated number
            manager.start(name, minutes);
            ctx.reply("koth.started", "koth", name, "time", minutes + "m");
        });

koth.register();
```

Handled for you: permission check → players-only → required arguments → usage message → errors logged with
the command that caused them. `/koth help` is generated automatically and only shows what the player may use.

### 4.5 Menus: `Menu` + `ItemBuilder`

```java
public final class KothMainMenu extends Menu {
    public KothMainMenu(CSKoth plugin, Player viewer) {
        super(plugin, viewer, 3, "<gold><bold>KOTH Menu");
    }

    @Override
    protected void build() {
        set(11, ItemBuilder.of(Material.EMERALD_BLOCK).name("<green>Create KOTH").build(),
                click -> CopeCore.prompts().text(viewer, "<yellow>Name of the new KOTH?",
                        name -> { koths.create(name); open(); },
                        this::open));
        set(15, ItemBuilder.of(Material.BOOK).name("<aqua>List KOTHs").glow().build(),
                click -> new KothListMenu(plugin, viewer, 0).open());
        closeButton(22);
        fillBorder();
        fillEmpty();
    }
}
```

* Menus are recognised by object, **never by title**, so recoloring or translating a title can't break them.
* Items can't be taken, dragged in or shift-clicked in, except in slots you mark with `editable(...)`.
* Built in: `paginate(...)` for lists with page arrows, and `adjustNumber(...)` for the Cope Bosses
  number control (left +, right −, shift ×10, Q to type a value).
* Spam-click limit and click sound from `config.yml`.
* Open/close inside a click is delayed one tick automatically, as Bukkit requires.

### 4.6 Chat prompts: `CopeCore.prompts()`

```java
var prompts = CopeCore.prompts();
prompts.text(player, "<yellow>Name?", name -> ..., menu::open);
prompts.integer(player, "<yellow>Capture time (seconds)?", 10, 3600, seconds -> ..., menu::open);
prompts.decimal(player, "<yellow>Damage multiplier?", 0.1, 10, false, value -> ..., menu::open);
prompts.yesNo(player, "<yellow>Allow item drops?", allowed -> ..., menu::open);
prompts.here(player, "<yellow>Walk to corner 1.", location -> ..., menu::open);   // player types "here"
```

**Your code always runs on the main thread.** This fixes the async-chat bug in CSKoth and CSSPVP, where
KOTHs and arenas were created and files saved from the chat thread. Wrong input asks again, "cancel" cancels,
and unanswered questions time out.

### 4.7 Regions: `CopeCore.regions()`

One `Region` class replaces the six region systems (Cope Bosses, CSKoth, CZRTP, CSSPVP arenas and duel zones,
CSNumbers).

```java
RegionStore arenas = CopeCore.regions().store(this, "arenas.yml");

// Admin selects with /copecore wand (left/right click) - the SAME wand for every plugin
Region region = CopeCore.regions().selection(player).toRegion("pit");
if (region == null) { /* not selected */ }
arenas.put(region);                                   // saved immediately

arenas.get("pit").contains(player.getLocation());
Location spawn = arenas.get("pit").randomSafeLocation(random, 30);   // safe ground, no lava, under nether roof
List<RegionRef> here = CopeCore.regions().at(location);          // regions of ALL plugins here
CopeCore.regions().showOutline(player, region);                  // particle outline
```

`Region.load` also reads the old `pos1/pos2` formats from Cope Bosses and CSNumbers, so their files can be imported.

**Enter/leave events**, fired once for all plugins:

```java
@EventHandler
public void onEnter(RegionEnterEvent event) {
    if (!event.region().isFrom(this)) return;     // only my regions
    if (CopeCore.states().has(event.getPlayer(), PlayerStates.COMBAT)) {
        event.setCancelled(true);                 // can't walk in while in combat
    }
}
```

### 4.8 Shared player states: `CopeCore.states()`

How plugins know about each other **without depending on each other**:

```java
// CSCombat
CopeCore.states().set(victim, PlayerStates.COMBAT, 15_000);   // refreshes on every hit

// CZRTP
if (CopeCore.states().has(player, PlayerStates.COMBAT)) {
    messages.send(player, "rtp.in-combat", "time", CopeCore.states().remainingText(player, PlayerStates.COMBAT));
    return;
}

// CSSPVP
CopeCore.states().set(player, PlayerStates.IN_ARENA);
CopeCore.states().remove(player, PlayerStates.IN_ARENA);
```

Agreed names: `COMBAT`, `IN_ARENA`, `IN_DUEL`, `BOSS_FIGHT`, `TELEPORTING`, `EDITING`.
Every change fires `PlayerStateChangeEvent`. `/copecore states <player>` shows them.

### 4.9 Talking between plugins: `Services`

```java
// Cope Bosses publishes:
public interface BossService { boolean spawn(String bossId, Location at); }
Services.provide(this, BossService.class, new BossServiceImpl(this));

// Cope Nights (softdepend: [CopeBosses]) during a blood moon:
Services.find(BossService.class).ifPresent(bosses -> bosses.spawn("hunter", location));
```

If the other plugin isn't installed, `find` is empty and nothing breaks.

### 4.10 PlaceholderAPI: `CopeCore.placeholders()`

```java
CopeCore.placeholders().register(this, "koth", (player, key) -> switch (key) {
    case "wins" -> String.valueOf(stats.wins(player.getUniqueId()));
    case "active" -> manager.activeName();
    default -> null;
});
```

→ `%cope_koth_wins%`, `%cope_koth_active%`. No PlaceholderAPI dependency in your plugin, and nothing crashes
when PlaceholderAPI isn't installed (the bug CSKoth 1.0.15 has).

### 4.11 Rewards: `RewardSet`

```yaml
rewards:
  items:
    - item: {...}        # written by your editor menu
      amount: 1-3
      chance: 0.5
  commands: ["eco give <player> 1000"]
  messages: ["<green>You won!"]
  broadcast: ["<gold><player> <yellow>captured <koth>!"]
```
```java
RewardSet rewards = RewardSet.load(config.getConfigurationSection("rewards"), getLogger());
rewards.give(player, random, "koth", kothName);   // items that don't fit drop at the player's feet
```

### 4.12 Small helpers

```java
Tasks.later(plugin, 20, () -> ...);                          // 1 second later
Tasks.timer(plugin, 0, 20, () -> ...);                        // every second
Tasks.asyncThenSync(plugin, () -> ImageIO.read(file), img -> place(img));
Tasks.ensureSync(plugin, () -> ...);                          // main thread, now if possible

Cooldowns cooldowns = new Cooldowns();
if (!cooldowns.tryUse(uuid, "enderpearl", 15_000)) { ... }   // CSSPVP's missing pearl/apple delays

Sounds.play(player, "entity.player.levelup:1:1.5");           // also "ENTITY_PLAYER_LEVELUP", custom pack sounds
TimeFormat.duration(125_000);   // "2m 5s"
TimeFormat.clock(125);          // "2:05"
TimeFormat.parse("1h30m");      // 5_400_000 ms
Locations.save(config, "lobby", location);  Locations.load(config, "lobby");
Locations.findSafeSpot(world, x, z, minY, maxY);
ServerVersion.isAtLeast(1, 20, 5);
CopeCore.protection().protect(itemFrame);                     // indestructible entity
CopeCore.debug(this, "only printed when debug: true");
```

---

## 5. Commands and permissions

| Command | Permission | What it does |
|---|---|---|
| `/copecore` or `/ccore` | `copecore.admin` | help page |
| `/copecore info` | `copecore.admin` | version, server, plugins using CopeCore |
| `/copecore reload` | `copecore.admin` | reload `config.yml` and `messages.yml` |
| `/copecore wand` | `copecore.wand` | get the selection wand |
| `/copecore pos1` / `pos2` | `copecore.wand` | set a corner where you stand |
| `/copecore selection [clear]` | `copecore.wand` | show (with outline) or clear the selection |
| `/copecore regions [plugin]` | `copecore.admin` | regions here, or all regions of a plugin |
| `/copecore states [player]` | `copecore.admin` | shared states (combat, in-arena...) |
| `/copecore debug` | `copecore.admin` | toggle debug logging |

`copecore.admin` includes `copecore.wand`. Both default to op.

---

## 6. Configuration files

Both files are fully commented. Summary:

**`config.yml`**
* `debug`: extra console output
* `startup.show-banner`, `startup.warn-command-conflicts`
* `prompts.timeout-seconds`, `prompts.cancel-words`, `prompts.here-words`
* `menus.border-material`, `menus.filler-material`, `menus.click-cooldown-ms`, `menus.click-sound`
* `selection.wand-material`, `selection.show-outline`, `selection.outline-seconds`, `selection.outline-particles`
* `regions.enter-leave-events`
* `placeholders.enabled`, `placeholders.parse-in-messages`

**`messages.yml`**
* `prefix`: CopeCore's own prefix
* `generic.*`: shared by **all** Cope plugins (no permission, usage, help layout, menu buttons...)
* `prompt.*`: chat prompt texts
* `selection.*`: wand texts
* `command.*`: `/copecore` texts

---

## 7. Compatibility notes

How one jar runs on 1.20.1 → 1.21.x:

* **Compiled against the 1.20.1 API**: only methods that exist on every supported version can be used.
* **Java 17 bytecode**: runs on Java 17 and 21.
* **`Sound` is never used directly.** It changed from an enum to an interface in 1.21.3, which breaks plugins
  compiled for older versions. Sounds are played by their text key (`Sounds.play`).
* **`InventoryView` is never used.** It changed from a class to an interface in 1.21. The menu system only
  uses `event.getInventory()` / `getClickedInventory()`.
* **Particle names changed in 1.20.5** (`VILLAGER_HAPPY` → `HAPPY_VILLAGER`): the config lists several and
  the first one that exists is used.
* **Enchantment fields were renamed in 1.20.5**: `ItemBuilder.glow()` looks the enchantment up by key.
* **Adventure is bundled and relocated** to `us.copestudios.copecore.libs.kyori`, so it never clashes with
  Paper's own copy or another plugin's.
* Messages go out as JSON through the BungeeCord chat API, which Spigot, Paper, Purpur and Arclight all have.

The code was compile-tested against **Spigot API 1.20 and 1.21.11**.

**When writing plugins on top of CopeCore**, follow the same rules: compile against 1.20.1, avoid `Sound`,
`InventoryView`, `Attribute` constants (renamed in 1.21.2) and other enum constants that were renamed. Use
`ServerVersion.isAtLeast(...)` when behaviour must differ between versions.

---

## 8. Moving a plugin to CopeCore: checklist

For each plugin, one at a time:

- [ ] Convert to Maven with the template in 3.1. Package: `us.copestudios.<plugin>`
- [ ] `plugin.yml`: `depend: [CopeCore]`, `api-version: '1.20'`
- [ ] Colors / `ChatColor` / `§` / own hex parser → `Text` and `messages.yml`
- [ ] Hand-made YAML loading → `ConfigFile`
- [ ] `switch (args[0])` commands → `CopeCommand`
- [ ] Menus matched by title → `Menu` + `ItemBuilder`
- [ ] Chat input in `AsyncPlayerChatEvent` → `CopeCore.prompts()`
- [ ] Own region/zone class → `Region` + `RegionStore` (import old files with `Region.load`)
- [ ] Own reward lists → `RewardSet`
- [ ] PlaceholderAPI expansion → `CopeCore.placeholders().register(...)`
- [ ] Set/check shared states where it makes sense (combat, in-arena, ...)
- [ ] Remove the code that CopeCore now provides
- [ ] Test on a 1.20.1 server and on the newest 1.21.x

---

## 9. Project layout

```
CopeCore/
├── pom.xml                         build file (heavily commented)
├── README.md                       this file
└── src/main/
    ├── resources/
    │   ├── plugin.yml
    │   ├── config.yml
    │   └── messages.yml
    └── java/us/copestudios/copecore/
        ├── CopeCore.java           main class + static shortcuts
        ├── text/                   Text, Messages, LegacyConverter
        ├── config/                 ConfigFile
        ├── command/                CopeCommand, SubCommand, CommandContext, CommandFailure
        ├── gui/                    Menu, MenuClick, ItemBuilder, MenuListener
        ├── prompt/                 ChatPrompts
        ├── region/                 Region, RegionStore, RegionService, RegionRef, Selection, RegionListener
        ├── player/                 PlayerStates, Cooldowns
        ├── event/                  RegionEnterEvent, RegionLeaveEvent, PlayerStateChangeEvent
        ├── reward/                 RewardSet
        ├── entity/                 ProtectedEntities
        ├── hook/                   PlaceholderService, PapiBridge (PlaceholderAPI)
        ├── service/                Services
        ├── scheduler/              Tasks
        ├── util/                   TimeFormat, Sounds, Locations, ServerVersion
        └── internal/               /copecore command, startup checks
```
