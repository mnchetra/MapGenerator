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
    private static int[][] tdStageWaypoints = new int[5][7]; // [stageIndex][w1, w2, w3, turn1Y, turn2Y, patternType, stageEndX]

    public enum GameMode {
        Attack, Survival, Sandbox, TowerDefense;

        public String displayName() {
            if (this == TowerDefense) return "Tower Defense";
            return name();
        }
    }

    public enum TDMode {
        Limit("Limit"), Endless("Endless");

        public final String displayName;

        TDMode(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum Difficulty {
        Easy(250), Normal(350), Hard(450);

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
        generateAndPlay(mode, difficulty, TDMode.Limit);
    }

    public static void generateAndPlay(GameMode mode, Difficulty difficulty, TDMode tdMode) {
        try {
            Vars.ui.loadAnd(() -> {
                Vars.logic.reset();

                int size = difficulty.size + Mathf.random(-20, 30); // Random map size variance
                
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
                    
                    // We will spawn multiple bases instead of just 1 or 2!
                    // Handled internally in generateBases/generateTerrain using the map size.
                } else {
                    cx = size / 2;
                    cy = size / 2;
                    sx = Mathf.random(20, size - 20);
                    sy = size - 20;
                }

                // Final variables for lambdas
                final int fSx = sx, fSy = sy;

                if (mode == GameMode.TowerDefense) {
                    isTowerDefense = true;
                    isEndlessTD = (tdMode == TDMode.Endless);
                    tdStage = 1;
                    int mapWidth = 100;
                    int mapHeight = 420;

                    Vars.world.loadGenerator(mapWidth, mapHeight, tiles -> {
                        generateTowerDefenseMap(tiles, mapWidth, mapHeight);
                    });
                } else if (mode == GameMode.Survival) {
                    isTowerDefense = false;
                    isEndlessTD = (tdMode == TDMode.Endless);
                    int survSize = (difficulty == Difficulty.Easy) ? 220 : ((difficulty == Difficulty.Normal) ? 280 : 340);

                    Vars.world.loadGenerator(survSize, survSize, tiles -> {
                        generateCivilizationSurvivalMap(tiles, survSize, difficulty, tdMode);
                    });
                } else if (mode == GameMode.Sandbox) {
                    isTowerDefense = false;
                    isEndlessTD = false;
                    int sandboxSize = 150;

                    Vars.world.loadGenerator(sandboxSize, sandboxSize, tiles -> {
                        generateSandboxMap(tiles, sandboxSize);
                    });
                } else {
                    isTowerDefense = false;
                    isEndlessTD = false;
                    
                    // Generate enemy base coordinates based on difficulty
                    arc.struct.Seq<arc.math.geom.Point2> enemyBases = new arc.struct.Seq<>();
                    if (mode == GameMode.Attack) {
                        int numBases = difficulty == Difficulty.Easy ? 2 : (difficulty == Difficulty.Normal ? 3 : 5);
                        for (int i = 0; i < numBases; i++) {
                            // Scatter bases away from player core
                            int bx, by;
                            do {
                                bx = Mathf.random(40, size - 40);
                                by = Mathf.random(40, size - 40);
                            } while (Mathf.dst(cx, cy, bx, by) < 120); // Keep them away from player
                            enemyBases.add(new arc.math.geom.Point2(bx, by));
                        }
                    }

                    Vars.world.loadGenerator(size, size, tiles -> {
                        generateTerrain(tiles, size, mode, difficulty, offsetX, offsetY, cx, cy, enemyBases, fSx, fSy);
                        
                        // Generate bases inside loadGenerator (isGenerating = true)
                        generateBases(tiles, mode, difficulty, cx, cy, enemyBases, fSx, fSy);
                        
                        // Post-process: Auto-connect all power nodes & surge towers so the enemy base is 100% powered!
                        autoConnectPowerNodes(tiles);
                    });
                }

                Rules rules = new Rules();
                setupRules(rules, mode, difficulty, tdMode);

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

                if (mode == GameMode.TowerDefense) {
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

    private static void generateTerrain(Tiles tiles, int size, GameMode mode, Difficulty difficulty, float offsetX, float offsetY, int cx, int cy, arc.struct.Seq<arc.math.geom.Point2> enemyBases, int sx, int sy) {
        Biome[] biomes = Biome.values();
        Biome biome = biomes[Mathf.random(biomes.length - 1)];
        boolean hasSand = biome == Biome.Desert || biome == Biome.Archipelago;

        if (mode == GameMode.Attack) {
            // Complex Cavern & River Generation (Ridged Multi-fractal)
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    Tile tile = new Tile(x, y, biome.baseFloor.id, Blocks.air.id, Blocks.air.id);
                    tiles.set(x, y, tile);

                    // Ridged multi-fractal for cellular cave walls
                    float ridgeNoise = Math.abs(Simplex.noise3d(0, 4, 0.55f, 1f / 80f, x + offsetX, y + offsetY, 0f));
                    // Base room generation (lower frequency)
                    float roomNoise = Simplex.noise3d(1, 3, 0.5f, 1f / 120f, x + offsetX, y + offsetY, 0f);
                    
                    // Liquid rivers (winding curves)
                    float riverNoise = Math.abs(Simplex.noise3d(2, 2, 0.5f, 1f / 150f, x + offsetX, y + offsetY, 0f));

                    boolean isRiver = riverNoise < 0.08f;
                    boolean isWall = ridgeNoise > 0.35f && roomNoise < 0.6f && !isRiver;
                    boolean isDeepRiver = riverNoise < 0.03f;
                    
                    if (isDeepRiver) {
                        tile.setFloor(biome.deepLiquid.asFloor());
                    } else if (isRiver) {
                        tile.setFloor(biome.liquid.asFloor());
                    } else {
                        // Floor variation
                        float floorNoise = Simplex.noise2d(3, 3, 0.5f, 1f / 50f, x + offsetX, y + offsetY);
                        if (floorNoise > 0.6f) tile.setFloor(biome.altFloor.asFloor());
                        else tile.setFloor(biome.baseFloor.asFloor());
                    }

                    boolean isWater = tile.floor() == biome.deepLiquid || tile.floor() == biome.shallowLiquid || tile.floor() == biome.liquid;

                    if (isWall) {
                        tile.setBlock(biome.wall);
                    } else if (!isWater) {
                        // Props
                        float propNoise = Simplex.noise2d(7, 4, 0.6f, 1f / 15f, x + offsetX, y + offsetY);
                        if (propNoise > 0.75f) tile.setBlock(biome.prop);
                        
                        // Ores
                        if (tile.block() == Blocks.air) {
                            float copperNoise = Simplex.noise2d(10, 2, 0.5f, 1f / 20f, x + offsetX, y + offsetY);
                            float leadNoise = Simplex.noise2d(11, 2, 0.5f, 1f / 20f, x + offsetX, y + offsetY);
                            float coalNoise = Simplex.noise2d(12, 2, 0.5f, 1f / 25f, x + offsetX, y + offsetY);
                            float scrapNoise = Simplex.noise2d(13, 2, 0.5f, 1f / 25f, x + offsetX, y + offsetY);
                            float titaniumNoise = Simplex.noise2d(14, 2, 0.5f, 1f / 22f, x + offsetX, y + offsetY);
                            float thoriumNoise = Simplex.noise2d(15, 2, 0.5f, 1f / 22f, x + offsetX, y + offsetY);
                            float scrapThreshold = hasSand ? 0.75f : 0.55f; // Spawn TONS of scrap if no sand

                            if (copperNoise > 0.75f) tile.setOverlay(Blocks.oreCopper);
                            else if (leadNoise > 0.75f) tile.setOverlay(Blocks.oreLead);
                            else if (coalNoise > 0.75f) tile.setOverlay(Blocks.oreCoal);
                            else if (scrapNoise > scrapThreshold) tile.setOverlay(Blocks.oreScrap);
                            else if (titaniumNoise > 0.75f && (difficulty != Difficulty.Easy)) tile.setOverlay(Blocks.oreTitanium);
                            else if (thoriumNoise > 0.75f && difficulty == Difficulty.Hard) tile.setOverlay(Blocks.oreThorium);
                        }
                    }
                }
            }
        } else {
            // Standard smooth blobby terrain for Survival/Sandbox/TowerDefense
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
                        float scrapThreshold = hasSand ? 0.75f : 0.55f; // Spawn TONS of scrap if no sand

                        if (copperNoise > 0.75f) tile.setOverlay(Blocks.oreCopper);
                        else if (leadNoise > 0.75f) tile.setOverlay(Blocks.oreLead);
                        else if (coalNoise > 0.75f) tile.setOverlay(Blocks.oreCoal);
                        else if (scrapNoise > scrapThreshold) tile.setOverlay(Blocks.oreScrap);
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
        }

        if (mode == GameMode.TowerDefense) {
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
            // Path carving for Attack Mode
            // Carve paths between player core and ALL enemy cores so they are never blocked
            for (arc.math.geom.Point2 base : enemyBases) {
                int midX = (cx + base.x) / 2 + Mathf.random(-size/4, size/4);
                int midY = (cy + base.y) / 2 + Mathf.random(-size/4, size/4);
                midX = Mathf.clamp(midX, 20, size - 20);
                midY = Mathf.clamp(midY, 20, size - 20);
                carveOrganicPath(tiles, cx, cy, midX, midY, base.x, base.y, 6, offsetX, offsetY);
            }
            
            // Connect enemy cores together to make a web
            for (int i = 0; i < enemyBases.size - 1; i++) {
                arc.math.geom.Point2 b1 = enemyBases.get(i);
                arc.math.geom.Point2 b2 = enemyBases.get(i + 1);
                int midX = (b1.x + b2.x) / 2 + Mathf.random(-size/6, size/6);
                int midY = (b1.y + b2.y) / 2 + Mathf.random(-size/6, size/6);
                midX = Mathf.clamp(midX, 20, size - 20);
                midY = Mathf.clamp(midY, 20, size - 20);
                carveOrganicPath(tiles, b1.x, b1.y, midX, midY, b2.x, b2.y, 6, offsetX, offsetY);
            }
        } else if (mode == GameMode.Survival || mode == GameMode.TowerDefense) {
            if (mode == GameMode.TowerDefense) {
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
                        // Single-arg setBlock(Blocks.air) correctly clears any block type
                        // (walls, props, buildings) during map generation
                        t.setBlock(Blocks.air);
                        t.setOverlay(Blocks.air);
                        // Make sure the floor is solid so cores/buildings don't explode on liquid
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

    private static void generateBases(Tiles tiles, GameMode mode, Difficulty difficulty, int cx, int cy, arc.struct.Seq<arc.math.geom.Point2> enemyBases, int sx, int sy) {
        // Place Player Core
        clearArea(tiles, cx, cy, 18);
        
        // Explicitly spawn starting ores near the player core since clearArea wipes all natural ores!
        buildOrePatch(tiles, cx - 8, cy + 8, 6, 6, Blocks.oreCopper);
        buildOrePatch(tiles, cx + 8, cy - 8, 6, 6, Blocks.oreLead);
        
        placeBlock(tiles, cx, cy, Blocks.coreShard, Team.sharded);

        if (mode == GameMode.Attack) {
            mindustry.maps.generators.BaseGenerator baseGen = new mindustry.maps.generators.BaseGenerator();
            mindustry.type.Sector dummySector = mindustry.content.Planets.serpulo.sectors.get(0);
            float diffFloat = difficulty == Difficulty.Hard ? 1f : (difficulty == Difficulty.Normal ? 0.5f : 0.2f);
            
            arc.struct.Seq<Tile> enemyCoreTiles = new arc.struct.Seq<>();
            
            // Loop through all generated enemy bases
            for (arc.math.geom.Point2 base : enemyBases) {
                clearArea(tiles, base.x, base.y, 15); // Small clear area for natural integration
                mindustry.world.Block coreType = difficulty == Difficulty.Hard ? Blocks.coreNucleus : (difficulty == Difficulty.Normal ? Blocks.coreFoundation : Blocks.coreShard);
                placeBlock(tiles, base.x, base.y, coreType, mindustry.game.Team.crux);
                enemyCoreTiles.add(tiles.get(base.x, base.y));
                tiles.getn(base.x, base.y).setOverlay(Blocks.spawn); // Minimap visibility
            }
            
            // Generate standard sprawling AI bases around all cores!
            if (enemyCoreTiles.size > 0) {
                baseGen.generate(tiles, enemyCoreTiles, enemyCoreTiles.first(), mindustry.game.Team.crux, dummySector, diffFloat);
            }

            // Cleanup pass: Remove any enemy base structures that were placed within 75 blocks of the player core!
            int safeRadius = 75;
            for (int dx = -safeRadius; dx <= safeRadius; dx++) {
                for (int dy = -safeRadius; dy <= safeRadius; dy++) {
                    if (dx * dx + dy * dy <= safeRadius * safeRadius) {
                        Tile t = tiles.get(cx + dx, cy + dy);
                        if (t != null && t.team() == Team.crux) {
                            t.setBlock(Blocks.air); 
                        }
                    }
                }
            }
        } else if (mode == GameMode.Survival || mode == GameMode.TowerDefense) {
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

    private static void setupRules(Rules rules, GameMode mode, Difficulty difficulty, TDMode tdMode) {
        rules.waves = true;
        rules.waveTimer = true;
        rules.winWave = (mode == GameMode.TowerDefense && tdMode == TDMode.Limit) ? 100 : 50;

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
            case TowerDefense:
                rules.waveSpacing = 3000f; // 50 seconds per wave
                break;
            case Survival:
                rules.waveSpacing = 7200f;
                break;
        }

        switch (difficulty) {
            case Easy:
                rules.dropZoneRadius = mode == GameMode.TowerDefense ? 25f : 300f;
                rules.buildCostMultiplier = 0.5f;
                rules.blockHealthMultiplier = 1.5f;
                break;
            case Normal:
                rules.dropZoneRadius = mode == GameMode.TowerDefense ? 25f : 150f;
                break;
            case Hard:
                rules.dropZoneRadius = mode == GameMode.TowerDefense ? 25f : 100f;
                rules.buildCostMultiplier = 1.5f;
                rules.blockHealthMultiplier = 0.8f;
                break;
        }

        if (mode == GameMode.TowerDefense) {
            rules.dropZoneRadius = 25f; // Small, clean spawn circle around spawn point
            rules.teams.get(Team.crux).unitDamageMultiplier = 1.0f; // Enemies deal damage to Core!
            rules.teams.get(Team.crux).unitHealthMultiplier = 1.0f;
        }

        if (mode == GameMode.Survival) {
            rules.waves = true;
            rules.waveTimer = true;
            rules.waveSpacing = 3600f; // 60 seconds per wave
            rules.initialWaveSpacing = 18000f; // 5 minutes initial grace period to let player prepare!
            rules.spawns.clear();

            if (tdMode == TDMode.Limit) {
                rules.winWave = 100;
            } else {
                rules.winWave = 0; // Endless waves!
            }

            // Early waves: small scouting parties only
            SpawnGroup g1 = new SpawnGroup(UnitTypes.dagger);
            g1.begin = 1; g1.end = 15; g1.unitAmount = 2; g1.unitScaling = 0.5f;
            rules.spawns.add(g1);

            SpawnGroup g2 = new SpawnGroup(UnitTypes.crawler);
            g2.begin = 5; g2.end = 25; g2.unitAmount = 3; g2.unitScaling = 0.8f;
            rules.spawns.add(g2);

            SpawnGroup g3 = new SpawnGroup(UnitTypes.mace);
            g3.begin = 15; g3.end = 50; g3.unitAmount = 2; g3.unitScaling = 0.5f;
            rules.spawns.add(g3);

            SpawnGroup g4 = new SpawnGroup(UnitTypes.fortress);
            g4.begin = 30; g4.end = 70; g4.unitAmount = 1; g4.unitScaling = 0.3f;
            rules.spawns.add(g4);

            SpawnGroup g5 = new SpawnGroup(UnitTypes.spiroct);
            g5.begin = 40; g5.end = 80; g5.unitAmount = 2; g5.unitScaling = 0.4f;
            rules.spawns.add(g5);

            SpawnGroup g6 = new SpawnGroup(UnitTypes.scepter);
            g6.begin = 55; g6.end = 99999; g6.unitAmount = 1; g6.unitScaling = 0.3f;
            rules.spawns.add(g6);

            SpawnGroup g7 = new SpawnGroup(UnitTypes.reign);
            g7.begin = 75; g7.end = 99999; g7.unitAmount = 1; g7.unitScaling = 0.3f;
            rules.spawns.add(g7);

            SpawnGroup g8 = new SpawnGroup(UnitTypes.toxopid);
            g8.begin = 85; g8.end = 99999; g8.unitAmount = 1; g8.unitScaling = 0.3f;
            rules.spawns.add(g8);
        } else if (mode == GameMode.TowerDefense) {
            rules.waves = true;
            rules.waveTimer = true;
            rules.waveSpacing = 3000f; // 50 seconds per wave
            rules.spawns.clear();

            if (tdMode == TDMode.Limit) {
                rules.winWave = 100;
            } else {
                rules.winWave = 0; // Endless waves!
            }

            int stage5End = (tdMode == TDMode.Limit) ? 100 : 99999;

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

            int w1 = leftFirst ? xl : xr;
            int w2 = leftFirst ? xr : xl;
            int w3 = leftFirst ? xl : xr;
            int stageEndX = (pattern == 2) ? w2 : w3;

            tdStageWaypoints[s][0] = w1;
            tdStageWaypoints[s][1] = w2;
            tdStageWaypoints[s][2] = w3;
            tdStageWaypoints[s][3] = turn1Y;
            tdStageWaypoints[s][4] = turn2Y;
            tdStageWaypoints[s][5] = pattern;
            tdStageWaypoints[s][6] = stageEndX;
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
        drawTrackLine(tiles, 50, 18, 50, 32, 3);

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

        tdSpawnX = tdStageWaypoints[0][6];
        tdSpawnY = 32 + 75 - 2; // 105 (Exact top end of Stage 1 track!)
        tiles.getn(tdSpawnX, tdSpawnY).setOverlay(Blocks.spawn);
    }

    private static void enclosePoolBorder(Tiles tiles, int centerX, int centerY, int w, int h) {
        int halfW = w / 2 + 1;
        int halfH = h / 2 + 1;
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dy = -halfH; dy <= halfH; dy++) {
                if (Math.abs(dx) == halfW || Math.abs(dy) == halfH) {
                    Tile t = tiles.get(centerX + dx, centerY + dy);
                    if (t != null) {
                        t.setBlock(Blocks.darkMetal);
                    }
                }
            }
        }
    }

    private static void generateSandboxMap(Tiles tiles, int size) {
        // 1. Fill entire 150x150 map floor with Black Sand (darksand)
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                Tile tile = new Tile(x, y, Blocks.darksand.id, Blocks.air.id, Blocks.air.id);
                tiles.set(x, y, tile);
            }
        }

        // 2. Enclose outer map edges with 3-tile thick solid darkMetal border walls
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (x < 3 || x >= size - 3 || y < 3 || y >= size - 3) {
                    Tile t = tiles.get(x, y);
                    if (t != null) t.setBlock(Blocks.darkMetal);
                }
            }
        }

        int cx = size / 2; // 75
        int cy = size / 2; // 75

        // 3. Central Player Base (Metal Floor Platform, Core Nucleus & Erekir Cores!)
        buildFloorPatch(tiles, cx, cy, 32, 32, Blocks.metalFloor);
        placeBlock(tiles, cx, cy, Blocks.coreNucleus, Team.sharded);

        // Erekir Cores (Bastion, Citadel, Acropolis)
        placeBlock(tiles, cx - 10, cy - 8, Blocks.coreBastion, Team.sharded);
        placeBlock(tiles, cx, cy - 8, Blocks.coreCitadel, Team.sharded);
        placeBlock(tiles, cx + 10, cy - 8, Blocks.coreAcropolis, Team.sharded);

        // 4. Place Sandbox Source Helpers (Item Source, Liquid Source, Power Source) near Core
        placeBlock(tiles, cx - 10, cy + 8, Blocks.itemSource, Team.sharded);
        placeBlock(tiles, cx, cy + 8, Blocks.liquidSource, Team.sharded);
        placeBlock(tiles, cx + 10, cy + 8, Blocks.powerSource, Team.sharded);
        placeMessageBlock(tiles, cx, cy + 12, "[gold]SANDBOX ARENA (150x150)[]\nBlack Sand Floor, All Ores, Liquids & Erekir Cores!");

        // 5. EVERY ORE IN THE GAME (Pure 8x8 Square Ore Patches on Black Sand floor!)
        mindustry.world.Block[] allOres = {
            Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreScrap,
            Blocks.oreTitanium, Blocks.oreThorium, Blocks.oreBeryllium, Blocks.oreTungsten
        };

        String[] oreNames = {
            "COPPER ORE", "LEAD ORE", "COAL ORE", "SCRAP ORE",
            "TITANIUM ORE", "THORIUM ORE", "BERYLLIUM ORE", "TUNGSTEN ORE"
        };

        int startOreX = 17;
        int oreSpacing = 16;
        int oreY = 35;

        for (int i = 0; i < allOres.length; i++) {
            int ox = startOreX + i * oreSpacing;
            if (ox + 4 < size - 5) {
                buildOrePatch(tiles, ox, oreY, 8, 8, allOres[i]);
                placeMessageBlock(tiles, ox, oreY - 6, "[accent]" + oreNames[i] + " SQUARE[]");
            }
        }

        // 6. EVERY LIQUID TYPE IN THE GAME (Pure 8x8 Square Liquid Pools on Black Sand floor!)
        mindustry.world.Block[] allLiquids = {
            Blocks.water, Blocks.deepwater, Blocks.slag, Blocks.tar,
            Blocks.cryofluid, Blocks.taintedWater, Blocks.darksandTaintedWater
        };

        String[] liquidNames = {
            "WATER POOL", "DEEP WATER", "SLAG POOL", "OIL / TAR POOL",
            "CRYOFLUID POOL", "TAINTED WATER", "DARK TAINTED WATER"
        };

        int startLiquidX = 17;
        int liquidSpacing = 16;
        int liquidY = 115;

        for (int i = 0; i < allLiquids.length; i++) {
            int lx = startLiquidX + i * liquidSpacing;
            if (lx + 4 < size - 5) {
                buildFloorPatch(tiles, lx, liquidY, 8, 8, allLiquids[i]);
                placeMessageBlock(tiles, lx, liquidY - 6, "[sky]" + liquidNames[i] + " SQUARE[]");
            }
        }

        // 7. THERMAL POWER ZONES (Left & Right Wings at y = 75)
        buildFloorPatch(tiles, 20, 75, 8, 8, Blocks.hotrock);
        placeMessageBlock(tiles, 20, 69, "[orange]HOTROCK POWER ZONE[]");

        buildFloorPatch(tiles, 130, 75, 8, 8, Blocks.magmarock);
        placeMessageBlock(tiles, 130, 69, "[red]MAGMAROCK POWER ZONE[]");
    }

    private static void generateCivilizationSurvivalMap(Tiles tiles, int size, Difficulty diff, TDMode tdMode) {
        int cx = size / 2;
        int cy = size / 2;

        // ─────── RANDOMLY SELECT A CIVILIZATION THEME EACH PLAY ───────
        // Each theme changes: terrain floors, wall blocks, capital plaza floor,
        //   capital wall material, outpost styles, ore bonuses, and flavor text.
        int themeIndex = Mathf.random(0, 4);
        String themeName;

        // Theme data
        mindustry.world.Block terrainBase, terrainAlt, terrainLiquid, terrainWall;
        mindustry.world.Block capitalFloor, capitalPlaza, capitalWall;
        mindustry.world.Block outpostFloorA, outpostFloorB;
        mindustry.world.Block eastOre1, eastOre2, westLiquid, westLiquid2, northOre1, northOre2, southLiquid, southOre;
        mindustry.world.Block[] bonus;

        switch (themeIndex) {
            case 0: // 🌿 FOREST KINGDOM
                themeName = "[green]FOREST KINGDOM[]";
                terrainBase = Blocks.grass; terrainAlt = Blocks.dirt;
                terrainLiquid = Blocks.water; terrainWall = Blocks.pine;
                capitalFloor = Blocks.darkPanel1; capitalPlaza = Blocks.metalFloor;
                capitalWall = Blocks.darkMetal;
                outpostFloorA = Blocks.grass; outpostFloorB = Blocks.dirt;
                eastOre1 = Blocks.oreTitanium; eastOre2 = Blocks.oreCopper;
                westLiquid = Blocks.water; westLiquid2 = Blocks.tar;
                northOre1 = Blocks.oreCopper; northOre2 = Blocks.oreLead;
                southLiquid = Blocks.cryofluid; southOre = Blocks.oreCoal;
                bonus = new mindustry.world.Block[]{Blocks.oreCopper, Blocks.oreLead};
                break;
            case 1: // 🌋 VOLCANIC EMPIRE
                themeName = "[orange]VOLCANIC EMPIRE[]";
                terrainBase = Blocks.basalt; terrainAlt = Blocks.hotrock;
                terrainLiquid = Blocks.slag; terrainWall = Blocks.stoneWall;
                capitalFloor = Blocks.basalt; capitalPlaza = Blocks.hotrock;
                capitalWall = Blocks.stoneWall;
                outpostFloorA = Blocks.basalt; outpostFloorB = Blocks.hotrock;
                eastOre1 = Blocks.oreThorium; eastOre2 = Blocks.oreScrap;
                westLiquid = Blocks.slag; westLiquid2 = Blocks.tar;
                northOre1 = Blocks.oreTitanium; northOre2 = Blocks.oreThorium;
                southLiquid = Blocks.slag; southOre = Blocks.oreScrap;
                bonus = new mindustry.world.Block[]{Blocks.oreTitanium, Blocks.oreThorium};
                break;
            case 2: // ❄️ ARCTIC DOMINION
                themeName = "[cyan]ARCTIC DOMINION[]";
                terrainBase = Blocks.snow; terrainAlt = Blocks.ice;
                terrainLiquid = Blocks.water; terrainWall = Blocks.snowWall;
                capitalFloor = Blocks.ice; capitalPlaza = Blocks.snow;
                capitalWall = Blocks.snowWall;
                outpostFloorA = Blocks.snow; outpostFloorB = Blocks.iceSnow;
                eastOre1 = Blocks.oreLead; eastOre2 = Blocks.oreCopper;
                westLiquid = Blocks.water; westLiquid2 = Blocks.cryofluid;
                northOre1 = Blocks.oreScrap; northOre2 = Blocks.oreLead;
                southLiquid = Blocks.cryofluid; southOre = Blocks.oreTitanium;
                bonus = new mindustry.world.Block[]{Blocks.oreLead, Blocks.oreScrap};
                break;
            case 3: // 🏜️ DESERT SULTANATE
                themeName = "[yellow]DESERT SULTANATE[]";
                terrainBase = Blocks.sand; terrainAlt = Blocks.darksand;
                terrainLiquid = Blocks.darksandTaintedWater; terrainWall = Blocks.sandWall;
                capitalFloor = Blocks.darksand; capitalPlaza = Blocks.sand;
                capitalWall = Blocks.sandWall;
                outpostFloorA = Blocks.sand; outpostFloorB = Blocks.darksand;
                eastOre1 = Blocks.oreCopper; eastOre2 = Blocks.oreLead;
                westLiquid = Blocks.darksandTaintedWater; westLiquid2 = Blocks.tar;
                northOre1 = Blocks.oreScrap; northOre2 = Blocks.oreCoal;
                southLiquid = Blocks.tar; southOre = Blocks.oreCopper;
                bonus = new mindustry.world.Block[]{Blocks.oreCopper, Blocks.oreCoal};
                break;
            default: // 🍄 SPORE FEDERATION
                themeName = "[purple]SPORE FEDERATION[]";
                terrainBase = Blocks.sporeMoss; terrainAlt = Blocks.moss;
                terrainLiquid = Blocks.taintedWater; terrainWall = Blocks.sporeWall;
                capitalFloor = Blocks.moss; capitalPlaza = Blocks.sporeMoss;
                capitalWall = Blocks.sporeWall;
                outpostFloorA = Blocks.sporeMoss; outpostFloorB = Blocks.moss;
                eastOre1 = Blocks.oreCoal; eastOre2 = Blocks.oreLead;
                westLiquid = Blocks.taintedWater; westLiquid2 = Blocks.darksandTaintedWater;
                northOre1 = Blocks.oreBeryllium; northOre2 = Blocks.oreTungsten;
                southLiquid = Blocks.taintedWater; southOre = Blocks.oreCoal;
                bonus = new mindustry.world.Block[]{Blocks.oreBeryllium, Blocks.oreTungsten};
                break;
        }

        // ─────── 1. FILL ALL TILES WITH BASE TERRAIN ───────
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                Tile tile = new Tile(x, y, terrainBase.id, Blocks.air.id, Blocks.air.id);
                tiles.set(x, y, tile);
            }
        }

        // ─────── 2. TERRAIN NOISE (Floor Variety + Mountain Walls) ───────
        float offsetX = Mathf.random(100000f);
        float offsetY = Mathf.random(100000f);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float n = Simplex.noise2d(1, 3, 0.5f, 1f / 60f, x + offsetX, y + offsetY);
                float m = Simplex.noise2d(2, 3, 0.5f, 1f / 50f, x + offsetX, y + offsetY);
                Tile t = tiles.get(x, y);
                if (t == null) continue;

                if (n < -0.3f) {
                    t.setFloor(terrainLiquid.asFloor());
                } else if (n > 0.3f) {
                    t.setFloor(terrainAlt.asFloor());
                } else {
                    t.setFloor(terrainBase.asFloor());
                }

                // Scattered ore deposits in the wilderness (theme-appropriate)
                if (t.floor() != terrainLiquid.asFloor()) {
                    float oreN = Simplex.noise2d(5, 2, 0.5f, 1f / 22f, x + offsetX, y + offsetY);
                    if (oreN > 0.78f) t.setOverlay(bonus[0]);
                    else if (oreN < -0.78f) t.setOverlay(bonus[1]);
                }

                // Mountain walls
                float wNoise = Simplex.noise2d(3, 3, 0.5f, 1f / 70f, x + offsetX, y + offsetY);
                if (wNoise > 0.48f && t.floor() != terrainLiquid.asFloor()) {
                    t.setBlock(terrainWall);
                    t.setOverlay(Blocks.air);
                }
            }
        }

        // ─────── 3. CAPITAL CITY CORES ───────
        // Force-clear a large area around capital center so cores always place correctly
        clearArea(tiles, cx, cy, 25);
        buildFloorPatch(tiles, cx, cy, 38, 38, capitalFloor);
        buildFloorPatch(tiles, cx, cy, 32, 32, capitalPlaza);

        // Explicitly clear core footprints
        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                Tile t = tiles.get(cx + dx, cy + dy);
                if (t != null) {
                    t.setBlock(Blocks.air);
                    t.setOverlay(Blocks.air);
                    t.setFloor(capitalPlaza.asFloor());
                }
            }
        }

        // Place Core Shard at center (guarantees player unit spawning in custom generator mode)
        placeBlock(tiles, cx, cy, Blocks.coreShard, Team.sharded);
        // Place Core Nucleus beside it for maximum storage capacity & capital aesthetic!
        placeBlock(tiles, cx + 6, cy, Blocks.coreNucleus, Team.sharded);

        // City Walls with 4 gate openings
        int halfWall = 19;
        for (int dx = -halfWall; dx <= halfWall; dx++) {
            for (int dy = -halfWall; dy <= halfWall; dy++) {
                if (Math.abs(dx) == halfWall || Math.abs(dy) == halfWall) {
                    boolean isGate = (Math.abs(dx) <= 2 && Math.abs(dy) == halfWall)
                                  || (Math.abs(dy) <= 2 && Math.abs(dx) == halfWall);
                    Tile t = tiles.get(cx + dx, cy + dy);
                    if (t != null) {
                        if (isGate) {
                            t.setBlock(Blocks.air);
                            t.setFloor(capitalFloor.asFloor());
                        } else {
                            t.setBlock(capitalWall);
                        }
                    }
                }
            }
        }

        // ─── CORNER WATCHTOWER TURRETS (Arc = no ammo needed!) ───
        int[][] corners = { {-halfWall, -halfWall}, {halfWall, -halfWall}, {-halfWall, halfWall}, {halfWall, halfWall} };
        for (int[] corner : corners) {
            placeBlock(tiles, cx + corner[0], cy + corner[1], Blocks.arc, Team.sharded);
        }

        // ─── SIDE TURRETS (no item sources — gather resources naturally!) ───
        int[] sideMid = {-8, 8};
        for (int s : sideMid) {
            placeBlock(tiles, cx + s, cy + halfWall, Blocks.scatter, Team.sharded);
            placeBlock(tiles, cx + s, cy - halfWall, Blocks.scatter, Team.sharded);
            placeBlock(tiles, cx + halfWall, cy + s, Blocks.scatter, Team.sharded);
            placeBlock(tiles, cx - halfWall, cy + s, Blocks.scatter, Team.sharded);
        }

        // ─── EXTRA DEFENSIVE OUTER WALL RING (2 tiles outside capital wall) ───
        int outerRing = halfWall + 3;
        for (int dx = -outerRing; dx <= outerRing; dx++) {
            for (int dy = -outerRing; dy <= outerRing; dy++) {
                if (Math.abs(dx) == outerRing || Math.abs(dy) == outerRing) {
                    boolean isOuterGate = (Math.abs(dx) <= 2 && Math.abs(dy) == outerRing)
                                       || (Math.abs(dy) <= 2 && Math.abs(dx) == outerRing);
                    Tile t = tiles.get(cx + dx, cy + dy);
                    if (t != null && !isOuterGate) {
                        t.setBlock(capitalWall);
                    }
                }
            }
        }

        // ─── ORE DEPOSITS inside the Capital City Plaza ───
        // (Convenient starter ores inside city walls so you can mine right away!)
        buildOrePatch(tiles, cx - 12, cy - 8,  7, 7, Blocks.oreCopper);
        buildOrePatch(tiles, cx + 12, cy - 8,  7, 7, Blocks.oreLead);
        buildOrePatch(tiles, cx - 12, cy + 8,  7, 7, Blocks.oreCoal);
        buildOrePatch(tiles, cx + 12, cy + 8,  7, 7, Blocks.oreScrap);
        buildOrePatch(tiles, cx,      cy - 13, 7, 7, Blocks.oreTitanium);
        buildOrePatch(tiles, cx,      cy + 13, 7, 7, Blocks.oreThorium);

        // Theme label sign
        String modeLabel = (tdMode == TDMode.Limit) ? "(LIMIT - Survive 100 Waves!)" : "(ENDLESS - Survive as long as possible!)";
        placeMessageBlock(tiles, cx, cy + 18, themeName + "\n[accent]" + modeLabel + "[]");

        // ─────── 4. RADIAL AVENUES ───────
        int dist = size / 3;
        drawTrackLine(tiles, cx, cy + halfWall + 1, cx, cy + dist, 4); // North
        drawTrackLine(tiles, cx, cy - halfWall - 1, cx, cy - dist, 4); // South
        drawTrackLine(tiles, cx + halfWall + 1, cy, cx + dist, cy, 4); // East
        drawTrackLine(tiles, cx - halfWall - 1, cy, cx - dist, cy, 4); // West

        // ─────── 5. OUTPOST DISTRICTS (themed, rich ore deposits) ───────
        // EAST: Metal & Ore Foundry (Titanium, Thorium, Scrap, Copper, Silicon, Metaglass)
        int eX = cx + dist, eY = cy;
        clearArea(tiles, eX, eY, 15);
        buildFloorPatch(tiles, eX, eY, 24, 24, outpostFloorA);
        buildOrePatch(tiles, eX - 7, eY - 7, 7, 7, eastOre1);
        buildOrePatch(tiles, eX + 7, eY - 7, 7, 7, eastOre2);
        buildOrePatch(tiles, eX - 7, eY + 7, 7, 7, Blocks.oreScrap);
        buildOrePatch(tiles, eX + 7, eY + 7, 7, 7, Blocks.oreCopper);
        buildOrePatch(tiles, eX,     eY - 7, 7, 7, Blocks.oreLead);
        buildOrePatch(tiles, eX,     eY + 7, 7, 7, Blocks.oreCoal);
        placeMessageBlock(tiles, eX, eY + 13, "[accent]EAST FOUNDRY []");

        // WEST: Liquid & Power (Coal, Lead, Copper + themed liquids)
        int wX = cx - dist, wY = cy;
        clearArea(tiles, wX, wY, 15);
        buildFloorPatch(tiles, wX, wY, 24, 24, outpostFloorB);
        buildFloorPatch(tiles, wX - 6, wY - 6, 7, 7, westLiquid);
        buildFloorPatch(tiles, wX + 6, wY - 6, 7, 7, westLiquid2);
        buildOrePatch(tiles, wX - 6, wY + 6, 7, 7, Blocks.oreCoal);
        buildOrePatch(tiles, wX + 6, wY + 6, 7, 7, Blocks.oreLead);
        buildOrePatch(tiles, wX,     wY - 6, 7, 7, Blocks.oreCopper);
        buildOrePatch(tiles, wX,     wY + 6, 7, 7, Blocks.oreScrap);
        placeMessageBlock(tiles, wX, wY + 13, "[sky]WEST REFINERY []");

        // NORTH: Watchtower Keep (Copper, Lead, Titanium, Thorium, Coal, Scrap)
        int nX = cx, nY = cy + dist;
        clearArea(tiles, nX, nY, 15);
        buildFloorPatch(tiles, nX, nY, 24, 24, outpostFloorA);
        buildOrePatch(tiles, nX - 7, nY - 7, 7, 7, northOre1);
        buildOrePatch(tiles, nX + 7, nY - 7, 7, 7, northOre2);
        buildOrePatch(tiles, nX - 7, nY + 7, 7, 7, Blocks.oreTitanium);
        buildOrePatch(tiles, nX + 7, nY + 7, 7, 7, Blocks.oreThorium);
        buildOrePatch(tiles, nX,     nY - 7, 7, 7, Blocks.oreCoal);
        buildFloorPatch(tiles, nX,   nY + 7, 7, 7, Blocks.hotrock);
        placeMessageBlock(tiles, nX, nY + 13, "[orange]NORTH KEEP []");

        // SOUTH: Resource Hub (Copper, Lead, Coal, Scrap + themed liquids)
        int sX = cx, sY = cy - dist;
        clearArea(tiles, sX, sY, 15);
        buildFloorPatch(tiles, sX, sY, 24, 24, outpostFloorB);
        buildFloorPatch(tiles, sX - 6, sY - 6, 7, 7, southLiquid);
        buildOrePatch(tiles, sX + 6, sY - 6, 7, 7, southOre);
        buildOrePatch(tiles, sX - 6, sY + 6, 7, 7, Blocks.oreCopper);
        buildOrePatch(tiles, sX + 6, sY + 6, 7, 7, Blocks.oreLead);
        buildOrePatch(tiles, sX,     sY - 6, 7, 7, Blocks.oreScrap);
        buildOrePatch(tiles, sX,     sY + 6, 7, 7, Blocks.oreCoal);
        placeMessageBlock(tiles, sX, sY - 13, "[scarlet]SOUTH HUB []");

        // ─────── 6. ENEMY INVASION DROP POINTS ───────
        int margin = 25;
        // 4 Spawns in wilderness corners pointing to the 4 City Gates
        int[][] spawnTargetPairs = {
            {margin, size - margin, cx - 15, cy + 15, cx, cy + halfWall + 5},        // North-West -> North Gate
            {size - margin, size - margin, cx + 15, cy + 15, cx + halfWall + 5, cy},  // North-East -> East Gate
            {margin, margin, cx - 15, cy - 15, cx - halfWall - 5, cy},               // South-West -> West Gate
            {size - margin, margin, cx + 15, cy - 15, cx, cy - halfWall - 5}         // South-East -> South Gate
        };

        for (int[] st : spawnTargetPairs) {
            int sx = st[0], sy = st[1];
            int midX = st[2], midY = st[3];
            int gateX = st[4], gateY = st[5];

            clearArea(tiles, sx, sy, 10);
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -6; dy <= 6; dy++) {
                    Tile t = tiles.get(sx + dx, sy + dy);
                    if (t != null) {
                        t.setBlock(Blocks.air);
                        t.setFloor(Blocks.basalt.asFloor()); // Dark staging ground floor
                    }
                }
            }

            // Spawn overlay (standard wave spawn marker)
            Tile spawnTile = tiles.get(sx, sy);
            if (spawnTile != null) spawnTile.setOverlay(Blocks.spawn);

            // ── MINIMAP VISIBILITY: place Team.crux buildings → shows as RED on minimap ──
            // Central enemy vault (3x3, bright red square on minimap)
            placeBlock(tiles, sx, sy, Blocks.vault, Team.crux);
            // Surrounding crux turrets (reinforce the visual cluster on minimap + dangerous!)
            placeBlock(tiles, sx - 4, sy,     Blocks.duo, Team.crux);
            placeBlock(tiles, sx + 4, sy,     Blocks.duo, Team.crux);
            placeBlock(tiles, sx,     sy - 4, Blocks.duo, Team.crux);
            placeBlock(tiles, sx,     sy + 4, Blocks.duo, Team.crux);
            // Label the drop zone
            placeMessageBlock(tiles, sx, sy + 6, "[red]⚠ ENEMY DROP ZONE ⚠[]");

            // Carve open invasion path from spawn point to outer city gate
            carveOrganicPath(tiles, sx, sy, midX, midY, gateX, gateY, 5, offsetX, offsetY);
        }

        // ─────── 7. FINAL CORE PROTECTION PASS ───────
        // Re-clear & place player Cores at Capital Plaza AFTER all paths are carved!
        clearArea(tiles, cx, cy, 15);
        buildFloorPatch(tiles, cx, cy, 32, 32, capitalPlaza);

        // Core Shard (Primary active spawn core)
        placeBlock(tiles, cx - 3, cy, Blocks.coreShard, Team.sharded);
        // Core Nucleus (Secondary capital core)
        placeBlock(tiles, cx + 3, cy, Blocks.coreNucleus, Team.sharded);
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

    private static void drawStageTrackOnly(Tiles tiles, int stageNum) {
        int yBase = 32 + (stageNum - 1) * 75;
        int endY = yBase + 75;

        int w1 = tdStageWaypoints[stageNum - 1][0];
        int w2 = tdStageWaypoints[stageNum - 1][1];
        int w3 = tdStageWaypoints[stageNum - 1][2];
        int turn1Y = tdStageWaypoints[stageNum - 1][3];
        int turn2Y = tdStageWaypoints[stageNum - 1][4];
        int pattern = tdStageWaypoints[stageNum - 1][5];

        int startX = (stageNum == 1) ? 50 : tdStageWaypoints[stageNum - 2][6];
        int startY = (stageNum == 1) ? 18 : yBase;

        // Render Distinct Track Patterns based on stage pattern selection (Width = 3 to match 3x3 Small Core!)
        if (pattern == 0) { // Double S-Bend
            drawTrackLine(tiles, startX, startY, startX, yBase, 3);
            drawTrackLine(tiles, startX, yBase, w1, yBase, 3);
            drawTrackLine(tiles, w1, yBase, w1, turn1Y, 3);
            drawTrackLine(tiles, w1, turn1Y, w2, turn1Y, 3);
            drawTrackLine(tiles, w2, turn1Y, w2, turn2Y, 3);
            drawTrackLine(tiles, w2, turn2Y, w3, turn2Y, 3);
            drawTrackLine(tiles, w3, turn2Y, w3, endY, 3);
        } else if (pattern == 1) { // Wide C-Loop
            drawTrackLine(tiles, startX, startY, startX, yBase, 3);
            drawTrackLine(tiles, startX, yBase, w1, yBase, 3);
            drawTrackLine(tiles, w1, yBase, w1, turn2Y, 3);
            drawTrackLine(tiles, w1, turn2Y, w3, turn2Y, 3);
            drawTrackLine(tiles, w3, turn2Y, w3, endY, 3);
        } else if (pattern == 2) { // Center Expressway
            drawTrackLine(tiles, startX, startY, startX, yBase, 3);
            drawTrackLine(tiles, startX, yBase, w2, yBase, 3);
            drawTrackLine(tiles, w2, yBase, w2, endY, 3);
        } else if (pattern == 3) { // Sharp Zig-Zag
            drawTrackLine(tiles, startX, startY, startX, yBase, 3);
            drawTrackLine(tiles, startX, yBase, w1, yBase, 3);
            drawTrackLine(tiles, w1, yBase, w1, turn1Y, 3);
            drawTrackLine(tiles, w1, turn1Y, w3, turn1Y, 3);
            drawTrackLine(tiles, w3, turn1Y, w3, endY, 3);
        }

        // Smooth and widen track turn corners
        carveCorner(tiles, startX, yBase, 2);
        if (pattern == 0) {
            carveCorner(tiles, w1, yBase, 2);
            carveCorner(tiles, w1, turn1Y, 2);
            carveCorner(tiles, w2, turn1Y, 2);
            carveCorner(tiles, w2, turn2Y, 2);
            carveCorner(tiles, w3, turn2Y, 2);
        } else if (pattern == 1) {
            carveCorner(tiles, w1, yBase, 2);
            carveCorner(tiles, w1, turn2Y, 2);
            carveCorner(tiles, w3, turn2Y, 2);
        } else if (pattern == 2) {
            carveCorner(tiles, w2, yBase, 2);
        } else if (pattern == 3) {
            carveCorner(tiles, w1, yBase, 2);
            carveCorner(tiles, w1, turn1Y, 2);
            carveCorner(tiles, w3, turn1Y, 2);
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
        int stageEndX = tdStageWaypoints[stageNum - 1][6];

        // Draw exact 3-tile wide track lines for this stage
        drawStageTrackOnly(tiles, stageNum);

        // Carve Wide 10x10 Spawn Chamber at the EXACT top end of the stage track (connected to path!)
        createSpawnRoom(tiles, stageEndX, endY - 2);

        // Calculate exact vertical track positions at lower (yBase+14) and upper (yBase+42) segments for THIS pattern
        int activeTrack1X = (pattern == 2) ? w2 : w1;
        int activeTrack2X = (pattern == 0) ? w2 : ((pattern == 1) ? w1 : ((pattern == 2) ? w2 : w3));

        // Position building pads with a clean 2-tile wall gap from the track corridor!
        int pad1X, pad2X;
        if (activeTrack1X == activeTrack2X) {
            // When both pads border the same vertical track line, place pad 1 on left and pad 2 on right!
            pad1X = Math.max(18, activeTrack1X - 18);
            pad2X = Math.min(82, activeTrack2X + 18);
        } else {
            pad1X = (activeTrack1X <= 50) ? Math.min(82, activeTrack1X + 18) : Math.max(18, activeTrack1X - 18);
            pad2X = (activeTrack2X <= 50) ? Math.min(82, activeTrack2X + 18) : Math.max(18, activeTrack2X - 18);
        }

        int pad1Y = yBase + 14;
        int pad2Y = yBase + 42;

        pad1X = findValidPadX(tiles, pad1X, pad1Y, 26, 14);
        pad2X = findValidPadX(tiles, pad2X, pad2Y, 26, 14);

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
                    // NEVER modify or touch tiles that have existing buildings (like the Core or player turrets)!
                    if (tile.build != null) continue;

                    boolean isLeftOrRightWall = (x == minX || x == maxX);
                    boolean isBottomWall = (y == minY);
                    boolean isTopWall = (y == maxY);
                    boolean isTrackEntrance = (isTopWall && x >= 47 && x <= 53);

                    if ((isLeftOrRightWall || isBottomWall || isTopWall) && !isTrackEntrance) {
                        if (tile.floor() != Blocks.darkPanel2.asFloor()) {
                            tile.setBlock(Blocks.darkMetal);
                        }
                    } else {
                        // Do not clear darkMetal wall if it borders the red track floor!
                        if (tile.block() == Blocks.darkMetal && !isNextToTrack(tiles, x, y)) {
                            tile.setBlock(Blocks.air);
                        }
                        if (tile.floor() != Blocks.metalFloor.asFloor() 
                            && tile.floor() != Blocks.darkPanel2.asFloor() 
                            && tile.floor() != Blocks.water.asFloor() 
                            && tile.floor() != Blocks.sand.asFloor()
                            && tile.floor() != Blocks.darksand.asFloor()
                            && tile.floor() != Blocks.tar.asFloor()
                            && tile.floor() != Blocks.cryofluid.asFloor()) {
                            tile.setFloor(Blocks.darkPanel1.asFloor());
                        }
                    }
                }
            }
        }
    }

    private static boolean isNextToTrack(Tiles tiles, int x, int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                Tile n = tiles.get(x + dx, y + dy);
                if (n != null && n.floor() == Blocks.darkPanel2.asFloor()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void drawTrackLine(Tiles tiles, int x1, int y1, int x2, int y2, int width) {
        int halfW = width / 2;
        int maxOffset = (width % 2 == 0) ? halfW - 1 : halfW;
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        for (int x = minX - (x1 == x2 ? halfW : 0); x <= maxX + (x1 == x2 ? maxOffset : 0); x++) {
            for (int y = minY - (y1 == y2 ? halfW : 0); y <= maxY + (y1 == y2 ? maxOffset : 0); y++) {
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
                    && tile.floor() != Blocks.magmarock.asFloor()
                    && tile.floor() != Blocks.darksand.asFloor()
                    && tile.floor() != Blocks.tar.asFloor()
                    && tile.floor() != Blocks.cryofluid.asFloor()) {
                    
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

    private static int findValidPadX(Tiles tiles, int preferredX, int padY, int padW, int padH) {
        int halfW = padW / 2;
        int halfH = padH / 2;

        // Search starting from preferredX, then outwards (+1, -1, +2, -2, ...) to find exact 2-tile gap with complete border walls!
        for (int offset = 0; offset <= 40; offset++) {
            int[] dxs = (offset == 0) ? new int[]{0} : new int[]{offset, -offset};
            for (int dx : dxs) {
                int candidateX = preferredX + dx;
                if (candidateX - halfW < 2 || candidateX + halfW > 98) continue;

                boolean hasTrackOverlap = false;
                for (int x = candidateX - halfW; x <= candidateX + halfW; x++) {
                    for (int y = padY - halfH; y <= padY + halfH; y++) {
                        Tile t = tiles.get(x, y);
                        if (t != null && t.floor() == Blocks.darkPanel2.asFloor()) {
                            hasTrackOverlap = true;
                            break;
                        }
                    }
                    if (hasTrackOverlap) break;
                }
                if (!hasTrackOverlap) {
                    return candidateX;
                }
            }
        }
        return preferredX;
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
            int nextSpawnX = tdStageWaypoints[1][6];
            int nextSpawnY = 32 + 2 * 75 - 2; // 180
            unlockStage(2, 105, 180, nextSpawnX, nextSpawnY);
        } else if (wave >= 35 && tdStage < 3) {
            tdStage = 3;
            int nextSpawnX = tdStageWaypoints[2][6];
            int nextSpawnY = 32 + 3 * 75 - 2; // 255
            unlockStage(3, 180, 255, nextSpawnX, nextSpawnY);
        } else if (wave >= 60 && tdStage < 4) {
            tdStage = 4;
            int nextSpawnX = tdStageWaypoints[3][6];
            int nextSpawnY = 32 + 4 * 75 - 2; // 330
            unlockStage(4, 255, 330, nextSpawnX, nextSpawnY);
        } else if (wave >= 90 && tdStage < 5) {
            tdStage = 5;
            int nextSpawnX = tdStageWaypoints[4][6];
            int nextSpawnY = 32 + 5 * 75 - 2; // 405
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

    private static void fillSpawnBoxWithWall(Tiles tiles, int centerX, int centerY) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                Tile t = tiles.get(centerX + dx, centerY + dy);
                if (t != null) {
                    t.setFloor(Blocks.darkPanel1.asFloor());
                    t.setBlock(Blocks.darkMetal);
                }
            }
        }
    }

    private static void unlockStage(int stageNum, int startY, int endY, int newSpawnX, int newSpawnY) {
        // 1. Remove old spawn overlay completely
        Tile oldSpawn = Vars.world.tile(tdSpawnX, tdSpawnY);
        if (oldSpawn != null) {
            oldSpawn.setOverlay(Blocks.air);
        }

        // 2. Expand Core Room & Unlock NEW Ores & Liquid Pools (Core remains 100% safe & untouched)!
        if (stageNum == 2) {
            expandRectRoom(Vars.world.tiles, 50, 19, 64, 26);
            buildOrePatch(Vars.world.tiles, 23, 11, 7, 7, Blocks.oreTitanium);
            buildOrePatch(Vars.world.tiles, 77, 11, 7, 7, Blocks.oreLead);
            buildFloorPatch(Vars.world.tiles, 77, 21, 6, 6, Blocks.darksand); // Black Sand!
            showToast("[accent]STAGE 2 UNLOCKED!\nNew Titanium Ore & Black Sand unlocked![]");
            Vars.state.rules.teams.get(Team.crux).unitHealthMultiplier = 2.5f;
        } else if (stageNum == 3) {
            expandRectRoom(Vars.world.tiles, 50, 19, 78, 26);
            buildOrePatch(Vars.world.tiles, 16, 11, 7, 7, Blocks.oreThorium);
            buildOrePatch(Vars.world.tiles, 84, 11, 7, 7, Blocks.oreThorium);
            buildFloorPatch(Vars.world.tiles, 23, 21, 6, 6, Blocks.tar); // Liquid Oil Pool!
            showToast("[orange]STAGE 3 UNLOCKED!\nNew Thorium Ore & Liquid Oil Pool unlocked![]");
            Vars.state.rules.teams.get(Team.crux).unitHealthMultiplier = 6.0f;
        } else if (stageNum == 4) {
            expandRectRoom(Vars.world.tiles, 50, 19, 88, 26);
            buildOrePatch(Vars.world.tiles, 11, 21, 7, 7, Blocks.oreThorium);
            buildOrePatch(Vars.world.tiles, 89, 21, 7, 7, Blocks.oreTitanium);
            buildFloorPatch(Vars.world.tiles, 14, 11, 6, 6, Blocks.cryofluid); // Cryofluid Pool!
            buildFloorPatch(Vars.world.tiles, 86, 11, 6, 6, Blocks.darksand); // Heavy Black Sand Patch!
            showToast("[red]STAGE 4 UNLOCKED!\nExtreme Expansion! Cryofluid Pool & Heavy Defense unlocked![]");
            Vars.state.rules.teams.get(Team.crux).unitHealthMultiplier = 15.0f;
        } else if (stageNum == 5) {
            expandRectRoom(Vars.world.tiles, 50, 19, 94, 26);
            buildOrePatch(Vars.world.tiles, 8, 11, 7, 7, Blocks.oreThorium);
            buildOrePatch(Vars.world.tiles, 92, 11, 7, 7, Blocks.oreTitanium);
            buildFloorPatch(Vars.world.tiles, 8, 21, 7, 7, Blocks.tar); // Expanded Oil Supply Hub!
            buildFloorPatch(Vars.world.tiles, 92, 21, 7, 7, Blocks.cryofluid); // Expanded Cryofluid Supply Hub!
            showToast("[scarlet]STAGE 5 UNLOCKED!\nMaximum Expansion! Full Oil, Cryofluid & Resource Supply Hubs![]");
            Vars.state.rules.teams.get(Team.crux).unitHealthMultiplier = 40.0f;
        }

        // 3. Carve layout for newly unlocked stage out of solid darkMetal walls!
        carveStageLayout(Vars.world.tiles, stageNum);

        // 4. Fill old spawn box bulges with solid darkMetal walls!
        for (int s = 0; s < stageNum - 1; s++) {
            int oldX = tdStageWaypoints[s][6];
            int oldY = 32 + (s + 1) * 75 - 2;
            fillSpawnBoxWithWall(Vars.world.tiles, oldX, oldY);
        }

        // 5. Re-carve all active stage track lines from Stage 1 up to current stage!
        for (int s = 1; s <= stageNum; s++) {
            drawStageTrackOnly(Vars.world.tiles, s);
        }

        // 6. Enclose ALL track edges with solid darkMetal fencing LAST!
        encloseTrackEdges(Vars.world.tiles, 100, 420);

        // 7. Set new spawn overlay
        tdSpawnX = newSpawnX;
        tdSpawnY = newSpawnY;
        Tile newSpawn = Vars.world.tile(tdSpawnX, tdSpawnY);
        if (newSpawn != null) {
            newSpawn.setOverlay(Blocks.spawn);
        }

        // 8. Explicitly ensure game state remains active and playing
        Vars.state.gameOver = false;
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
