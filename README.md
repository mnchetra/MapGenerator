# MapGenerator - Procedural Map Generator Mod for Mindustry

A feature-rich Mindustry Java Mod that adds an in-game procedural map generator dialog with multiple game modes, customized difficulties, biomes, enemy bases, and dynamic multi-stage Tower Defense modes!

---

## Features

### Game Modes

* **Attack Mode**: Generate wild organic maps featuring player cores vs. fortified enemy bases equipped with turrets and defense is depends on the difficulty.
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

| Difficulty | Map Size | Enemy Cores | Base Fencing | Core Upgrades | Drop Zone |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Easy** | Small (~250) | Single Core Shard | Basic Duo Ring | Core Shard | 300 |
| **Normal** | Medium (~350) | Core Foundation | Lancer / Scatter | Core Foundation | 150 |
| **Hard** | Large (~500) | Dual Core Nucleus | Ripple / Salvo / Shields | Core Nucleus | 100 |

---

## How to Play

1. In the Mindustry Main Menu, click the **Map Gen** button in the bottom-left corner.
2. Select your desired Game Mode (e.g. *Tower Defense (Limit)* or *Tower Defense (Endless)*).
3. Choose a Difficulty (*Easy*, *Normal*, *Hard*).
4. Click **Generate & Play**!

---

## License & Credits

* **Author**: `mnchetra`
* Built for Mindustry v7+ (minGameVersion 146+).
* Powered by Arc and the Mindustry API.
