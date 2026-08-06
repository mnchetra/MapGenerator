# MapGenerator - Procedural Map Generator Mod for Mindustry

A feature-rich Mindustry Java Mod that adds an in-game procedural map generator dialog with multiple game modes, custom `.msav` map support, customized difficulties, biomes, enemy bases, and dynamic multi-stage Tower Defense modes!

---

## What's New in v1.3

### 🌐 Full Multiplayer Server Compatibility
- **Server Joining Fix (`hidden: true`)**: Updated mod metadata so Mindustry treats the mod as a clean client-side add-on, allowing players to freely join any public or private multiplayer server without being blocked or rejected.
- **Server & Campaign Isolation**: Isolated all mod event listeners (`WaveEvent`, `WinEvent`, `LoseEvent`, `Trigger.update`) so the mod remains 100% passive when playing on multiplayer servers or campaign maps.

### 📦 Resource Management & Enemy Core Stocking
- **All-Resource Stocked Enemy Cores**: Enemy Crux cores (`Team.crux`) are automatically populated with 100% max capacity for **EVERY resource in Mindustry** (Serpulo & Erekir items) via post-game-start frame initialization. Enemy bases can build/fire continuously, and players can destroy/loot enemy cores to capture all resources.
- **Balanced Player Starter Loadout**: Reset player core (`Team.sharded`) starting loadout to standard starter resources (1,000 Copper, 1,000 Lead, 100 Silicon, 100 Graphite) without cheat resources flooding player inventory.

### ⚔️ Hard Mode Pacing & 35-Wave Preparation Grace
- **10-Minute Initial Prep Timer**: Added an initial 10-minute (`36000f`) grace period before Wave 1 arrives across all modes.
- **Rebalanced Waves 1–35**: Waves 1 to 35 consist of gentle T1/T2 scouting parties (Dagger, Crawler, Flare, Atrax, Mace), giving players 35 waves of prep time before T3 (Fortress/Zenith/Spiroct) and T4/T5 (Scepter/Reign/Toxopid/Omura) heavy units spawn.

### 🌊 Natural Water Features (Rivers & Lakes)
- **Rivers & Lakes**: Procedurally generates winding rivers (~4–6 tiles wide) and natural lake bodies (~20–30 tiles wide) without flooding terrain.
- **Liquid Preservation**: Path carving and area clearing preserve natural liquid floors.

### 🚁 Dynamic Terrain-Adaptive Enemy Wave Spawning
- **Dry Maps**: Spawns **Ground Units ONLY** (`Dagger`, `Crawler`, `Mace`, `Atrax`, `Fortress`, `Spiroct`, `Scepter`, `Reign`, `Toxopid`).
- **Naval Water Maps**: Spawns **Naval Combat & Support Ships** (`Risso`, `Retusa`, `Minke`, `Oxynoe`, `Bryde`, `Aegires`, `Sei`, `Omura`) AND **Flying Air Units**.
- **Obstacle / Blocked Water Maps**: Spawns **Flying Air Units ONLY** (`Flare`, `Horizon`, `Zenith`, `Antumbra`, `Eclipse`) so enemies fly over obstacles directly to the player core.

---

## What's New in v1.2

### ⚔️ Defeat & Victory Continue Screen (Next Map Generation)
- **Defeat Continue Option**: When your base is destroyed, a new **"Defeat! Core Destroyed!"** dialog appears with options to return to the **Main Menu** or click **Try New Map** to immediately generate and launch a fresh map!
- **Seamless Next Map Progression**: Clicking **Keep Playing** (on Victory) or **Try New Map** (on Defeat) now automatically generates and loads a brand new map matching your selected game mode and difficulty.
- **Dialog Overlay Fix**: Resolved issue where native `GameOverDialog` overlayed on top of custom win/defeat screens.

### 🗺️ Attack Mode Core Verification & Custom Map Rebalancing
- **0-Core Wave 1 Bug Fix**: Resolved an issue where procedural and custom Attack maps could lose their enemy cores during base generation cleanup, causing immediate Wave 1 victory.
- **Enemy Core Auto-Placement & Fallback**: Custom Attack maps are scanned upon loading; if no enemy core is present, the mod places a Crux Core at an enemy spawn/structure location, or safely falls back to procedural map generation.
- **Custom Map Frequency Adjustment**: Reduced custom `.msav` map selection chance on `Random` difficulty from 50% down to 5% so procedural terrain generation remains the primary gameplay focus.

---

## What's New in v1.1

### 🗺️ Custom `.msav` Map Support in Attack Mode
- Players can now play custom Mindustry maps (`.msav`) in Attack Mode!
- Supports custom `.msav` maps bundled inside `assets/maps/` as well as any custom maps imported into Mindustry.
- Includes an interactive **Map Selector Dialog** in the UI to choose specific custom maps or select random custom maps.
- To add your maps (Survival, Attack, Tower Defense, SandBox) to the mod Contact Me: https://t.me/yourmindustrymaps

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

* **Author**: `Seven`
* Built for Mindustry v7+ (minGameVersion 159+).
* Powered by Arc and the Mindustry API.
