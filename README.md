# Create: Thauma Research

A Thaumaturge addon for NeoForge 1.21.1 that adds a new Thaumonomicon chapter, **Mechanoturgy** (Механотургия). Its research unlocks the machines of [Create](https://github.com/Creators-of-Create/Create).

The research is ordinary Thaumaturge data and works the same way as Thaumaturge's own:
- you open it with observations and theories from the existing chapters;
- you scan things with a given aspect;
- you bring items with you;
- you finish research notes with aspects on the research table.

Each entry reads as a note in the thaumaturge's journal, in English and Russian.

**Opening the chapter:** "Mechanoturgy" in the first chapter, after Artifice (one Artifice observation, plus scans of Motus and Machina).

**Locks:** until you complete the research that unlocks a Create item, every recipe that makes that item is locked (see the research gates below). Its tooltip names the research it needs. Create items that no research unlocks (decorative blocks, materials and so on) stay free.

| Research | Unlocks |
|---|---|
| Mechanoturgy | andesite alloy, wrench |
| The World Moves | shaft, cogwheel, hand crank, andesite casing |
| The Strength of Water / of Wind | water wheels; windmill bearing, mechanical bearing, sails |
| Kinetic Energy | large cogwheel, gearboxes, belt, chain drives |
| Measuring Rotation | engineer's goggles, speedometer, stressometer |
| Governing by Signal | clutch, gearshifts, redstone link, latches, displays |
| Tools of Labour | drill, saw, harvester, plough |
| The Mechanism | press, basin, mixer, millstone, encased fan |
| Logistics / Sorting | depot, chute, funnels, tunnels, vault, ejector; filters, brass funnel/tunnel, mechanical arm |
| Brass | brass casing, electron tube, brass hand, sandpaper |
| Blaze Fire / Liquids / The Steam Boiler | blaze burner; pipes, pump, tanks, spout, drain; steam engine, whistle, flywheel |
| The Precision Mechanism / Schematics | deployer, mechanical crafter, speed controller; schematic table, schematicannon |
| Moving Structures / The Railway | pistons, pulleys, gantries, chassis, glue; train station, signals, controls, schedule |
| Packages and Orders | packager, frogport, stock link, stock ticker, factory gauge |

`tools/gen.py` generates the chapter, its entries, the texts and the chapter's research gates.

## Machines and the aura

Create's machines pollute the aura with flux, the same way Thaumaturge's crucible does. Flux goes into the chunk's aura (with the same particles), and sometimes physical flux spills next to the machine.

What the mod ships with:

| Source | When | Default |
|---|---|---|
| Steam engine (`create:powered_shaft`) | while it turns; scales with rpm | 0.1 flux × rpm/64, half the time, every 10 s; sometimes spills flux |
| Blaze burner, kindled | `blaze = kindled` | 0.05 flux, half the time, every 15 s |
| Blaze burner, seething (blaze cake) | `blaze = seething` | 0.25 flux, 75 % of the time, every 5 s; sometimes spills flux |
| Ore blasting (fan through lava/fire) | each crushed ore, raw ore or ore processed | 0.1 flux per item, half the time |
| Ore washing (fan through water) | each crushed ore, gravel, soul sand or red sand washed | 0.05 flux per item, 30 % of the time |

Fan processing counts loose items, items on belts and items on depots.

### Config

`config/createthaumaresearch-common.toml`:
- `enabled`: whether machines emit flux at all;
- `flux_multiplier`: every emission's flux is multiplied by this;
- `scan_interval`: ticks between scans of the working machines;
- `allow_spill`: whether rules may spill physical flux.

### Your own rules

Put a JSON file in a datapack at `data/<namespace>/createthaumaresearch/aura_emissions/<name>.json`. To change or turn off a built-in rule, replace the file `data/createthaumaresearch/createthaumaresearch/aura_emissions/<name>.json` (set `"flux": 0` to switch it off).

A **block** rule covers any ticking block (a block entity), from Create or any other mod:

```json
{
  "type": "block",
  "blocks": ["create:blaze_burner", "#mymod:furnaces"],
  "state": {"blaze": ["kindled", "seething"]},
  "min_speed": 1,
  "scale_with_speed": false,
  "interval": 100,
  "chance": 0.5,
  "flux": 0.25,
  "spill_chance": 0.0,
  "effect": true
}
```

A **fan processing** rule covers an encased fan processing items:

```json
{
  "type": "fan_processing",
  "processing": ["create:splashing"],
  "items": ["#create:crushed_raw_materials"],
  "chance": 0.3,
  "flux": 0.05
}
```

Fields:
- `blocks`: block ids or block tags (`#…`).
- `state`: the block state properties that must match, each with the values allowed.
- `min_speed`: the minimum rpm a kinetic block must turn at (any block entity with Create's `getSpeed()`).
- `scale_with_speed`: multiply the flux by rpm/64.
- `interval`: ticks between tries.
- `processing`: Create fan processing types (`create:splashing`, `create:blasting`, `create:haunting`, `create:smoking`).
- `items`: item ids or tags the rule is limited to.
- `chance`: chance per try or per item.
- `flux`: how much flux goes into the aura.
- `spill_chance`: chance to also spill physical flux.
- `effect`: whether to show the pollution particles.

## Research gates (locking any recipe)

Thaumaturge only locks its own recipes: the arcane workbench, the crucible and infusion each carry `"research": {"entry", "stage", "negate"}`. The crafting table, furnaces and stonecutter are not locked by research. This mod adds a small library that locks **any** recipe the same way, behind research. [RecipeStages](https://github.com/jaredlll08/RecipeStages) was the model, but here the stages are research.

The check itself is Thaumaturge's own (`ResearchGate.passes`), so `entry`, `stage` and `negate` mean exactly what they mean in Thaumaturge's recipes.

### Gate files

Put a JSON file in a datapack at `data/<namespace>/createthaumaresearch/research_gates/<name>.json`:

```json
{
  "research": {"entry": "thaumaturge:metallurgy", "stage": 1, "negate": false},
  "recipes": ["minecraft:iron_ingot_from_smelting_raw_iron"],
  "outputs": ["create:cogwheel", "#c:gears"],
  "recipe_types": ["minecraft:smithing_transform"],
  "namespaces": ["mymod"],
  "except": ["create:crafting/kinetics/cogwheel_from_conversion"]
}
```

- `research`: the research that opens the recipes. `stage` and `negate` are optional.
- `recipes`: recipe ids.
- `outputs`: every recipe that makes one of these items or tags.
- `recipe_types`: every recipe of these types.
- `namespaces`: every recipe whose id is in these mods.
- `except`: recipes left free.

A recipe matching any of the lists is locked. A recipe under several gates needs all of them.

### Where the gates apply

| Where | Whose research |
|---|---|
| Crafting table, inventory grid | the player crafting; the result slot stays empty |
| Stonecutter, smithing table | the player; the result slot stays empty |
| Furnace, smoker, blast furnace | the player who placed it; the recipe does not burn |
| Create: applying a casing (item application) | the player; the use is refused with a message |
| Create: deployer | its owner (the player who placed it) |
| Create: mechanical crafter | the player who placed the crafter |

Machines remember who placed them. While the owner is offline, the answer from when they were last online is used. Config, in the `research_gates` section:
- `lock_unowned_machines` (default `false`): whether machines nobody placed (world generation, commands) are also refused;
- `gate_furnaces`: turns furnace gating off.

### For other mods

`net.createthaumaresearch.gate.ResearchGates` provides:
- `mayUse(player, recipeHolder, output)`: for a player;
- `mayUse(level, ownerUuid, recipeHolder, output)`: for a machine;
- `gatesFor(recipeHolder, output)`;
- `refusal(...)`: the message to show.

`Owners.ownerOf(blockEntity)` / `setOwner` give a machine's owner.
