# Fight Club

Fight Club is a combat overhaul for Minecraft 1.21.1 on Fabric and NeoForge. It adds longswords, spears and daggers in every vanilla material, and gives swords, shields, tridents and maces new moves: sword blocking, shield bashes, spear charges, dagger pounces, thrown weapons, a longsword spin and a mace hit streak. Every weapon number lives in a datapack file, every mechanic can be switched off in the config, and other mods can add their own weapons that use the same move sets.

This README is for players, modpack developers and mod developers. Pack developers mostly need [Configuration](#configuration) and [Weapon specs](#weapon-specs). Mod developers adding weapons should read [Java API](#java-api).

**Version parity.** The development workspace also contains forward-port targets for Minecraft 26.1 and 26.2 on Fabric and NeoForge. Those ports are still in progress and not ready for release. They don't have the weapon items, the [built-in mod compatibility](#built-in-mod-compatibility) materials or the [Java API](#java-api) yet; everything below describes the 1.21.1 builds.

## Contents

- [Requirements and integrations](#requirements-and-integrations)
- [Weapons and moves](#weapons-and-moves)
- [Configuration](#configuration)
- [Weapon specs](#weapon-specs): file locations, fields, overriding a weapon
- [Built-in mod compatibility](#built-in-mod-compatibility): extra materials, Better Combat, Vanilla Backport
- [Java API](#java-api): adding weapons from another mod, specs in code, models, queries
- [Troubleshooting](#troubleshooting)

## Requirements and integrations

| Mod | Status |
| --- | --- |
| Fabric or NeoForge (1.21.1) | Required; use the Fight Club file for your loader |
| Fabric API | Required on Fabric |
| Better Combat | Optional; see [Better Combat](#better-combat) |
| Vanilla Backport | Optional; see [Vanilla Backport](#vanilla-backport) |
| Ice and Fire, Antarchy, Alex's Caves, Oreganized, Caverns & Chasms, Born in Chaos, Eidolon, Galosphere | Optional; each adds weapon materials, see [Built-in mod compatibility](#built-in-mod-compatibility) |

Fight Club is needed on both the server and the client.

## Weapons and moves

| Weapon | Attack | Hold attack | Right-click | Notes |
| --- | --- | --- | --- | --- |
| Sword (vanilla) | Normal swing | | Block frontal attacks | |
| Longsword | Wide, slow horizontal sweep that hits everything in an arc, even when the swing hits only air | | Hold to charge, release a full charge to spin and hit everything around you (then a cooldown) | No critical hits. Slower with anything in the offhand. Short recovery after each hit. The spin also works with Better Combat. |
| Spear | Long-reach thrust that pierces mobs lined up behind the target | Lower the spear; release to strike harder, or run into a mob to hit it without releasing | Throw | |
| Dagger | Fast stab | Pounce on the mob nearest your crosshair for a big hit | Throw | The pounce target is outlined while the charge is ready. |
| Trident (vanilla) | Thrust that pierces like a spear | Lower and charge like a spear | Throw (vanilla) | Unenchanted tridents get Loyalty III. |
| Mace (vanilla) | Sweeps | | | Each consecutive hit attacks faster (up to 3 stacks); a miss or a pause ends the streak. |
| Shield (vanilla) | | | Block, within a narrower frontal angle | Press attack while blocking to bash enemies back. |

Charged hits and throws start a short **recovery**: the weapon lowers, the hotbar slot shows a cooldown, and the weapon can't be used until it ends.

Longswords, spears and daggers come in wood, stone, copper, iron, gold, diamond and netherite. Their item ids are `fight_club:<material>_<weapon>`, such as `fight_club:diamond_longsword`. They're in the vanilla Combat tab and in Fight Club's own creative tab, and are crafted like vanilla tools: a longsword is two material over a stick, a spear one material over two sticks, a dagger one material over one stick. Netherite uses netherite ingots the same way.

Fight Club also removes the anvil's "Too Expensive!" limit and lets potions stack to 16. Both can be turned off.

## Configuration

Fight Club keeps its settings in `config/fight_club.toml`. It creates the file on first launch, adds new options with their defaults when an update brings them, keeps every value you set, and re-reads the file on `/reload`. A value it can't read logs a warning and that option keeps its previous value.

On a server, the server's `[gameplay]` and `[vanilla]` values are sent to every player so tooltips and controls match. `[client]` options only affect the game they're set in.

| Section | Option | Default | Effect |
| --- | --- | --- | --- |
| `gameplay` | `sword_blocking` | `true` | Right-click with a sword to block frontal attacks. |
| `gameplay` | `shield_bash` | `true` | Press attack while blocking with a shield to bash enemies back. |
| `gameplay` | `narrow_shield_angle` | `true` | Shields only block within their blocking angle (`weapons/base/shield.json`) instead of vanilla's 180 degrees. |
| `gameplay` | `longsword_offhand_penalty` | `true` | Longswords attack slower while anything is in the offhand. |
| `gameplay` | `longsword_no_crits` | `true` | Longswords can't land normal critical hits. |
| `gameplay` | `thrust_penetration` | `true` | Spear and trident thrusts pierce mobs lined up behind the target. |
| `gameplay` | `data_driven_sweeps` | `true` | Sweeps use each weapon's sweep radius and angle. Off: vanilla sweeps. |
| `gameplay` | `charged_attacks` | `true` | Spear and trident charges, and dagger pounces. |
| `gameplay` | `running_charge` | `true` | With a spear or trident lowered, running into a mob strikes it without releasing. |
| `gameplay` | `weapon_throwing` | `true` | Right-click to throw spears and daggers. |
| `gameplay` | `longsword_spin` | `true` | Hold right-click with a longsword to charge a spin attack that hits everything around you. |
| `gameplay` | `mace_hit_streak` | `true` | Consecutive mace hits stack an attack speed bonus; a miss or a pause ends the streak. |
| `gameplay` | `recovery` | `true` | Special attacks have a short recovery before the weapon can be used again. |
| `vanilla` | `remove_anvil_limit` | `true` | Removes "Too Expensive!". XP costs still rise as in vanilla. |
| `vanilla` | `potion_stack_size` | `16` | Stack size for potions, splash potions and lingering potions, 1 to 64 (1 = vanilla). Needs a restart. |
| `client` | `animations` | `true` | Fight Club's weapon poses and attack animations. |
| `client` | `swing_sounds` | `true` | Swing whooshes for daggers, spears, tridents and longswords. Always off with Better Combat, which has its own. |
| `client` | `charge_indicator` | `true` | Show charge progress on the attack indicator. |
| `client` | `pounce_outline` | `true` | Outline the mob a charged dagger will pounce on. |
| `client` | `pounce_fov` | `true` | Narrow the view when a pounce is ready and widen it during the dash (scaled by FOV Effects). |

Weapon numbers aren't in the config. They're in the weapon spec files.

## Weapon specs

Every weapon's stats come from a JSON file at `data/<namespace>/weapons/<material>/<weapon>.json`. The namespace doesn't matter; the material and weapon folders do. Fight Club ships one for each of its weapons, plus `base/shield.json`, `base/trident.json` and `base/mace.json` for the vanilla items. To change a weapon, put a file with the same material and weapon in a datapack. Changes apply on `/reload`, including the item's attribute tooltip, and the server sends its specs to every player.

Every field below is required unless it has a default. A file with a missing field or an out-of-range value is skipped with an error in the log that names the file and field, and the weapon keeps its bundled stats.

| Field | Type and range | Meaning |
| --- | --- | --- |
| `weapon` | string | `longsword`, `spear`, `dagger`, or `shield` / `trident` / `mace` for the base files |
| `material` | string | Material name, matching the folder |
| `attack_style` | `sweep`, `thrust`, `impact` or `single_target` | How a normal hit spreads |
| `attack_damage` | 0 to 1000 | Total attack damage, including the player's base 1 |
| `attack_speed` | 0.01 to 100 | Attacks per second, like the vanilla tooltip |
| `reach` | 0 to 32 | Attack reach in blocks (vanilla is 3) |
| `sweep_radius`, `sweep_angle_degrees` | 0 to 32, 0 to 360 | Area of a `sweep` hit |
| `thrust_width`, `penetration_count` | 0 to 16, 0 to 64 | Width of a `thrust` and how many mobs behind the target it also hits |
| `critical_hits` | boolean | Whether jump crits work |
| `offhand_speed_multiplier` | 0 to 1 | Attack speed multiplier while the offhand holds anything (longswords) |
| `charge_ticks` | 0 to 1200 | Ticks to hold attack before a charge or pounce is ready (20 ticks = 1 second). Longswords: ticks to hold right-click before a spin is ready (0 disables the spin) |
| `charge_damage`, `charge_knockback` | 0 to 1000, 0 to 100 | Damage and knockback of a charged hit (longswords: of the spin, pushing outward) |
| `spin_radius`, `spin_cooldown_ticks` | 0 to 16 (default 3), 0 to 1200 (default 60) | Longswords: reach of the spin around the player, and the item cooldown after it |
| `hit_streak_speed_bonus`, `hit_streak_max_stacks`, `hit_streak_window_ticks` | 0 to 10 (default 0), 0 to 64 (default 0), 0 to 1200 (default 30) | Mace: attack speed bonus per consecutive hit (0.2 = +20%), the stack cap, and how long after the attack cooldown refills a streak waits for the next hit |
| `recovery_ticks` | 0 to 1200 | Recovery after a special attack |
| `blocking`, `blocking_angle_degrees`, `blocking_damage_multiplier` | boolean, 0 to 360, 0 to 1 | Blocking cone and the share of damage that gets through. The multiplier must be below 1 when `blocking` is true. |
| `throwing`, `throw_velocity`, `throw_range` | boolean, 0 to 100, 0 to 256 | Throwing. Velocity and range must be above 0 when `throwing` is true. |
| `throw_charge_ticks` | 0 to 1200, default 0 | Ticks to hold right-click before a throw can be released |
| `pounce_range`, `pounce_angle_degrees` | 0 to 32 (default 6), 0 to 90 (default 30) | Dagger pounce distance and aiming cone half-angle |
| `bash_hit_cooldown_ticks` | 0 to 1200, default 60 | Shields: ticks before the next bash after a bash that hits something (blocking is unaffected). A bash that hits nothing only waits `recovery_ticks`. |

For example, this makes iron spears hit harder and throw farther. Copy the bundled file and change what you need, because a datapack file replaces the whole spec rather than merging with it:

```json
{
  "weapon": "spear",
  "material": "iron",
  "attack_style": "thrust",
  "attack_damage": 6.5,
  "attack_speed": 1.4,
  "reach": 4.5,
  "sweep_radius": 0,
  "sweep_angle_degrees": 0,
  "thrust_width": 0.55,
  "penetration_count": 2,
  "critical_hits": true,
  "offhand_speed_multiplier": 1,
  "charge_ticks": 20,
  "charge_damage": 0,
  "charge_knockback": 2,
  "recovery_ticks": 0,
  "blocking": false,
  "blocking_angle_degrees": 0,
  "blocking_damage_multiplier": 1,
  "throwing": true,
  "throw_charge_ticks": 10,
  "throw_velocity": 1.6,
  "throw_range": 16
}
```

Save it as `data/mypack/weapons/iron/spear.json` in your datapack.

## Built-in mod compatibility

### Extra weapon materials

When one of these mods is installed, Fight Club registers a longsword, spear and dagger in that mod's material, with recipes, textures and specs. Nothing is registered when the mod is absent, and none of them are required. Item ids are `fight_club:<folder>/<material>/<weapon>`, such as `fight_club:iceandfire/dragonsteel_fire/longsword`, and specs use the material name, such as `weapons/dragonsteel_fire/longsword.json`.

| Material | Enabled by | Item folder | Durability | Longsword / spear / dagger damage | Recipe | Repaired with |
| --- | --- | --- | --- | --- | --- | --- |
| Silver | Ice and Fire, Eidolon, Caverns & Chasms, Galosphere or Oreganized | `generic/silver` | 460 | 9.8 / 5.6 / 2.8 | `#c:ingots/silver` and sticks | `#c:ingots/silver` |
| Electrum | Oreganized | `oreganized/electrum` | 1561 | 11.9 / 6.8 / 3.4 | Smithing: diamond Fight Club weapon + electrum ingot | Electrum ingot |
| Necromium | Caverns & Chasms | `caverns_and_chasms/necromium` | 2031 | 13.3 / 7.6 / 3.8 | Smithing: diamond Fight Club weapon + necromium ingot | Necromium ingot |
| Dark metal | Born in Chaos | `born_in_chaos/dark_metal` | 1100 | 12.6 / 7.2 / 3.6 | Dark metal ingots and a bone handle | Dark metal ingot |
| Desert and jungle myrmex chitin | Ice and Fire | `iceandfire/myrmex_desert`, `iceandfire/myrmex_jungle` | 600 | 11.2 / 6.4 / 3.2 | Myrmex chitin and sticks | Myrmex chitin |
| Dragonbone | Ice and Fire | `iceandfire/dragonbone` | 1660 | 13.3 / 7.6 / 3.8 | Dragonbone and witherbone | Dragonbone |
| Flamed, iced and electrified dragonbone | Ice and Fire | `iceandfire/dragonbone_fire`, `_ice`, `_lightning` | 2000 | 14.7 / 8.4 / 4.2 | Dragonbone weapon + fire, ice or lightning dragon blood | Dragonbone |
| Fire, ice and lightning dragonsteel | Ice and Fire | `iceandfire/dragonsteel_fire`, `_ice`, `_lightning` | 8000 | 16.8 / 9.6 / 4.8 | Dragonsteel ingots and witherbone | Dragonsteel ingot |
| Scarlet neodymium | Alex's Caves | `alexscaves/scarlet_neodymium` | 2031 | 14.0 / 8.0 / 4.0 | Scarlet neodymium ingots and sticks | Scarlet neodymium ingot |
| Ultimate | Antarchy | `antarchy/ultimate` | 3072 | 47.6 / 27.2 / 13.6 | Uranium ingot and titanium ingot on a netherite Fight Club weapon | Titanium ingot |

The damage column is the bundled spec; pack developers can change it like any other [weapon spec](#weapon-specs). The flamed, iced and electrified dragonbone and the three dragonsteels share one row each because their stats match. The Ultimate weapons are new Fight Club items alongside Antarchy's own ultimate sword and basilisk dagger; they don't replace them.

### Better Combat

Fight Club works with Better Combat installed, and ships Better Combat weapon attributes for all of its weapons (longswords swing like claymores, spears and daggers use Better Combat's spear and dagger animations) and for vanilla tridents, maces, axes and backport spears. With Better Combat:

- Better Combat plays the swing animations and sounds. Fight Club's own swing sounds turn off.
- Fight Club pauses Better Combat's attacks, including hold-to-attack, while a spear charge or dagger pounce is held, so the charge and the swing don't fight each other.
- Extra reach comes from the `range_bonus` in Fight Club's weapon attribute files instead of Fight Club's own reach modifier, so it isn't applied twice.

### Vanilla Backport

When Vanilla Backport (`vanillabackport`) is installed, its wood through netherite spears become Fight Club spears for players: they charge, throw and pierce like Fight Club's, and use Fight Club's held models. Fight Club doesn't register its own spears in those materials, so there's only one of each. Mobs holding backport spears keep the backport's own spear behavior.

## Java API

Other mods can add weapons that use Fight Club's longsword, spear or dagger move set, with their own items, textures, recipes and stats. Everything is in `com.craisinlord.fightclub.api` and works the same on Fabric and NeoForge. The mod id is `fight_club`.

| Class | Purpose |
| --- | --- |
| `FightClubApi` | Create weapon items, register specs and held models, and query stacks and players |
| `WeaponType` | `LONGSWORD`, `SPEAR` or `DAGGER` |
| `WeaponSpecBuilder` | Build a spec in code, starting from one of Fight Club's |

`FightClubApi.API_VERSION` is `1` and goes up when the API changes.

### Adding a weapon

A weapon is an item plus a spec. Create the item with `FightClubApi.createWeapon` and register it yourself, in your namespace, the same way you register your other items:

```java
public static final Item MYTHRIL_LONGSWORD = FightClubApi.createWeapon(
        WeaponType.LONGSWORD, "examplemod_mythril", ExampleTiers.MYTHRIL);

Registry.register(BuiltInRegistries.ITEM,
        ResourceLocation.fromNamespaceAndPath("examplemod", "mythril_longsword"), MYTHRIL_LONGSWORD);
```

The overload `createWeapon(type, material, tier, properties)` keeps extra `Item.Properties` such as `fireResistant()` or a rarity. Weapons never stack.

- The **tier** supplies durability, enchantability, the repair ingredient and drop rules, just like a vanilla tool tier. It doesn't change damage.
- The **material** is the name of the spec the item reads. Material names are shared by every mod and datapack, so give yours a prefix, like `examplemod_mythril`. Use one of Fight Club's names, such as `diamond` or `silver`, only if you want your item to share that weapon's stats.

The item gets everything else from Fight Club: the move set, recovery, the tooltip lines, and attribute values that follow the spec live.

### Giving it stats

Ship a spec file at `data/examplemod/weapons/examplemod_mythril/longsword.json`, in the same format as [Weapon specs](#weapon-specs). Datapacks can then override it.

Data files only load once a world does, so also register a default in code. It covers the main menu, creative-tab tooltips, and clients of servers that don't have your data. It's also a quick way to make a spec without writing every field, since the builder starts from Fight Club's iron spec of the same type:

```java
FightClubApi.registerDefaultSpec(
        FightClubApi.specBuilder("examplemod_mythril", WeaponType.LONGSWORD)
                .copyOf("diamond")       // start from Fight Club's diamond longsword
                .attackDamage(11.5f)
                .attackSpeed(1.1f)
                .build());
```

Call it from common init so both sides have it. A datapack file with the same material and weapon always wins over it. The builder has methods for the common fields (`attackDamage`, `attackSpeed`, `reach`, `sweep`, `thrust`, `charge`, `recoveryTicks`, `throwing`, `throwChargeTicks`, `pounce`, ...) and `set(field, value)` for anything else, using the field names from the spec table. `build()` checks the spec like a file and throws `JsonParseException` naming the bad field.

### Models, textures and the rest

Your mod provides its own assets and data, like any item:

| What | Where |
| --- | --- |
| Item model and texture | `assets/examplemod/models/item/mythril_longsword.json`, as for any handheld item |
| Held 3D model (optional) | `assets/examplemod/models/item/mythril_longsword_in_hand.json`, then call `FightClubApi.registerInHandModel(ResourceLocation.fromNamespaceAndPath("examplemod", "mythril_longsword"))` from common init. Fight Club uses it in hand and when thrown and keeps the normal model in GUIs, on the ground and in item frames, like the vanilla trident. Fight Club's own longswords and spears do this; daggers use the normal model everywhere. |
| Name | Your lang file, `item.examplemod.mythril_longsword` |
| Recipe | Your datapack |
| Creative tab | Add the item to a tab yourself. Fight Club's tab only lists its own items. |
| Better Combat (optional) | `data/examplemod/weapon_attributes/mythril_longsword.json`, for example `{"parent": "bettercombat:claymore", "attributes": {"range_bonus": 0.5}}` for a longsword, `bettercombat:spear` with `range_bonus` 1.5 for a spear, or `bettercombat:dagger`. With Better Combat installed, reach comes from `range_bonus`. |

### Queries

These work on any Fight Club weapon: Fight Club's own, ones from other mods, and backport spears.

| Method | Returns |
| --- | --- |
| `isWeapon(stack)` | Whether the stack is a Fight Club weapon |
| `weaponType(stack)` | `Optional<WeaponType>` |
| `material(stack)` | `Optional<String>` spec material |
| `spec(stack)` | `Optional<WeaponSpec>`: the spec in effect, from a datapack, then `registerDefaultSpec`, then Fight Club's bundled file |
| `spec(material, type)` | The spec in effect for a material and type, or `null` |
| `isRecovering(player)` | Whether the player is in recovery and can't use a weapon |
| `isCharging(player)` | Server side: whether the player is holding a spear charge or dagger pounce |

### Making Fight Club optional

To support Fight Club without requiring it, keep every Fight Club reference in a class that's only loaded when it's installed:

```java
// In your common init
if (Platform.isModLoaded("fight_club")) ExampleFightClubCompat.register();
```

```java
final class ExampleFightClubCompat {
    static void register() {
        Item longsword = FightClubApi.createWeapon(WeaponType.LONGSWORD, "examplemod_mythril", ExampleTiers.MYTHRIL);
        // register the item, default spec and in-hand model as above
    }
}
```

`Platform.isModLoaded` stands for your loader's check: `FabricLoader.getInstance().isModLoaded` on Fabric, `ModList.get().isLoaded` on NeoForge. Make the spec files and recipes conditional as well (`fabric:load_conditions` and `neoforge:conditions` with a `mod_loaded` check on `fight_club`), the way Fight Club's own compat recipes check for their mods.

## Troubleshooting

- **A weapon's stats didn't change after editing its spec.** Check the log for `Ignoring invalid weapon spec` with the file and field name. A datapack file must be at `data/<namespace>/weapons/<material>/<weapon>.json`, exactly two folders deep, and contain every required field.
- **The log says `Loaded N Fight Club weapon specifications`.** That's the number of spec files the server loaded on world start or `/reload`.
- **A compat material's weapons are missing.** They only register when the mod that provides the material is installed. The log says `Fight Club: enabled <material> weapons` for each one.
- **Spear charges feel wrong with Better Combat.** Make sure both mods are on the server and every client.
