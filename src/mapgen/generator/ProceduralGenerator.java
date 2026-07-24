package mapgen.generator;

import arc.math.Mathf;
import arc.util.noise.Simplex;
import arc.Events;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.core.GameState;
import mindustry.game.SpawnGroup;
import mindustry.content.UnitTypes;
import mindustry.world.blocks.logic.MessageBlock.MessageBuild;

public class ProceduralGenerator {
    public static boolean isTowerDefense = false;
    public static boolean isEndlessTD = false;
    public static int tdStage = 1;
    public static int tdSpawnX = 0;
    public static int tdSpawnY = 0;
    private static int[][] tdStageWaypoints = new int[5][6]; // [stageIndex][w1, w2, w3, turn1Y, turn2Y, patternType]

    public enum GameMode {
        Attack, Survival, Sandbox, TowerDefenseLimit, TowerDefenseEndless;

        public boolean isTD() {
            return this == TowerDefenseLimit || this == TowerDefenseEndless;
        }

        public String displayName() {
            switch (this) {
                case TowerDefenseLimit: return "Tower Defense (Limit)";
                case TowerDefenseEndless: return "Tower Defense (Endless)";
                default: return name();
            }
        }
    }

    public enum Difficulty {
        Easy(250), Normal(350), Hard(500);

        public final int size;

        Difficulty(int size) {
            this.size = size;
        }
    }

    public enum Biome {
        Desert(Blocks.sand, Blocks.darksand, Blocks.darksandTaintedWater, Blocks.sandWall, Blocks.sandBoulder, Blocks.darksandTaintedWater, Blocks.water),
        Glacial(Blocks.snow, Blocks.ice, Blocks.water, Blocks.snowWall, Blocks.snowBoulder, Blocks.water, Blocks.iceSnow),
        Volcanic(Blocks.basalt, Blocks.hotrock, Blocks.slag, Blocks.stoneWall, Blocks.boulder, Blocks.slag, Blocks.slag),
        Spore(Blocks.sporeMoss, Blocks.moss, Blocks.taintedWater, Blocks.sporeWall, Blocks.sporeCluster, Blocks.taintedWater, Blocks.darksandTaintedWater),
        Archipelago(Blocks.grass, Blocks.sand, Blocks.water, Blocks.stoneWall, Blocks.pine, Blocks.deepwater, Blocks.water),
        Standard(Blocks.stone, Blocks.dirt, Blocks.mud, Blocks.stoneWall, Blocks.boulder, Blocks.water, Blocks.darksandTaintedWater);

        public final mindustry.world.Block baseFloor, altFloor, liquid, wall, prop, deepLiquid, shallowLiquid;

        Biome(mindustry.world.Block baseFloor, mindustry.world.Block altFloor, mindustry.world.Block liquid, mindustry.world.Block wall, mindustry.world.Block prop, mindustry.world.Block deepLiquid, mindustry.world.Block shallowLiquid) {
            this.baseFloor = baseFloor;
            this.altFloor = altFloor;
            this.liquid = liquid;
            this.wall = wall;
            this.prop = prop;
            this.deepLiquid = deepLiquid;
            this.shallowLiquid = shallowLiquid;
        }
    }

    public static void generateAndPlay(GameMode mode, Difficulty difficulty) {
        try {
            Vars.ui.loadAnd(() -> {
                Vars.logic.reset();

                int size = difficulty.size + Mathf.random(-40, 60); // Random map size variance
                
                // Pre-calculate random values
                int seed = (int) (Math.random() * Integer.MAX_VALUE);
                Mathf.rand.setSeed(seed);

                float offsetX = Mathf.random(100000f);
                float offsetY = Mathf.random(100000f);
                
                int cx, cy;
                int ex1 = -1, ey1 = -1, ex2 = -1, ey2 = -1;
                int sx = -1, sy = -1;

                if (mode == GameMode.Attack) {
                    boolean left = Mathf.chance(0.5);
                    boolean bottom = Mathf.chance(0.5);
                    
                    // Player core in a random corner
                    cx = left ? Mathf.random(30, 60) : Mathf.random(size - 60, size - 30);
                    cy = bottom ? Mathf.random(30, 60) : Mathf.random(size - 60, size - 30);
                    
                    // Enemy core in the opposite corner
                    ex1 = !left ? Mathf.random(30, 60) : Mathf.random(size - 60, size - 30);
                    ey1 = !bottom ? Mathf.random(30, 60) : Mathf.random(size - 60, size - 30);
                    
                    if (difficulty == Difficulty.Hard) {
                        // Second enemy core also roughly opposite
                        ex2 = !left ? Mathf.random(40, 80) : Mathf.random(size - 80, size - 40);
                        ey2 = !bottom ? Mathf.random(40, 80) : Mathf.random(size - 80, size - 40);
                    }
                } else {
                    cx = size / 2;
                    cy = size / 2;
                    sx = Mathf.random(20, size - 20);
                    sy = size - 20;
                }

                // Final variables for lambdas
                final int fEx1 = ex1, fEy1 = ey1, fEx2 = ex2, fEy2 = ey2;
                final int fSx = sx, fSy = sy;

                if (mode.isTD()) {
                    isTowerDefense = true;
                    isEndlessTD = (mode == GameMode.TowerDefenseEndless);
                    tdStage = 1;
                    int mapWidth = 100;
                    int mapHeight = 420;

                    Vars.world.loadGenerator(mapWidth, mapHeight, tiles -> {
                        generateTowerDefenseMap(tiles, mapWidth, mapHeight);
                    });
                } else {
                    isTowerDefense = false;
                    isEndlessTD = false;
                    Vars.world.loadGenerator(size, size, tiles -> {
                        generateTerrain(tiles, size, mode, difficulty, offsetX, offsetY, cx, cy, fEx1, fEy1, fEx2, fEy2, fSx, fSy);
                        
                        // Generate bases inside loadGenerator (isGenerating = true)
                        generateBases(tiles, mode, difficulty, cx, cy, fEx1, fEy1, fEx2, fEy2, fSx, fSy);
                        
                        // Post-process: Auto-connect all power nodes & surge towers so the enemy base is 100% powered!
                        autoConnectPowerNodes(tiles);
                    });
                }

                Rules rules = new Rules();
                setupRules(rules, mode, difficulty);

                // Setup the state precisely as playMap does
                Vars.state.map = new mindustry.maps.Map(arc.struct.StringMap.of("name", "Procedural Generation"));
                Vars.state.rules = rules;
                Vars.state.rules.sector = null;
                Vars.state.rules.editor = false;

                // Fire WorldLoadEvent to initialize all systems (BlockIndexer, Pathfinder, Renderer, etc)
                // This naturally detects our pre-placed buildings and registers them to the correct teams.
                arc.Events.fire(new mindustry.game.EventType.WorldLoadEvent());

                Vars.logic.play();
                Vars.state.set(mindustry.core.GameState.State.playing);
                arc.Events.fire(mindustry.game.EventType.Trigger.newGame);

                if (mode.isTD()) {
                    if (isEndlessTD) {
                        showToast("[gold]TOWER DEFENSE (ENDLESS)![]\n[accent]Survive endless waves! Map expands up to Wave 90![]");
                    } else {
                        showToast("[gold]TOWER DEFENSE (LIMIT)![]\n[accent]Survive to Wave 100 to WIN! Map expands up to Wave 90![]");
                    }
                }
            });
            
            // Just in case Custom Game dialog is still open, hide it explicitly
            arc.Core.app.post(() -> {
                if (Vars.ui != null && Vars.ui.custom != null) {
                    Vars.ui.custom.hide();
                }
            });

        } catch (Exception e) {
            Vars.ui.showException("Failed to generate map", e);
        }
    }

