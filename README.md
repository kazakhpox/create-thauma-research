# Create: Thauma Research

A Thaumaturge addon for NeoForge 1.21.1 that adds a new Thaumonomicon chapter, **Mechanoturgy** (Механотургия). Its research unlocks the machines of [Create](https://github.com/Creators-of-Create/Create).

The research is ordinary Thaumaturge data and works the same way as Thaumaturge's own:
- you open it with observations and theories from the existing chapters;
- you scan things with a given aspect;
- you bring items with you;
- you finish research notes with aspects on the research table.

Each entry reads as a note in the thaumaturge's journal, in English and Russian.

**Opening the chapter:** "Mechanoturgy" in the first chapter, after Artifice (one Artifice observation, plus scans of Motus and Machina).

**Locks:** until you complete the research that unlocks a Create item:
- the crafting table and inventory grid give nothing for it;
- you cannot apply its casing to a log (andesite, brass and copper casings, and the railway casing);
- its tooltip names the research it needs.

Create items that no research unlocks (decorative blocks, materials and so on) stay free.

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

`tools/gen.py` generates the chapter, its entries, the texts and the lock list (`data/createthaumaresearch/research_locks.json`).
