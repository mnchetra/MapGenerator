# MapGenerator - Procedural Map Generator Mod for Mindustry

A feature-rich Mindustry Java Mod that adds an in-game procedural map generator dialog with multiple game modes, custom `.msav` map support, customized difficulties, biomes, enemy bases, and dynamic multi-stage Tower Defense modes!

---

## What's New in v1.1

### 🗺️ Custom `.msav` Map Support in Attack Mode
- Players can now play custom Mindustry maps (`.msav`) in Attack Mode!
- Supports custom `.msav` maps bundled inside `assets/maps/` as well as any custom maps imported into Mindustry.
- Includes an interactive **Map Selector Dialog** in the UI to choose specific custom maps or select random custom maps.

### 🎲 Default `Random` Difficulty & Custom Map Chance
- `Random` difficulty is now the default selection in the Map Generator dialog.
- Playing on `Random` difficulty automatically gives a 50% chance to play custom `.msav` maps!
- Added PC hover tooltips to difficulty buttons in the UI to inform players of map chances.

### ⚔️ Attack Mode Balancing & Quality-of-Life Fixes
- **Extended Preparation Time**: Added an initial grace period before Wave 1 arrives (4 minutes on Hard, 5 minutes on Normal, 6 minutes on Easy) to allow players to set up mining and base infrastructure.
- **Player Core Scaling**:
  - **Easy**: `Core Shard` (Small Core)
  - **Normal**: `Core Foundation` (Medium Core)
  - **Hard**: `Core Nucleus` (Large Core)
- **Hard Difficulty Starter Defenses**: Pre-placed air defenses (`Scatter` turrets with lead supply) and ground defenses (`Lancer` energy turrets with power supply) near player core on Hard difficulty.
- **Improved Base Spreading & Pathing**: Increased minimum distance between player core and enemy cores (100–140 blocks) and guaranteed wide (8-tile) carved paths connecting all enemy bases directly to the player core.

---

## Features

### Game Modes

* **Attack Mode**: Generate wild organic maps featuring player cores vs. fortified enemy bases equipped with turrets and defense depending on difficulty, or play custom `.msav` maps!
* **Survival Mode**: Survive waves of incoming ground and air enemy forces on randomized biome terrain.
* **Tower Defense (Limited)**:
  * Dynamic 5-stage expanding track layout.
  * Specialized turret platforms with built-in ore nodes (Copper, Lead, Coal, Titanium, Thorium) for ammo and power.
  * Map expands automatically at Waves 15, 35, 60, and 90 with upgraded Core structures (Core Shard -> Core Foundation -> Core Nucleus).
  * Survive to Wave 100 to trigger Victory!
* **Tower Defense (Endless)**:
  * Infinite wave survival mode.
  * Expands across 5 stages up to Wave 90, followed by exponentially scaling enemy health and damage multipliers beyond Wave 90.
* **Sandbox Mode**: Unlimited resources for testing builds and custom terrain configurations.

---

### Biomes & Terrain Generation

Procedurally generates varied biomes using multi-octave Simplex noise:
* **Desert** (Sand, Darksand, Sandstone Walls)
* **Glacial** (Snow, Ice, Snow Walls)
* **Volcanic** (Basalt, Hotrock, Slag Pools)
* **Spore** (Spore Moss, Tainted Water, Spore Walls)
* **Archipelago** (Grass, Sand, Deepwater, Pine Trees)
* **Standard** (Stone, Dirt, Mud, Boulders)

---

### Difficulties & Customization

| Difficulty | Map Size | Player Core | Enemy Cores | Base Fencing | Starter Defenses | Wave Grace Period |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Easy** | Small (~250) | Core Shard | Single Core Shard | Basic Duo Ring | None | 6 Minutes |
| **Normal** | Medium (~350) | Core Foundation | Core Foundation | Lancer / Scatter | None | 5 Minutes |
| **Hard** | Large (~500) | Core Nucleus | Dual Core Nucleus | Ripple / Salvo / Shields | Scatter + Lancer | 4 Minutes |
| **Random (Default)** | Dynamic | Random | Random | Scaled | Scaled | Scaled + Custom Map Chance |

---

## How to Play

1. In the Mindustry Main Menu, click the **Map Gen** button in the bottom-left corner.
2. Select your desired Game Mode (e.g., *Attack*, *Tower Defense*, or *Survival*).
3. Choose a Difficulty (*Random*, *Easy*, *Normal*, *Hard*).
4. Click **Generate & Play**!

---

## Custom Map Setup

To add your own custom `.msav` maps to the mod:
1. Place your `.msav` map files into the `assets/maps/` directory.
2. Or import custom maps directly in Mindustry via **Custom Game -> Import Map**.
3. Select **Attack Mode** -> **Custom Map (.msav)** in the Map Generator UI!

---

## License & Credits

* **Author**: `mnchetra`
* Built for Mindustry v7+ (minGameVersion 159+).
* Powered by Arc and the Mindustry API.
