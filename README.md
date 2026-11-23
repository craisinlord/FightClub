# Fight Club

Fight Club is a combat overhaul for Minecraft 1.21.1 on Fabric and NeoForge. It adds longswords, spears and daggers in every vanilla material, and gives swords, shields, tridents and maces new moves: sword blocking, shield bashes, spear charges, dagger pounces, thrown weapons, a longsword spin and a mace hit streak. Every weapon number is data driven, and everything is very configurable.

This README is for players, modpack developers and mod developers. Pack developers mostly need [Configuration](#configuration), [Weapon specs](#weapon-specs) and, if the pack has other weapon mods, [Weapon mods](#weapon-mods). Mod developers adding weapons should read [Java API](#java-api).

**Version parity.** The development workspace also contains forward-port targets for Minecraft 26.1, 26.2 and 26.3 on Fabric and NeoForge. Those ports are still in progress and not ready for release. They use vanilla's spears instead of Fight Club's, and don't have the [built-in mod compatibility](#built-in-mod-compatibility) materials or the [Java API](#java-api) yet. [Weapon mods](#weapon-mods) support is in them; see [On 26.x](#on-26x). Everything else below describes the 1.21.1 builds.

## Contents

- [Requirements and integrations](#requirements-and-integrations)
- [Weapons and moves](#weapons-and-moves)
- [Configuration](#configuration)
- [Weapon specs](#weapon-specs): file locations, fields, overriding a weapon
- [Other mods' items](#other-mods-items): giving another mod's dagger, longsword, spear or shield Fight Club's moves by datapack
- [Built-in mod compatibility](#built-in-mod-compatibility): extra materials and their perks, Better Combat, Vanilla Backport
- [Weapon mods](#weapon-mods): Silent Gear, Simply Swords, Spartan Weaponry and Farmer's Delight knives, for players and modpack developers
- [Java API](#java-api): adding weapons from another mod, specs in code, models, queries
- [Troubleshooting](#troubleshooting)

## Requirements and integrations

| Mod | Status |
| --- | --- |
| Fabric or NeoForge (1.21.1) | Required; use the Fight Club file for your loader |
| Fabric API | Required on Fabric |
| Better Combat | Optional; see [Better Combat](#better-combat) |
| Vanilla Backport | Optional; see [Vanilla Backport](#vanilla-backport) |
| Silent Gear | Optional (NeoForge); see [Silent Gear](#silent-gear) |
| Simply Swords | Optional; see [Simply Swords](#simply-swords) |
| Spartan Weaponry Reborn or Spartan Weaponry Unofficial | Optional (NeoForge); see [Spartan Weaponry](#spartan-weaponry) |
| Farmer's Delight or Farmer's Delight Refabricated | Optional; see [Farmer's Delight](#farmers-delight) |
| JEI | Optional; hidden Fight Club weapons stay out of its item list, see [Weapon mods](#weapon-mods) |
| Ice and Fire, Antarchy, The Aether, Twilight Forest, Mekanism, Deeper and Darker, The Undergarden, Oreganized, Caverns & Chasms, Born in Chaos, Eidolon, Galosphere | Optional; each adds weapon materials, see [Built-in mod compatibility](#built-in-mod-compatibility) (1.21.1 only) |

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
| `gameplay` | `potion_throw_cooldown` | `true` | Splash and lingering potions get a half-second throw cooldown (half an ender pearl's) so stacked potions can't be spammed. |
| `gameplay` | `hide_replaced_weapons` | `true` | Hide Fight Club weapons (no recipe, creative tab, JEI entry or mob spawns) when another mod's weapon takes over that weapon and material, such as Silent Gear's daggers. Existing items keep working. |
| `vanilla` | `remove_anvil_limit` | `true` | Removes "Too Expensive!". XP costs still rise as in vanilla. |
| `vanilla` | `potion_stack_size` | `16` | Stack size for potions, splash potions and lingering potions, 1 to 64 (1 = vanilla). Needs a restart. |
| `client` | `animations` | `true` | Fight Club's weapon poses and attack animations. |
| `client` | `swing_sounds` | `true` | Swing whooshes for swords, daggers, longswords, axes, maces, spears and tridents. Always off with Better Combat, which has its own. |
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

## Other mods' items

A datapack can give another mod's item a Fight Club weapon's moves, with no code. Put a file at `data/<namespace>/weapon_items/<name>.json`:

```json
{
  "weapon": "dagger",
  "material": "iron",
  "items": ["examplemod:bone_dagger", "#c:tools/daggers"]
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `weapon` | required | `dagger`, `longsword`, `spear`, `sword` or `shield` |
| `material` | `iron` (`base` for shields) | Which spec the items use: `weapons/<material>/<weapon>.json`. Use one of Fight Club's, or ship your own spec next to the file |
| `items` | required | Item ids, or item tags starting with `#`. Ids of mods that aren't installed are skipped, so one file can cover optional mods |
| `right_click` | `true` | Right click throws daggers and spears and charges the longsword spin. Set it to `false` for items that already do something on right click; they keep that and still get the left-click moves |
| `spec_stats` | `false` | Replace the item's own attack damage and speed with the spec's. By default the item keeps its own, and the spec supplies everything else (reach, sweeps, charges, throws, recovery) |
| `spec` | none | Spec fields that differ from the material's spec, such as `charge_damage` or `throw_damage`. The items get their own copy of the spec with these fields replaced, so each weapon's ability damage can be tuned on its own |
| `replaces` | none | Fight Club items these items take the place of: a list of materials such as `["iron", "gold"]`, or `"all"` for wood through netherite. When one of the listed item ids is installed, Fight Club's `<material>_<weapon>` items lose their recipes and leave the creative tab, JEI and mob spawns. Existing ones keep working. The `hide_replaced_weapons` config option turns this off |

For example, a dagger that keeps its own hit damage but pounces for 18 and throws for 9:

```json
{
  "weapon": "dagger",
  "material": "iron",
  "items": ["examplemod:bone_dagger"],
  "spec": { "charge_damage": 18, "throw_damage": 9 }
}
```

`charge_damage` is the whole charged hit (dagger pounce, spear charge) and the longsword spin's damage; `throw_damage` is the thrown weapon's hit. For items that keep their own damage, `charge_damage` and `throw_damage` scale with how hard the item hits compared to the spec's `attack_damage`: an item that hits twice as hard as the iron dagger spec also pounces and throws twice as hard. This keeps one file right for weapons whose damage depends on what they're made of, like Silent Gear's.

Mapped items get the full move set of their weapon: dagger pounce and throw, longsword arc sweep and spin, spear charge, thrust and throw, and for shields the bash, the narrower block angle and the bash pose. Fight Club's own items always use their own spec, and an id listed in a file wins over a tag. Changes apply on `/reload`, and the server sends the mappings to every player with the specs.

## Built-in mod compatibility

### Extra weapon materials

When one of these mods is installed, Fight Club registers a longsword, spear and dagger in that mod's material, with recipes, textures and specs. These materials are in the 1.21.1 versions only. Nothing is registered when the mod is absent, and none of them are required. Item ids are `fight_club:<folder>/<material>/<weapon>`, such as `fight_club:iceandfire/dragonsteel_fire/longsword`, and specs use the material name, such as `weapons/dragonsteel_fire/longsword.json`.

| Material | Enabled by | Item folder | Durability | Longsword / spear / dagger damage | Recipe | Repaired with |
| --- | --- | --- | --- | --- | --- | --- |
| Silver | Ice and Fire, Eidolon, Caverns & Chasms, Galosphere or Oreganized | `generic/silver` | 460 | 9.8 / 5.6 / 2.8 | `#c:ingots/silver` and sticks | `#c:ingots/silver` |
| Electrum | Oreganized | `oreganized/electrum` | 1561 | 11.9 / 6.8 / 3.4 | Smithing: diamond Fight Club weapon + electrum ingot | Electrum ingot |
| Necromium | Caverns & Chasms | `caverns_and_chasms/necromium` | 2031 | 13.3 / 7.6 / 3.8 | Smithing: diamond Fight Club weapon + necromium ingot | Necromium ingot |
| Dark metal | Born in Chaos | `born_in_chaos/dark_metal` | 1100 | 12.6 / 7.2 / 3.6 | Dark metal ingots and a bone handle | Dark metal ingot |
| Dragonbone | Ice and Fire | `iceandfire/dragonbone` | 1660 | 13.3 / 7.6 / 3.8 | Dragonbone and witherbone | Dragonbone |
| Flamed, iced and electrified dragonbone | Ice and Fire | `iceandfire/dragonbone_fire`, `_ice`, `_lightning` | 2000 | 14.7 / 8.4 / 4.2 | Dragonbone weapon + fire, ice or lightning dragon blood | Dragonbone |
| Fire, ice and lightning dragonsteel | Ice and Fire | `iceandfire/dragonsteel_fire`, `_ice`, `_lightning` | 8000 | 39.2 / 22.4 / 11.2 | Dragonsteel ingots and witherbone | Dragonsteel ingot |
| Ultimate | Antarchy | `antarchy/ultimate` | 3072 | 47.6 / 27.2 / 13.6 | Uranium ingot and titanium ingot on a netherite Fight Club weapon | Titanium ingot |
| Zanite | The Aether | `aether/zanite` | 250 | 9.8 / 5.6 / 2.8 | Zanite gemstones and a skyroot stick | Zanite gemstone |
| Gravitite | The Aether | `aether/gravitite` | 1561 | 11.9 / 6.8 / 3.4 | Enchanted gravitite and a skyroot stick | Enchanted gravitite |
| Ironwood | Twilight Forest | `twilightforest/ironwood` | 512 | 9.8 / 5.6 / 2.8 | Ironwood ingots and sticks | Ironwood ingot |
| Steeleaf | Twilight Forest | `twilightforest/steeleaf` | 131 | 11.9 / 6.8 / 3.4 | Steeleaf and sticks | Steeleaf |
| Knightmetal | Twilight Forest | `twilightforest/knightmetal` | 512 | 11.9 / 6.8 / 3.4 | Knightmetal ingots and sticks | Knightmetal ingot |
| Fiery | Twilight Forest | `twilightforest/fiery` | 1024 | 13.3 / 7.6 / 3.8 | Fiery ingots and a blaze rod | Fiery ingot |
| Bronze | Mekanism | `mekanism/bronze` | 375 | 9.8 / 5.6 / 2.8 | Bronze ingots and sticks | Bronze ingot |
| Lapis lazuli | Mekanism | `mekanism/lapis_lazuli` | 128 | 8.4 / 4.8 / 2.4 | Lapis lazuli and sticks | Lapis lazuli |
| Osmium | Mekanism | `mekanism/osmium` | 1024 | 13.3 / 7.6 / 3.8 | Osmium ingots and sticks | Osmium ingot |
| Refined glowstone | Mekanism | `mekanism/refined_glowstone` | 384 | 9.8 / 5.6 / 2.8 | Refined glowstone ingots and sticks | Refined glowstone ingot |
| Refined obsidian | Mekanism | `mekanism/refined_obsidian` | 4096 | 17.5 / 10.0 / 5.0 | Refined obsidian ingots and sticks | Refined obsidian ingot |
| Steel | Mekanism | `mekanism/steel` | 500 | 11.9 / 6.8 / 3.4 | Steel ingots and an iron ingot handle | Steel ingot |
| Resonarium | Deeper and Darker | `deeperdarker/resonarium` | 1601 | 11.9 / 6.8 / 3.4 | Smithing: diamond Fight Club weapon + resonarium template + resonarium plate | Resonarium plate |
| Warden | Deeper and Darker | `deeperdarker/warden` | 2519 | 14.7 / 8.4 / 4.2 | Smithing: netherite Fight Club weapon + warden template + reinforced echo shard | Reinforced echo shard |
| Cloggrum | The Undergarden | `undergarden/cloggrum` | 286 | 11.9 / 6.8 / 3.4 | Cloggrum ingots and sticks | Cloggrum ingot |
| Froststeel | The Undergarden | `undergarden/froststeel` | 575 | 9.8 / 5.6 / 2.8 | Froststeel ingots and sticks | Froststeel ingot |
| Utherium | The Undergarden | `undergarden/utherium` | 1279 | 12.6 / 7.2 / 3.6 | Utherium crystals and sticks | Utherium crystal |
| Forgotten | The Undergarden | `undergarden/forgotten` | 1876 | 11.9 / 6.8 / 3.4 | Smithing: cloggrum weapon + forgotten template + forgotten ingot | Forgotten ingot |

The damage column is the bundled spec; pack developers can change it like any other [weapon spec](#weapon-specs). The flamed, iced and electrified dragonbone and the three dragonsteels share one row each because their stats match. Dragonsteel follows Ice and Fire's `dragonSteelBaseDamage` and `dragonSteelBaseDurability` settings: the damage numbers are for the default of 25 and scale with it, so a modpack that buffs or nerfs Ice and Fire's dragonsteel sword moves Fight Club's dragonsteel weapons the same way. The Ultimate weapons are new Fight Club items alongside Antarchy's own ultimate sword and basilisk dagger; they don't replace them.

The Mekanism materials are enabled by Mekanism itself, which provides the ingots; Mekanism Tools isn't needed.

### Material perks

Compat weapons keep what the source mod's own sword does. Perks apply to all three weapons, on every kind of hit: normal swings, sweeps, pierces, charges, spins and thrown hits.

| Material | Perk |
| --- | --- |
| Silver | Ice and Fire's bonus damage against undead |
| Flamed, iced and electrified dragonbone; fire, ice and lightning dragonsteel | Ice and Fire's own dragon-blood and dragonsteel effects (burn, freeze or lightning on hit) |
| Dark metal | Born in Chaos's own on-hit effect for its sharpened dark metal sword |
| Zanite | Grows stronger as it loses durability, by the Aether's formula: up to 2.5x the base damage when nearly broken |
| Gravitite | Full-strength hits launch a grounded target into the air |
| Ironwood | Crafted with Knockback I, like Twilight Forest's ironwood sword |
| Steeleaf | Crafted with Looting II, like Twilight Forest's steeleaf sword |
| Knightmetal | +2 damage against targets in a full armor set or with natural armor |
| Fiery | Sets targets on fire for 15 seconds; fire-resistant item, Uncommon rarity |
| Refined obsidian | Fire-resistant item |
| Refined glowstone | Piglins are drawn to it, like Mekanism's refined glowstone sword |
| Warden | Fire-resistant item, Rare rarity |
| Froststeel | Hits apply the Undergarden's Chilly effect (level III, 30 seconds) |
| Utherium | 1.5x damage against Rotspawn |
| Forgotten | 1.5x damage against Undergarden creatures, except bosses |

Bronze, lapis lazuli, osmium, steel, resonarium, cloggrum, electrum, necromium, plain dragonbone and ultimate have no perk, because the source mod's sword has none. Perk tooltips use the source mod's own text where it has one.

### Better Combat

Fight Club works with Better Combat installed, and ships Better Combat weapon attributes for all of its weapons (longswords swing like claymores, spears and daggers use Better Combat's spear and dagger animations) and for vanilla tridents, maces, axes and backport spears. With Better Combat:

- Better Combat plays the swing animations and sounds. Fight Club's own swing sounds turn off.
- Fight Club pauses Better Combat's attacks, including hold-to-attack, while a spear charge or dagger pounce is held, so the charge and the swing don't fight each other.
- Extra reach comes from the `range_bonus` in Fight Club's weapon attribute files instead of Fight Club's own reach modifier, so it isn't applied twice.

### Vanilla Backport

When Vanilla Backport (`vanillabackport`) is installed, its wood through netherite spears become Fight Club spears for players: they charge, throw and pierce like Fight Club's, and use Fight Club's held models. Fight Club doesn't register its own spears in those materials, so there's only one of each. Mobs holding backport spears keep the backport's own spear behavior.

## Weapon mods

Fight Club works alongside other weapon mods instead of competing with them. When Silent Gear, Simply Swords, Spartan Weaponry or Farmer's Delight is installed, their weapons get Fight Club's moves for what they are: their daggers and knives pounce and throw, their longswords, claymores and katanas sweep and spin, and their spears charge and throw. Fight Club's own versions of those weapons then step aside, so a pack doesn't end up with two iron daggers. There's nothing to set up: install the mods together, on the server and every client.

### For players

- **Their weapons, our moves.** A mapped weapon keeps its own look, damage, attack speed, durability, enchantments and the other mod's passive traits. Fight Club adds the moves of its weapon type, listed for each mod below, and the matching tooltip lines.
- **Ability damage follows the weapon.** Pounce, charge, spin and throw damage scale with the weapon's own hit damage, so a stronger weapon pounces and throws harder. A netherite Silent Gear dagger hits harder than a flint one, and a Simply Swords claymore spins harder than its longsword.
- **Right-click.** On mapped daggers and spears, right-click is Fight Club's throw; on mapped longswords it's the spin. This replaces the other mod's own throw on those items, such as Simply Swords' instant spear throw or Spartan's dagger throw. Weapons that aren't mapped keep everything they had.
- **Held and thrown.** The weapon keeps its own model in hand, in flight and stuck in a wall, held in Fight Club's poses.
- **Fight Club's duplicates are hidden.** When another mod covers a weapon and material, Fight Club's version has no recipe, isn't in the creative tabs or JEI, and mobs stop spawning with it. It's still in the game: ones you already have keep working, and `/give` still works. Only the materials the other mod also has are hidden: with Simply Swords, Fight Club's wood, stone and copper weapons stay, because Simply Swords starts at iron, and with Farmer's Delight only the daggers its knives match are hidden.
- **Reach doesn't stack.** A weapon gets its own reach or Fight Club's, whichever is longer, never both.
- **Better Combat.** Simply Swords and Spartan keep their own Better Combat animations, which are made for their weapons. Silent Gear weapons use Fight Club's.

### For modpack developers

Every mapping is an ordinary [weapon item file](#other-mods-items) inside Fight Club's jar, at `data/fight_club/weapon_items/<mod>/<weapon>.json`. A datapack file at the same path replaces it, and changes apply on `/reload`.

| To | Do this |
| --- | --- |
| Keep Fight Club's own weapons available as well | Set `hide_replaced_weapons = false` in `config/fight_club.toml`. Nothing is hidden and every recipe stays. |
| Hide only some materials | Override the file and list the materials in `replaces`, such as `["diamond", "netherite"]`. Leave `replaces` out to hide nothing for that weapon. |
| Turn a mapping off | Override the file with an empty list, such as `{"weapon": "longsword", "items": []}`. Those items go back to how their own mod made them, and Fight Club's matching weapons come back. |
| Move a weapon to another type | Take its ids out of one file and add them to another. For example, to make Simply Swords' halberd a spear, remove the halberd ids from `simplyswords/longsword.json` and add them to `simplyswords/spear.json`. Keep each item in only one file. |
| Change a weapon's ability damage | Add a `spec` block, such as `"spec": {"charge_damage": 18, "throw_damage": 9}`. See [Other mods' items](#other-mods-items) for how it scales. |
| Keep an item's own right-click | Add `"right_click": false`. It keeps its right-click and still gets the left-click moves. |
| Map another weapon mod | Write the same kind of file with that mod's item ids or tags. Ids of mods that aren't installed are skipped, so one datapack can cover mods a pack might not have. |

Things worth knowing:

- **Several weapon mods together.** Each mod's files work on their own. A Fight Club weapon is hidden if any installed mod replaces it.
- **Better Combat files.** Fight Club only ships Better Combat attributes for Silent Gear's weapons, in `data/silentgear/weapon_attributes/`. A datapack can override them or add files for the other mods.
- **Mobs.** A mob that would have spawned with a hidden Fight Club weapon spawns with nothing in that hand.
- **The log.** On world load and `/reload`, Fight Club logs `Loaded N Fight Club weapon item mappings`, `N Fight Club weapons are replaced by other mods' items` and `disabled N recipes for weapons replaced by other mods`.

### On 26.x

The 26.x builds include the same weapon mod support: the same mapping files, hiding, recipe removal, damage scaling and held poses, and the JEI plugin. The differences:

- 26.x has vanilla spears instead of Fight Club's, so another mod's spear gets Fight Club's spear moves but doesn't hide anything, and vanilla's spears stay craftable.
- Mod availability decides what works where. Silent Gear has NeoForge builds for 26.1 and 26.2, and Spartan Weaponry Unofficial has one for 26.1. Farmer's Delight Refabricated has Fabric builds for 26.1, 26.2 and 26.3, where its copper knife also hides Fight Club's copper dagger. Simply Swords and Spartan Weaponry Reborn have no 26.x release yet; their mapping files are included and take effect if they get one with the same item ids.

### Silent Gear

When Silent Gear is installed, its weapons get Fight Club's moves for what they are, while keeping Silent Gear's materials, traits and damage:

| Silent Gear | Fight Club weapon |
| --- | --- |
| Dagger, knife | Dagger: pounce and throw |
| Katana | Longsword: arc sweep and spin |
| Spear | Spear: charge, thrust and throw |
| Sword, machete | Sword: block |
| Shield | Shield: bash and narrower block |
| Trident, mace | Trident and mace reworks |

Pounce, charge, spin and throw damage scale with each weapon's own damage, so a netherite dagger pounces harder than a flint one. Fight Club's own daggers, longswords and spears (wood through netherite) are hidden, since Silent Gear's take their place. Fight Club's Better Combat attributes for these weapons replace Silent Gear's, so a katana swings like a Fight Club longsword. The mappings are under `data/fight_club/weapon_items/silentgear/`.

### Simply Swords

When Simply Swords is installed, its iron, gold, diamond and netherite weapons get Fight Club's moves for what they are, while keeping their own damage, speed and look:

| Simply Swords | Fight Club weapon |
| --- | --- |
| Sai | Dagger: pounce and throw |
| Longsword, claymore, twinblade, katana, glaive, halberd | Longsword: arc sweep and spin |
| Spear | Spear: charge, thrust and throw (replaces Simply Swords' instant throw) |
| Cutlass, rapier | Sword: block |

Fight Club's iron, gold, diamond and netherite daggers, longswords and spears are hidden; wood, stone and copper stay, since Simply Swords has none. Runic and unique weapons, scythes, warglaives, chakrams, greataxes and greathammers are left to Simply Swords, along with its own Better Combat animations. The mappings are under `data/fight_club/weapon_items/simplyswords/`.

### Spartan Weaponry

Spartan Weaponry has no official 1.21.1 release, so Fight Club supports both ports: Spartan Weaponry Reborn (and other ports that keep the `spartanweaponry` namespace) and Spartan Weaponry Unofficial (`spartan_weaponry_unofficial`). Weapons are matched by Spartan's own weapon-type tags, so every material, including tin, bronze, steel, silver and add-on materials, is covered:

| Spartan Weaponry | Fight Club weapon |
| --- | --- |
| Dagger | Dagger: pounce and throw (replaces Spartan's throw) |
| Longsword, greatsword, katana, glaive, halberd, quarterstaff | Longsword: arc sweep and spin |
| Spear, pike | Spear: charge, thrust and throw |
| Saber, rapier | Sword: block |

Spartan's passive traits (two-handed, armor piercing, backstab and the rest) still apply. Reach doesn't add up: a weapon whose reach trait already gives it more reach than Fight Club would keeps its own, so a pike reaches further than a Fight Club spear. Fight Club's wood through netherite daggers, longswords and spears are hidden. Parrying daggers, lances, scythes, blunt weapons, axes, throwing weapons and ranged weapons are left to Spartan, along with its own Better Combat animations. The mappings are under `data/fight_club/weapon_items/spartanweaponry/` and `data/fight_club/weapon_items/spartan_weaponry_unofficial/`.

### Farmer's Delight

When Farmer's Delight (NeoForge) or Farmer's Delight Refabricated (Fabric) is installed, its knives become Fight Club daggers: they pounce and throw, and keep their own damage, look and kitchen uses. Cutting boards and harvesting with a knife work as before, since right-clicking a block still goes to the block first; right-clicking the air throws the knife.

Every knife in Farmer's Delight's `farmersdelight:tools/knives` tag is mapped, so knives from Farmer's Delight add-ons that use the tag become daggers too.

Only the daggers Farmer's Delight has its own knife for are hidden: iron, gold, diamond and netherite, plus copper on 26.x where Farmer's Delight has a copper knife. Fight Club's wood, stone and copper daggers (on 1.21.1) and every dagger from [Extra weapon materials](#extra-weapon-materials), such as ultimate or dragonsteel, stay craftable, and add-on knives don't hide anything. The flint knife doesn't hide Fight Club's stone dagger. The mapping is `data/fight_club/weapon_items/farmersdelight/dagger.json`.

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
- **Fight Club's daggers, longswords or spears are missing from the creative tab, JEI or crafting.** Another installed weapon mod covers them; see [Weapon mods](#weapon-mods). Set `hide_replaced_weapons = false` to bring them back.
- **Another mod's weapon doesn't get Fight Club's moves.** Only the types listed under [Weapon mods](#weapon-mods) are mapped. If you wrote your own mapping, check the log for `Ignoring invalid weapon item file`.