    private static void generateTerrain(Tiles tiles, int size, GameMode mode, Difficulty difficulty, float offsetX, float offsetY, int cx, int cy, int ex1, int ey1, int ex2, int ey2, int sx, int sy) {
        Biome[] biomes = Biome.values();
        Biome biome = biomes[Mathf.random(biomes.length - 1)];

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                Tile tile = new Tile(x, y, biome.baseFloor.id, Blocks.air.id, Blocks.air.id);
                tiles.set(x, y, tile);

                // Smoother, larger scale noise
                float elevation = Simplex.noise2d(0, 3, 0.5f, 1f / 80f, x + offsetX, y + offsetY);
                float moisture = Simplex.noise2d(4, 3, 0.5f, 1f / 70f, x + offsetX, y + offsetY);

                if (elevation < -0.25f) {
                    if (moisture > 0.3f) tile.setFloor(biome.deepLiquid.asFloor());
                    else tile.setFloor(biome.shallowLiquid.asFloor());
                } else if (elevation < 0.1f) {
                    if (moisture > 0.55f) tile.setFloor(biome.altFloor.asFloor());
                    else tile.setFloor(biome.liquid.asFloor()); // Transition zone
                } else {
                    if (moisture > 0.55f) tile.setFloor(biome.altFloor.asFloor());
                    else tile.setFloor(biome.baseFloor.asFloor());
                }

                boolean isWater = tile.floor() == biome.deepLiquid || tile.floor() == biome.shallowLiquid || tile.floor() == biome.liquid;
                
                // Ore grouped generation
                if (!isWater) {
                    float copperNoise = Simplex.noise2d(1, 2, 0.5f, 1f / 20f, x + offsetX, y + offsetY);
                    float leadNoise = Simplex.noise2d(2, 2, 0.5f, 1f / 20f, x + offsetX, y + offsetY);
                    float coalNoise = Simplex.noise2d(3, 2, 0.5f, 1f / 25f, x + offsetX, y + offsetY);
                    float scrapNoise = Simplex.noise2d(4, 2, 0.5f, 1f / 25f, x + offsetX, y + offsetY);
                    float titaniumNoise = Simplex.noise2d(5, 2, 0.5f, 1f / 22f, x + offsetX, y + offsetY);
                    float thoriumNoise = Simplex.noise2d(6, 2, 0.5f, 1f / 22f, x + offsetX, y + offsetY);

                    if (copperNoise > 0.75f) tile.setOverlay(Blocks.oreCopper);
                    else if (leadNoise > 0.75f) tile.setOverlay(Blocks.oreLead);
                    else if (coalNoise > 0.75f) tile.setOverlay(Blocks.oreCoal);
                    else if (scrapNoise > 0.75f) tile.setOverlay(Blocks.oreScrap);
                    else if (titaniumNoise > 0.75f && (difficulty == Difficulty.Normal || difficulty == Difficulty.Hard)) tile.setOverlay(Blocks.oreTitanium);
                    else if (thoriumNoise > 0.75f && difficulty == Difficulty.Hard) tile.setOverlay(Blocks.oreThorium);
                }

                // Walls (Scaled up drastically to create solid, chunky maze-like walls)
                float wallNoise = Simplex.noise2d(3, 3, 0.5f, 1f / 120f, x + offsetX, y + offsetY);
                if (wallNoise > 0.45f && !isWater && tile.overlay() == Blocks.air) {
                    tile.setBlock(biome.wall);
                } else {
                    // Props (trees, boulders) - Scattered randomly, not clumpy
                    float propNoise = Simplex.noise2d(7, 4, 0.6f, 1f / 15f, x + offsetX, y + offsetY);
                    if (propNoise > 0.75f && !isWater && tile.block() == Blocks.air && tile.overlay() == Blocks.air) {
                        tile.setBlock(biome.prop);
                    }
                }
            }
        }

        if (mode.isTD()) {
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    Tile t = tiles.getn(x, y);
                    if (t.floor() != biome.liquid && t.floor() != biome.deepLiquid && t.floor() != biome.shallowLiquid) {
                        t.setBlock(Blocks.thoriumWall);
                    }
                }
            }
        }

        // 1. Carve paths based on mode
        if (mode == GameMode.Attack) {
            // Pick a wild midpoint for the bezier control point
            int midX = (cx + ex1) / 2 + Mathf.random(-size/2, size/2);
            int midY = (cy + ey1) / 2 + Mathf.random(-size/2, size/2);
            midX = Mathf.clamp(midX, 20, size - 20);
            midY = Mathf.clamp(midY, 20, size - 20);
            
            carveOrganicPath(tiles, cx, cy, midX, midY, ex1, ey1, 5, offsetX, offsetY);
            
            if (ex2 != -1) {
                int midX2 = (cx + ex2) / 2 + Mathf.random(-size/2, size/2);
                int midY2 = (cy + ey2) / 2 + Mathf.random(-size/2, size/2);
                midX2 = Mathf.clamp(midX2, 20, size - 20);
                midY2 = Mathf.clamp(midY2, 20, size - 20);
                
                carveOrganicPath(tiles, cx, cy, midX2, midY2, ex2, ey2, 5, offsetX, offsetY);
            }
        } else if (mode == GameMode.Survival || mode.isTD()) {
            if (mode.isTD()) {
                // Control point creates a nice L-shaped sweeping curve
                int controlX = sx;
                int controlY = cy;
                carveOrganicPath(tiles, sx, sy, controlX, controlY, cx, cy, 6, offsetX, offsetY);
            } else {
                int controlX = (sx + cx) / 2 + Mathf.random(-size/4, size/4);
                int controlY = (sy + cy) / 2 + Mathf.random(-size/4, size/4);
                carveOrganicPath(tiles, sx, sy, controlX, controlY, cx, cy, 4, offsetX, offsetY);
            }
        }
    }

    private static void clearArea(Tiles tiles, int x, int y, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    Tile t = tiles.get(x + dx, y + dy);
                    if (t != null) {
                        t.setBlock(Blocks.air, mindustry.game.Team.derelict, 0);
                        // Make sure the floor is solid so cores/buildings don't instantly explode if placed on water
                        if (t.floor().isLiquid) {
                            t.setFloor(Blocks.stone.asFloor());
                        }
                    }
                }
            }
        }
    }

    private static void placeBlock(Tiles tiles, int x, int y, mindustry.world.Block block, mindustry.game.Team team) {
        Tile center = tiles.get(x, y);
        if (center != null) {
            center.setBlock(block, team, 0);
        }
    }

    private static void generateBases(Tiles tiles, GameMode mode, Difficulty difficulty, int cx, int cy, int ex1, int ey1, int ex2, int ey2, int sx, int sy) {
        // 2. Place Player Core
        clearArea(tiles, cx, cy, 18); // Increased from 12 to 18 for more space
        placeBlock(tiles, cx, cy, Blocks.coreShard, Team.sharded);

        // 3. Place Mode-specific Entities
        if (mode == GameMode.Attack) {
            mindustry.maps.generators.BaseGenerator baseGen = new mindustry.maps.generators.BaseGenerator();
            mindustry.type.Sector dummySector = mindustry.content.Planets.serpulo.sectors.get(0);
            float diffFloat = difficulty == Difficulty.Hard ? 1f : (difficulty == Difficulty.Normal ? 0.5f : 0.2f);
            
            arc.struct.Seq<Tile> enemyCores = new arc.struct.Seq<>();
            
            // Place first enemy core
            clearArea(tiles, ex1, ey1, 40); // Increased drastically so huge enemy bases don't get cut off by terrain walls
            mindustry.world.Block core1 = difficulty == Difficulty.Hard ? Blocks.coreNucleus : (difficulty == Difficulty.Normal ? Blocks.coreFoundation : Blocks.coreShard);
            placeBlock(tiles, ex1, ey1, core1, mindustry.game.Team.crux);
            enemyCores.add(tiles.get(ex1, ey1));
            tiles.getn(ex1, ey1).setOverlay(Blocks.spawn); // Make it visible on minimap
            
            if (difficulty == Difficulty.Hard) {
                // Place second enemy core
                clearArea(tiles, ex2, ey2, 40); // Increased drastically so huge enemy bases don't get cut off by terrain walls
                placeBlock(tiles, ex2, ey2, Blocks.coreNucleus, mindustry.game.Team.crux);
                enemyCores.add(tiles.get(ex2, ey2));
                tiles.getn(ex2, ey2).setOverlay(Blocks.spawn); // Make it visible on minimap
            }
            
            // Pass enemyCores.first() as target so BaseGenerator does NOT carve a straight path across the map to the player core!
            baseGen.generate(tiles, enemyCores, enemyCores.first(), mindustry.game.Team.crux, dummySector, diffFloat);

            // Cleanup pass: Remove any enemy base structures that were placed within 75 blocks of the player core!
            int safeRadius = 75;
            for (int dx = -safeRadius; dx <= safeRadius; dx++) {
                for (int dy = -safeRadius; dy <= safeRadius; dy++) {
                    if (dx * dx + dy * dy <= safeRadius * safeRadius) {
                        Tile t = tiles.get(cx + dx, cy + dy);
                        if (t != null && t.team() == Team.crux) {
                            t.setBlock(Blocks.air, Team.derelict, 0);
                        }
                    }
                }
            }
        } else if (mode == GameMode.Survival || mode.isTD()) {
            clearArea(tiles, sx, sy, 10);
            tiles.getn(sx, sy).setOverlay(Blocks.spawn);
        }
    }


    private static void carveOrganicPath(Tiles tiles, int x1, int y1, int cx, int cy, int x2, int y2, int width, float offsetX, float offsetY) {
        int steps = (int) (Mathf.dst(x1, y1, cx, cy) + Mathf.dst(cx, cy, x2, y2));
        float prevX = x1;
        float prevY = y1;
        
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            
            // Quadratic Bezier formula for perfectly smooth macroscopic curve
            float u = 1f - t;
            float px = u * u * x1 + 2 * u * t * cx + t * t * x2;
            float py = u * u * y1 + 2 * u * t * cy + t * t * y2;
            
            // Add a small organic wiggle to the edges of the smooth curve
            float wiggle = Simplex.noise2d(10, 2, 0.5f, 1f / 40f, px + offsetX, py + offsetY) * 12f;
            
            // Calculate perpendicular vector for wiggle direction
            float dx = px - prevX;
            float dy = py - prevY;
            if (dx == 0 && dy == 0) {
                dx = 1f; 
                dy = 0f;
            }
            float len = Mathf.dst(dx, dy);
            float perpX = -dy / len;
            float perpY = dx / len;
            
            prevX = px;
            prevY = py;
            
            int finalX = (int)(px + perpX * wiggle);
            int finalY = (int)(py + perpY * wiggle);
            
            clearArea(tiles, finalX, finalY, width);
        }
    }

    private static void buildEnemyBase(Tiles tiles, int ex, int ey, Difficulty diff) {
        int radius = diff == Difficulty.Hard ? 22 : (diff == Difficulty.Normal ? 18 : 12);
        clearArea(tiles, ex, ey, radius);
        
        if (diff == Difficulty.Easy) {
            placeBlock(tiles, ex, ey, Blocks.coreShard, Team.crux);
            buildRing(tiles, ex, ey, 3, 3, Blocks.duo, true);
            buildRing(tiles, ex, ey, 6, 6, Blocks.copperWallLarge, false);
        } else if (diff == Difficulty.Normal) {
            placeBlock(tiles, ex, ey, Blocks.coreFoundation, Team.crux);
            buildRing(tiles, ex, ey, 4, 4, Blocks.lancer, true);
            buildRing(tiles, ex, ey, 4, 2, Blocks.scatter, true);
            buildRing(tiles, ex, ey, 7, 7, Blocks.titaniumWallLarge, false);
        } else {
            placeBlock(tiles, ex, ey, Blocks.coreNucleus, Team.crux);
            buildRing(tiles, ex, ey, 5, 5, Blocks.ripple, true);
            buildRing(tiles, ex, ey, 5, 2, Blocks.salvo, true);
            buildRing(tiles, ex, ey, 8, 8, Blocks.thoriumWallLarge, false);
            buildRing(tiles, ex, ey, 10, 10, Blocks.plastaniumWallLarge, false);
            
            // Add some force projectors
            int[][] fpOffsets = {{6, 6}, {-6, 6}, {6, -6}, {-6, -6}};
            for (int[] off : fpOffsets) {
                Tile t = tiles.get(ex + off[0], ey + off[1]);
                if (t != null) {
                    placeBlock(tiles, ex + off[0], ey + off[1], Blocks.forceProjector, Team.crux);
                    placeBlock(tiles, ex + off[0] + 1, ey + off[1], Blocks.itemSource, Team.crux);
                }
            }
        }
    }

    private static void buildRing(Tiles tiles, int ex, int ey, int radiusX, int radiusY, mindustry.world.Block block, boolean addSources) {
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                if (Math.abs(dx) == radiusX || Math.abs(dy) == radiusY) {
                    // Only place sparsely if it's a turret to avoid overlapping 2x2s
                    if (addSources && (dx % 2 != 0 || dy % 2 != 0)) continue;
                    
                    Tile t = tiles.get(ex + dx, ey + dy);
                    if (t != null) {
                        placeBlock(tiles, ex + dx, ey + dy, block, Team.crux);
                        if (addSources) {
                            placeBlock(tiles, ex + dx + 1, ey + dy, Blocks.itemSource, Team.crux);
                        }
                    }
                }
            }
        }
    }

    private static void setupRules(Rules rules, GameMode mode, Difficulty difficulty) {
        rules.waves = true;
        rules.waveTimer = true;
        rules.winWave = mode == GameMode.TowerDefenseLimit ? 100 : 50;

        switch (mode) {
            case Attack:
                rules.attackMode = true;
                rules.waveSpacing = difficulty == Difficulty.Hard ? 2400f : (difficulty == Difficulty.Normal ? 3000f : 3600f);
                break;
            case Sandbox:
                rules.waves = false;
                rules.waveTimer = false;
                rules.infiniteResources = true;
                break;
            case TowerDefenseLimit:
            case TowerDefenseEndless:
                rules.waveSpacing = 3000f; // 50 seconds per wave
                break;
            case Survival:
                rules.waveSpacing = 7200f;
                break;
        }

        switch (difficulty) {
            case Easy:
                rules.dropZoneRadius = mode.isTD() ? 25f : 300f;
                rules.buildCostMultiplier = 0.5f;
                rules.blockHealthMultiplier = 1.5f;
                break;
            case Normal:
                rules.dropZoneRadius = mode.isTD() ? 25f : 150f;
                break;
            case Hard:
                rules.dropZoneRadius = mode.isTD() ? 25f : 100f;
                rules.buildCostMultiplier = 1.5f;
                rules.blockHealthMultiplier = 0.8f;
                break;
        }

        if (mode.isTD()) {
            rules.dropZoneRadius = 25f; // Small, clean spawn circle around spawn point
            rules.teams.get(Team.crux).unitDamageMultiplier = 1.0f; // Enemies deal damage to Core!
            rules.teams.get(Team.crux).unitHealthMultiplier = 1.0f;
        }

        if (mode.isTD()) {
            rules.waves = true;
            rules.waveTimer = true;
            rules.waveSpacing = 3000f; // 50 seconds per wave
            rules.spawns.clear();

            if (mode == GameMode.TowerDefenseLimit) {
                rules.winWave = 100;
            } else {
                rules.winWave = 0; // Endless waves!
            }

            int stage5End = (mode == GameMode.TowerDefenseLimit) ? 100 : 99999;

            // Stage 1 (Easy: Waves 1-15) - T1 Crawlers
            SpawnGroup g1 = new SpawnGroup(UnitTypes.crawler);
            g1.begin = 1; g1.end = 15; g1.unitAmount = 5; g1.unitScaling = 2f;
            rules.spawns.add(g1);

            // Stage 2 (Normal: Waves 16-35) - T1 & T2 Crawlers (Atrax)
            SpawnGroup g2 = new SpawnGroup(UnitTypes.crawler);
            g2.begin = 16; g2.end = 35; g2.unitAmount = 8; g2.unitScaling = 2.5f;
            rules.spawns.add(g2);

            SpawnGroup g3 = new SpawnGroup(UnitTypes.atrax);
            g3.begin = 16; g3.end = 35; g3.unitAmount = 3; g3.unitScaling = 1.5f;
            rules.spawns.add(g3);

            // Stage 3 (Hard: Waves 36-60) - T2 & T3 Crawlers (Spiroct)
            SpawnGroup g4 = new SpawnGroup(UnitTypes.atrax);
            g4.begin = 36; g4.end = 60; g4.unitAmount = 6; g4.unitScaling = 2f;
            rules.spawns.add(g4);

            SpawnGroup g5 = new SpawnGroup(UnitTypes.spiroct);
            g5.begin = 36; g5.end = 60; g5.unitAmount = 3; g5.unitScaling = 1f;
            rules.spawns.add(g5);

            // Stage 4 (Extreme: Waves 61-89) - T3 & T4 Crawlers (Arkyid)
            SpawnGroup g6 = new SpawnGroup(UnitTypes.spiroct);
            g6.begin = 61; g6.end = 89; g6.unitAmount = 6; g6.unitScaling = 1.5f;
            rules.spawns.add(g6);

            SpawnGroup g7 = new SpawnGroup(UnitTypes.arkyid);
            g7.begin = 61; g7.end = 89; g7.unitAmount = 2; g7.unitScaling = 0.8f;
            rules.spawns.add(g7);

            // Stage 5 (Eradication: Waves 90+) - T4 & T5 Crawlers (Toxopid)
            SpawnGroup g8 = new SpawnGroup(UnitTypes.arkyid);
            g8.begin = 90; g8.end = stage5End; g8.unitAmount = 4; g8.unitScaling = 1.2f;
            rules.spawns.add(g8);

            SpawnGroup g9 = new SpawnGroup(UnitTypes.toxopid);
            g9.begin = 90; g9.end = stage5End; g9.unitAmount = 1; g9.unitScaling = 0.5f;
            rules.spawns.add(g9);
        } else if (rules.waves) {
            rules.spawns.clear();

            // Tier 1 Ground
            SpawnGroup g1 = new SpawnGroup(UnitTypes.dagger);
            g1.begin = 1;
            g1.end = 30;
            g1.unitAmount = 3;
            g1.unitScaling = 1.5f;
            rules.spawns.add(g1);

            SpawnGroup g2 = new SpawnGroup(UnitTypes.crawler);
            g2.begin = 3;
            g2.end = 40;
            g2.unitAmount = 4;
            g2.unitScaling = 2f;
            rules.spawns.add(g2);

            // Tier 1 Air
            SpawnGroup g3 = new SpawnGroup(UnitTypes.flare);
            g3.begin = 2;
            g3.end = 35;
            g3.unitAmount = 2;
            g3.unitScaling = 1.2f;
            rules.spawns.add(g3);

            // Tier 2 Ground & Legs
            SpawnGroup g4 = new SpawnGroup(UnitTypes.mace);
            g4.begin = 8;
            g4.end = 60;
            g4.unitAmount = 2;
            g4.unitScaling = 1f;
            rules.spawns.add(g4);

            SpawnGroup g5 = new SpawnGroup(UnitTypes.atrax);
            g5.begin = 10;
            g5.end = 60;
            g5.unitAmount = 2;
            g5.unitScaling = 1f;
            rules.spawns.add(g5);

            // Tier 2 Air
            SpawnGroup g6 = new SpawnGroup(UnitTypes.horizon);
            g6.begin = 12;
            g6.end = 60;
            g6.unitAmount = 2;
            g6.unitScaling = 1f;
            rules.spawns.add(g6);

            // Tier 3 Heavy (Normal / Hard)
            if (difficulty != Difficulty.Easy) {
                SpawnGroup g7 = new SpawnGroup(UnitTypes.fortress);
                g7.begin = 18;
                g7.end = 100;
                g7.unitAmount = 1;
                g7.unitScaling = 0.5f;
                rules.spawns.add(g7);

                SpawnGroup g8 = new SpawnGroup(UnitTypes.zenith);
                g8.begin = 20;
                g8.end = 100;
                g8.unitAmount = 1;
                g8.unitScaling = 0.5f;
                rules.spawns.add(g8);
            }

            // Tier 4 Boss (Hard)
            if (difficulty == Difficulty.Hard) {
                SpawnGroup g9 = new SpawnGroup(UnitTypes.scepter);
                g9.begin = 30;
                g9.end = 100;
                g9.unitAmount = 1;
                g9.unitScaling = 0.2f;
                rules.spawns.add(g9);
            }
        }
    }

    private static void generateTowerDefenseMap(Tiles tiles, int width, int height) {
        // Track floor cannot be built on (players cannot place walls/turrets on the red track!)
        Blocks.darkPanel2.asFloor().placeableOn = false;

        // Force fresh RNG seed every single map generation!
        Mathf.rand.setSeed(System.currentTimeMillis() + System.nanoTime());

        // Procedurally randomize track waypoints and PATTERN TYPES for each stage so map layout changes!
        for (int s = 0; s < 5; s++) {
            boolean leftFirst = Mathf.chance(0.5f);
            int xl = Mathf.random(20, 36);
            int xr = Mathf.random(64, 80);
            int yBase = 32 + s * 75;
            int turn1Y = yBase + 28;
            int turn2Y = yBase + 56;
            int pattern = Mathf.random(0, 3); // 4 distinct track pattern styles!

            tdStageWaypoints[s][0] = leftFirst ? xl : xr;
            tdStageWaypoints[s][1] = leftFirst ? xr : xl;
            tdStageWaypoints[s][2] = leftFirst ? xl : xr;
            tdStageWaypoints[s][3] = turn1Y;
            tdStageWaypoints[s][4] = turn2Y;
            tdStageWaypoints[s][5] = pattern;
        }

        // Fill whole map with darkMetal walls (so un-unlocked sections are 100% solid walls and hidden!)
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Tile t = new Tile(x, y, Blocks.darkPanel1.id, Blocks.air.id, Blocks.darkMetal.id);
                tiles.set(x, y, t);
            }
        }

        // 1. Carve Large Rectangular Player Core Base (Y: 6..32) - Massive Space for Defense & Factories!
        expandRectRoom(tiles, 50, 19, 50, 26);

        // Core Area Ore Patches (Positioned at y <= 11, safely away from enemy path entry!)
        buildOrePatch(tiles, 32, 11, 8, 8, Blocks.oreCopper);
        buildOrePatch(tiles, 68, 11, 8, 8, Blocks.oreLead);
        buildOrePatch(tiles, 50, 8, 8, 8, Blocks.oreCoal);

        // Water Pool & Sand Patch (Positioned at y = 21 on left/right wings, zero path overlap!)
        buildFloorPatch(tiles, 32, 21, 6, 6, Blocks.water);
        buildFloorPatch(tiles, 68, 21, 6, 6, Blocks.sand);

        // 2. Carve Stage 1 Track & Layout (Y: 32..95) - Easy
        carveStageLayout(tiles, 1);

        // Funnel Track Corridor in Core Base Room (Connects directly to the top edge of Core Shard at y = 18!)
        drawTrackLine(tiles, 50, 18, 50, 32, 6);

        // Dynamically enclose all track edges with darkMetal walls so fencing follows the track perfectly for any pattern!
        encloseTrackEdges(tiles, width, height);

        // 3. Place Core Shard AFTER track carving so track meets the top edge of the Core!
        placeBlock(tiles, 50, 16, Blocks.coreShard, Team.sharded);

        // Core Objective Message Block
        if (isEndlessTD) {
            placeMessageBlock(tiles, 50, 22, "[gold]WELCOME TO TOWER DEFENSE (ENDLESS)![]\n\n[accent]MAP EXPANSION GOAL:[]\nSurvive enemy waves to expand the map!\n- Wave 15 -> Stage 2 (Normal)\n- Wave 35 -> Stage 3 (Hard)\n- Wave 60 -> Stage 4 (Extreme)\n- Wave 90 -> Stage 5 (Eradication)\n- Wave 90+ -> Endless Scaling!\n\nBuild turrets on marked platforms along the track!");
        } else {
            placeMessageBlock(tiles, 50, 22, "[gold]WELCOME TO TOWER DEFENSE (LIMIT)![]\n\n[accent]MAP EXPANSION GOAL:[]\nSurvive to Wave 100 to WIN!\n- Wave 15 -> Stage 2 (Normal)\n- Wave 35 -> Stage 3 (Hard)\n- Wave 60 -> Stage 4 (Extreme)\n- Wave 90 -> Stage 5 (Eradication)\n- Wave 100 -> VICTORY!\n\nBuild turrets on marked platforms along the track!");
        }

        tdSpawnX = tdStageWaypoints[0][2];
        tdSpawnY = 32 + 75 - 3; // 104 (Exact top end of Stage 1 track!)
        tiles.getn(tdSpawnX, tdSpawnY).setOverlay(Blocks.spawn);
    }

    private static void carveCorner(Tiles tiles, int cx, int cy, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius * radius) {
                    Tile t = tiles.get(cx + dx, cy + dy);
                    if (t != null) {
                        t.setBlock(Blocks.air);
                        t.setFloor(Blocks.darkPanel2.asFloor());
                    }
                }
            }
        }
    }

    private static void carveStageLayout(Tiles tiles, int stageNum) {
        int yBase = 32 + (stageNum - 1) * 75;
        int endY = yBase + 75;

        int w1 = tdStageWaypoints[stageNum - 1][0];
        int w2 = tdStageWaypoints[stageNum - 1][1];
        int w3 = tdStageWaypoints[stageNum - 1][2];
        int turn1Y = tdStageWaypoints[stageNum - 1][3];
        int turn2Y = tdStageWaypoints[stageNum - 1][4];
        int pattern = tdStageWaypoints[stageNum - 1][5];

        int startX = (stageNum == 1) ? 50 : tdStageWaypoints[stageNum - 2][2];
        int startY = (stageNum == 1) ? 18 : yBase;

        // Render Distinct Track Patterns based on stage pattern selection
        if (pattern == 0) { // Double S-Bend
            drawTrackLine(tiles, startX, startY, startX, yBase, 6);
            drawTrackLine(tiles, startX, yBase, w1, yBase, 6);
            drawTrackLine(tiles, w1, yBase, w1, turn1Y, 6);
            drawTrackLine(tiles, w1, turn1Y, w2, turn1Y, 6);
            drawTrackLine(tiles, w2, turn1Y, w2, turn2Y, 6);
            drawTrackLine(tiles, w2, turn2Y, w3, turn2Y, 6);
            drawTrackLine(tiles, w3, turn2Y, w3, endY, 6);
        } else if (pattern == 1) { // Wide C-Loop
            drawTrackLine(tiles, startX, startY, startX, yBase, 6);
            drawTrackLine(tiles, startX, yBase, w1, yBase, 6);
            drawTrackLine(tiles, w1, yBase, w1, turn2Y, 6);
            drawTrackLine(tiles, w1, turn2Y, w3, turn2Y, 6);
            drawTrackLine(tiles, w3, turn2Y, w3, endY, 6);
        } else if (pattern == 2) { // Center Expressway
            drawTrackLine(tiles, startX, startY, startX, yBase, 6);
            drawTrackLine(tiles, startX, yBase, w2, yBase, 6);
            drawTrackLine(tiles, w2, yBase, w2, endY, 6);
        } else if (pattern == 3) { // Sharp Zig-Zag
            drawTrackLine(tiles, startX, startY, startX, yBase, 6);
            drawTrackLine(tiles, startX, yBase, w1, yBase, 6);
            drawTrackLine(tiles, w1, yBase, w1, turn1Y, 6);
            drawTrackLine(tiles, w1, turn1Y, w3, turn1Y, 6);
            drawTrackLine(tiles, w3, turn1Y, w3, endY, 6);
        }

        // Smooth and widen track turn corners
        carveCorner(tiles, startX, yBase, 4);
        if (pattern == 0) {
            carveCorner(tiles, w1, yBase, 4);
            carveCorner(tiles, w1, turn1Y, 4);
            carveCorner(tiles, w2, turn1Y, 4);
            carveCorner(tiles, w2, turn2Y, 4);
            carveCorner(tiles, w3, turn2Y, 4);
        } else if (pattern == 1) {
            carveCorner(tiles, w1, yBase, 4);
            carveCorner(tiles, w1, turn2Y, 4);
            carveCorner(tiles, w3, turn2Y, 4);
        } else if (pattern == 2) {
            carveCorner(tiles, w2, yBase, 4);
        } else if (pattern == 3) {
            carveCorner(tiles, w1, yBase, 4);
            carveCorner(tiles, w1, turn1Y, 4);
            carveCorner(tiles, w3, turn1Y, 4);
        }

        // Carve Wide 10x10 Spawn Chamber at the top end of the stage track so units (T1-T5) never get stuck in walls!
        createSpawnRoom(tiles, w3, endY - 3);

        // Calculate actual vertical track positions at lower (yBase+14) and upper (yBase+42) segments for this pattern
        int track1X = (pattern == 2) ? startX : ((pattern == 3) ? w2 : w1);
        int track2X = (pattern == 1 || pattern == 3) ? w1 : w2;

        // Position building pads with a safe 20-tile offset away from the active track segment
        int pad1X = (track1X <= 50) ? Math.min(78, track1X + 20) : Math.max(22, track1X - 20);
        int pad2X = (track2X <= 50) ? Math.min(78, track2X + 20) : Math.max(22, track2X - 20);

        int pad1Y = yBase + 14;
        int pad2Y = yBase + 42;

        if (stageNum == 1) {
            drawBuildingPad(tiles, pad1X, pad1Y, 26, 14, Blocks.oreCopper, Blocks.oreLead);
            placeMessageBlock(tiles, pad1X, pad1Y, "[accent]TURRET PLATFORM 1[]\nMine Copper & Lead directly from platform!");

            drawBuildingPad(tiles, pad2X, pad2Y, 26, 14, Blocks.oreCoal, Blocks.oreCopper);
            placeMessageBlock(tiles, pad2X, pad2Y, "[accent]TURRET PLATFORM 2[]\nMine Coal & Copper directly from platform!");
        } else if (stageNum == 2) {
            drawBuildingPad(tiles, pad1X, pad1Y, 26, 14, Blocks.oreTitanium, Blocks.oreCopper);
            placeMessageBlock(tiles, pad1X, pad1Y, "[accent]TURRET PLATFORM 3[]\nTitanium & Copper ammo platform!");

            drawBuildingPad(tiles, pad2X, pad2Y, 26, 14, Blocks.oreCoal, Blocks.oreLead);
            placeMessageBlock(tiles, pad2X, pad2Y, "[accent]TURRET PLATFORM 4[]\nHeavy turret placement zone!");

            buildFloorPatch(tiles, 18, pad2Y, 6, 6, Blocks.hotrock);
            placeMessageBlock(tiles, 18, pad2Y, "[orange]THERMAL POWER ZONE[]");

            buildFloorPatch(tiles, 82, pad1Y, 6, 6, Blocks.deepwater);
            placeMessageBlock(tiles, 82, pad1Y, "[sky]WATER SUPPLY ZONE[]");
        } else if (stageNum == 3) {
            drawBuildingPad(tiles, pad1X, pad1Y, 26, 14, Blocks.oreThorium, Blocks.oreCopper);
            placeMessageBlock(tiles, pad1X, pad1Y, "[accent]TURRET PLATFORM 5[]");

            drawBuildingPad(tiles, pad2X, pad2Y, 26, 14, Blocks.oreThorium, Blocks.oreScrap);
            placeMessageBlock(tiles, pad2X, pad2Y, "[accent]TURRET PLATFORM 6[]");

            buildFloorPatch(tiles, 82, pad2Y, 6, 6, Blocks.magmarock);
            placeMessageBlock(tiles, 82, pad2Y, "[orange]HIGH THERMAL POWER ZONE[]");

            buildFloorPatch(tiles, 18, pad1Y, 6, 6, Blocks.water);
        } else if (stageNum == 4) {
            drawBuildingPad(tiles, pad1X, pad1Y, 26, 14, Blocks.oreThorium, Blocks.oreTitanium);
            placeMessageBlock(tiles, pad1X, pad1Y, "[red]EXTREME DEFENSE ZONE[]");

            drawBuildingPad(tiles, pad2X, pad2Y, 26, 14, Blocks.oreThorium, Blocks.oreCoal);
            placeMessageBlock(tiles, pad2X, pad2Y, "[red]EXTREME DEFENSE ZONE[]");

            buildFloorPatch(tiles, 18, pad2Y, 6, 6, Blocks.hotrock);
        } else if (stageNum == 5) {
            drawBuildingPad(tiles, pad1X, pad1Y, 26, 14, Blocks.oreThorium, Blocks.oreTitanium);
            placeMessageBlock(tiles, pad1X, pad1Y, "[scarlet]ERADICATION DEFENSE PLATFORM[]");

            drawBuildingPad(tiles, pad2X, pad2Y, 26, 14, Blocks.oreThorium, Blocks.oreTitanium);
            placeMessageBlock(tiles, pad2X, pad2Y, "[scarlet]FINAL DEFENSE PLATFORM[]");

            buildFloorPatch(tiles, 82, pad2Y, 6, 6, Blocks.magmarock);
        }
    }

    private static void expandRectRoom(Tiles tiles, int centerX, int centerY, int w, int h) {
        int halfW = w / 2;
        int halfH = h / 2;
        int minX = centerX - halfW;
        int maxX = centerX + halfW;
        int minY = centerY - halfH;
        int maxY = centerY + halfH;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Tile tile = tiles.get(x, y);
                if (tile != null) {
                    boolean isLeftOrRightWall = (x == minX || x == maxX);
                    boolean isBottomWall = (y == minY);
                    boolean isTopWall = (y == maxY);
                    boolean isTrackEntrance = (isTopWall && x >= 47 && x <= 53);

                    if ((isLeftOrRightWall || isBottomWall || isTopWall) && !isTrackEntrance) {
                        if (tile.build == null && tile.floor() != Blocks.darkPanel2.asFloor()) {
                            tile.setBlock(Blocks.darkMetal);
                        }
                    } else {
                        if (tile.block() == Blocks.darkMetal) {
                            tile.setBlock(Blocks.air);
                        }
                        if (tile.floor() != Blocks.metalFloor.asFloor() 
                            && tile.floor() != Blocks.darkPanel2.asFloor() 
                            && tile.floor() != Blocks.water.asFloor() 
                            && tile.floor() != Blocks.sand.asFloor()) {
                            tile.setFloor(Blocks.darkPanel1.asFloor());
                        }
                    }
                }
            }
        }
    }

    private static void drawTrackLine(Tiles tiles, int x1, int y1, int x2, int y2, int width) {
        int halfW = width / 2;
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        for (int x = minX - (x1 == x2 ? halfW : 0); x <= maxX + (x1 == x2 ? halfW - 1 : 0); x++) {
            for (int y = minY - (y1 == y2 ? halfW : 0); y <= maxY + (y1 == y2 ? halfW - 1 : 0); y++) {
                Tile tile = tiles.get(x, y);
                if (tile != null) {
                    tile.setBlock(Blocks.air);
                    tile.setFloor(Blocks.darkPanel2.asFloor());
                }
            }
        }
    }

    private static void createSpawnRoom(Tiles tiles, int centerX, int centerY) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                Tile t = tiles.get(centerX + dx, centerY + dy);
                if (t != null) {
                    t.setBlock(Blocks.air);
                    t.setFloor(Blocks.darkPanel2.asFloor());
                }
            }
        }
    }

    private static void encloseTrackEdges(Tiles tiles, int width, int height) {
        for (int x = 1; x < width - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                // Leave clearance near Core (y <= 18, x = 46..54) for 4x4/5x5 Core Upgrades!
                if (y <= 18 && x >= 46 && x <= 54) continue;

                Tile tile = tiles.get(x, y);
                if (tile != null && tile.floor() != Blocks.darkPanel2.asFloor() 
                    && tile.floor() != Blocks.metalFloor.asFloor() 
                    && tile.floor() != Blocks.water.asFloor() 
                    && tile.floor() != Blocks.sand.asFloor()
                    && tile.floor() != Blocks.deepwater.asFloor()
                    && tile.floor() != Blocks.hotrock.asFloor()
                    && tile.floor() != Blocks.magmarock.asFloor()) {
                    
                    if (tile.build == null) {
                        boolean isNextToTrack = false;
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                Tile n = tiles.get(x + dx, y + dy);
                                if (n != null && n.floor() == Blocks.darkPanel2.asFloor()) {
                                    isNextToTrack = true;
                                    break;
                                }
                            }
                            if (isNextToTrack) break;
                        }
                        if (isNextToTrack) {
                            tile.setBlock(Blocks.darkMetal);
                        }
                    }
                }
            }
        }
    }

    private static void drawBuildingPad(Tiles tiles, int centerX, int centerY, int w, int h, mindustry.world.Block leftOre, mindustry.world.Block rightOre) {
        int halfW = w / 2;
        int halfH = h / 2;
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dy = -halfH; dy <= halfH; dy++) {
                Tile tile = tiles.get(centerX + dx, centerY + dy);
                if (tile != null) {
                    // NEVER cut off or overwrite the red track floor!
                    if (tile.floor() == Blocks.darkPanel2.asFloor()) continue;

                    boolean isBorder = (Math.abs(dx) == halfW || Math.abs(dy) == halfH);
                    if (isBorder) {
                        tile.setBlock(Blocks.darkMetal);
                    } else {
                        tile.setBlock(Blocks.air);
                        tile.setFloor(Blocks.metalFloor.asFloor());

                        // Left Ore Zone (5x5 patch on left end of platform floor)
                        boolean inLeftZone = (leftOre != null) && (dx >= -halfW + 2 && dx <= -halfW + 6 && dy >= -2 && dy <= 2);
                        // Right Ore Zone (5x5 patch on right end of platform floor)
                        boolean inRightZone = (rightOre != null) && (dx >= halfW - 6 && dx <= halfW - 2 && dy >= -2 && dy <= 2);

                        if (inLeftZone) {
                            tile.setOverlay(leftOre);
                        } else if (inRightZone) {
                            tile.setOverlay(rightOre);
                        } else {
                            tile.setOverlay(Blocks.air);
                        }
                    }
                }
            }
        }
    }

    private static void placeMessageBlock(Tiles tiles, int x, int y, String text) {
        Tile t = tiles.get(x, y);
        if (t != null) {
            t.setBlock(Blocks.message, Team.sharded, 0);
            if (t.build instanceof MessageBuild msg) {
                msg.message.setLength(0);
                msg.message.append(text);
            }
        }
    }

    private static void drawOreAlcove(Tiles tiles, int centerX, int centerY, int w, int h, mindustry.world.Block ore) {
        int halfW = w / 2;
        int halfH = h / 2;
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dy = -halfH; dy <= halfH; dy++) {
                Tile tile = tiles.get(centerX + dx, centerY + dy);
                if (tile != null && tile.floor() != Blocks.darkPanel2.asFloor()) {
                    tile.setBlock(Blocks.air);
                    tile.setFloor(Blocks.darkPanel1.asFloor());
                    tile.setOverlay(ore);
                }
            }
        }
    }

    private static void buildOrePatch(Tiles tiles, int centerX, int centerY, int w, int h, mindustry.world.Block ore) {
        int halfW = w / 2;
        int halfH = h / 2;
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dy = -halfH; dy <= halfH; dy++) {
                Tile tile = tiles.get(centerX + dx, centerY + dy);
                if (tile != null && tile.block() == Blocks.air) {
                    tile.setOverlay(ore);
                }
            }
        }
    }

    private static void buildFloorPatch(Tiles tiles, int centerX, int centerY, int w, int h, mindustry.world.Block floor) {
        int halfW = w / 2;
        int halfH = h / 2;
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dy = -halfH; dy <= halfH; dy++) {
                Tile tile = tiles.get(centerX + dx, centerY + dy);
                if (tile != null) {
                    tile.setBlock(Blocks.air);
                    tile.setFloor(floor.asFloor());
                }
            }
        }
    }

    public static void checkTDExpansion() {
        if (!isTowerDefense || Vars.state == null || !Vars.state.isGame()) return;
        
        int wave = Vars.state.wave;
        
        if (wave >= 15 && tdStage < 2) {
            tdStage = 2;
            int nextSpawnX = tdStageWaypoints[1][2];
            int nextSpawnY = 32 + 2 * 75 - 3; // 179
            unlockStage(2, 105, 180, nextSpawnX, nextSpawnY);
        } else if (wave >= 35 && tdStage < 3) {
            tdStage = 3;
            int nextSpawnX = tdStageWaypoints[2][2];
            int nextSpawnY = 32 + 3 * 75 - 3; // 254
            unlockStage(3, 180, 255, nextSpawnX, nextSpawnY);
        } else if (wave >= 60 && tdStage < 4) {
            tdStage = 4;
            int nextSpawnX = tdStageWaypoints[3][2];
            int nextSpawnY = 32 + 4 * 75 - 3; // 329
            unlockStage(4, 255, 330, nextSpawnX, nextSpawnY);
        } else if (wave >= 90 && tdStage < 5) {
            tdStage = 5;
            int nextSpawnX = tdStageWaypoints[4][2];
            int nextSpawnY = 32 + 5 * 75 - 3; // 404
            unlockStage(5, 330, 405, nextSpawnX, nextSpawnY);
        }
    }

    private static void showToast(String message) {
        arc.Core.app.post(() -> {
            if (Vars.ui != null && Vars.ui.hudfrag != null) {
                Vars.ui.hudfrag.showToast(message);
            }
        });
    }

    private static void unlockStage(int stageNum, int startY, int endY, int newSpawnX, int newSpawnY) {
        // 1. Remove old spawn overlay completely
        Tile oldSpawn = Vars.world.tile(tdSpawnX, tdSpawnY);
        if (oldSpawn != null) {
            oldSpawn.setOverlay(Blocks.air);
        }

        // 2. Carve layout for newly unlocked stage out of solid darkMetal walls!
        carveStageLayout(Vars.world.tiles, stageNum);
        encloseTrackEdges(Vars.world.tiles, 100, 420);

        // 3. Expand Core Room, Upgrade Core & Unlock NEW Ores (positioned safely inside floor space)!
        if (stageNum == 2) {
            expandRectRoom(Vars.world.tiles, 50, 19, 64, 26);
            buildOrePatch(Vars.world.tiles, 23, 11, 7, 7, Blocks.oreTitanium);
            buildOrePatch(Vars.world.tiles, 77, 11, 7, 7, Blocks.oreLead);
            placeBlock(Vars.world.tiles, 50, 16, Blocks.coreFoundation, Team.sharded); // Upgrade to Core Foundation (4x4)!
            showToast("[accent]STAGE 2 UNLOCKED!\nCore Upgraded to CORE FOUNDATION (4x4)! New Titanium Ore unlocked![]");
            Vars.state.rules.teams.get(Team.crux).unitHealthMultiplier = 2.5f;
        } else if (stageNum == 3) {
            expandRectRoom(Vars.world.tiles, 50, 19, 78, 26);
            buildOrePatch(Vars.world.tiles, 16, 11, 7, 7, Blocks.oreThorium);
            buildOrePatch(Vars.world.tiles, 84, 11, 7, 7, Blocks.oreThorium);
            placeBlock(Vars.world.tiles, 50, 16, Blocks.coreNucleus, Team.sharded); // Upgrade to Core Nucleus (5x5)!
            showToast("[orange]STAGE 3 UNLOCKED!\nCore Upgraded to CORE NUCLEUS (5x5)! New Thorium Ore unlocked![]");
            Vars.state.rules.teams.get(Team.crux).unitHealthMultiplier = 6.0f;
        } else if (stageNum == 4) {
            expandRectRoom(Vars.world.tiles, 50, 19, 88, 26);
            buildOrePatch(Vars.world.tiles, 11, 21, 7, 7, Blocks.oreThorium);
            buildOrePatch(Vars.world.tiles, 89, 21, 7, 7, Blocks.oreTitanium);
            placeBlock(Vars.world.tiles, 50, 16, Blocks.coreNucleus, Team.sharded);
            showToast("[red]STAGE 4 UNLOCKED!\nCore Base Expanded to Extreme Size![]");
            Vars.state.rules.teams.get(Team.crux).unitHealthMultiplier = 15.0f;
        } else if (stageNum == 5) {
            expandRectRoom(Vars.world.tiles, 50, 19, 94, 26);
            buildOrePatch(Vars.world.tiles, 8, 11, 7, 7, Blocks.oreThorium);
            buildOrePatch(Vars.world.tiles, 92, 11, 7, 7, Blocks.oreTitanium);
            placeBlock(Vars.world.tiles, 50, 16, Blocks.coreNucleus, Team.sharded);
            showToast("[scarlet]STAGE 5 UNLOCKED!\nMaximum Core Base & Nucleus Expansion![]");
            Vars.state.rules.teams.get(Team.crux).unitHealthMultiplier = 40.0f;
        }

        // 4. Set new spawn overlay
        tdSpawnX = newSpawnX;
        tdSpawnY = newSpawnY;
        Tile newSpawn = Vars.world.tile(tdSpawnX, tdSpawnY);
        if (newSpawn != null) {
            newSpawn.setOverlay(Blocks.spawn);
        }

        // 5. Trigger pathfinding re-calculation so enemies immediately follow the newly unlocked track!
        if (Vars.pathfinder != null) {
            for (int x = 10; x < 90; x++) {
                for (int y = startY - 10; y <= endY + 10; y++) {
                    Tile t = Vars.world.tile(x, y);
                    if (t != null) {
                        Vars.pathfinder.updateTile(t);
                    }
                }
            }
        }
    }

    private static void autoConnectPowerNodes(Tiles tiles) {
        for (int x = 0; x < tiles.width; x++) {
            for (int y = 0; y < tiles.height; y++) {
                Tile tile = tiles.getn(x, y);
                if (tile.build instanceof mindustry.world.blocks.power.PowerNode.PowerNodeBuild node1) {
                    mindustry.world.blocks.power.PowerNode block1 = (mindustry.world.blocks.power.PowerNode) node1.block;
                    int range = (int) block1.laserRange;
                    
                    for (int dx = -range; dx <= range; dx++) {
                        for (int dy = -range; dy <= range; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            Tile otherTile = tiles.get(x + dx, y + dy);
                            if (otherTile != null && otherTile.build instanceof mindustry.world.blocks.power.PowerNode.PowerNodeBuild node2) {
                                if (node1.team == node2.team) {
                                    float dst = Mathf.dst(x, y, x + dx, y + dy);
                                    if (dst <= block1.laserRange) {
                                        mindustry.world.blocks.power.PowerNode block2 = (mindustry.world.blocks.power.PowerNode) node2.block;
                                        if (node1.power != null && node2.power != null) {
                                            if (node1.power.links.size < block1.maxNodes && node2.power.links.size < block2.maxNodes) {
                                                if (!node1.power.links.contains(otherTile.pos())) {
                                                    node1.configure(otherTile.pos());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
