package mapgen;

import arc.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.gen.Groups;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;
import mindustry.*;
import mapgen.ui.GeneratorDialog;
import mapgen.generator.ProceduralGenerator;

import arc.math.Mathf;
import mindustry.content.Blocks;

public class MapGeneratorMod extends Mod {
    private GeneratorDialog dialog;

    public MapGeneratorMod() {
        Log.info("Loaded MapGeneratorMod constructor.");
        
        if (!Vars.headless) {
            arc.Events.on(ClientLoadEvent.class, e -> {
                checkForUpdates();

                dialog = new GeneratorDialog();
                // Add a global button that is only visible in the main menu
                arc.scene.ui.layout.Table t = new arc.scene.ui.layout.Table();
                t.bottom().left();
                t.button("Map Gen", mindustry.gen.Icon.map, () -> {
                    dialog.show();
                }).size(150f, 60f).pad(20f);
                
                t.visibility = () -> Vars.state.isMenu();
                arc.Core.scene.add(t);
            });
        }

        // Register wave event listener for dynamic expanding Tower Defense mode
        arc.Events.on(WaveEvent.class, e -> {
            if (Vars.state != null && Vars.state.isGame()) {
                ProceduralGenerator.checkTDExpansion();
            }
        });

        // Victory Event: Ask player if they want to continue playing or exit
        arc.Events.on(GameOverEvent.class, e -> {
            if (Vars.state != null && (e.winner == Vars.player.team() || e.winner == mindustry.game.Team.sharded)) {
                arc.Core.app.post(() -> {
                    showVictoryContinueDialog();
                });
            }
        });

        // Core-Only Damage Protection & Auto-Unstick for Enemy Units
        arc.Events.run(Trigger.update, () -> {
            if (ProceduralGenerator.isTowerDefense && Vars.state != null && Vars.state.isGame()) {
                if (Groups.build != null) {
                    Groups.build.each(b -> {
                        if (b != null && !(b.block instanceof CoreBlock)) {
                            b.health = 999999f;
                        }
                    });
                }

                if (Groups.unit != null) {
                    Groups.unit.each(u -> {
                        if (u != null && u.team == mindustry.game.Team.crux) {
                            mindustry.world.Tile tile = u.tileOn();
                            if (tile != null && tile.block().solid) {
                                // Find nearest non-solid tile to gently unstick the unit towards track floor
                                mindustry.world.Tile bestTile = null;
                                float bestDstSq = Float.MAX_VALUE;
                                int ux = tile.x;
                                int uy = tile.y;
                                
                                for (int dx = -3; dx <= 3; dx++) {
                                    for (int dy = -3; dy <= 3; dy++) {
                                        mindustry.world.Tile near = Vars.world.tile(ux + dx, uy + dy);
                                        if (near != null && !near.block().solid) {
                                            float dstSq = dx * dx + dy * dy;
                                            if (near.floor() == Blocks.darkPanel2.asFloor()) {
                                                dstSq -= 5f; // Strongly prefer track floor
                                            }
                                            if (dstSq < bestDstSq) {
                                                bestDstSq = dstSq;
                                                bestTile = near;
                                            }
                                        }
                                    }
                                }
                                
                                if (bestTile != null) {
                                    float dx = bestTile.worldx() - u.x;
                                    float dy = bestTile.worldy() - u.y;
                                    float len = Mathf.len(dx, dy);
                                    if (len > 0.001f) {
                                        u.trns((dx / len) * 3f, (dy / len) * 3f);
                                    }
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void loadContent() {
        Log.info("Loading MapGenerator content.");
    }

    private static void showVictoryContinueDialog() {
        if (Vars.headless) return;

        BaseDialog winDialog = new BaseDialog("Victory!");
        
        winDialog.cont.add("[gold]Victory! You won![]").fontScale(1.2f).pad(15f).row();
        winDialog.cont.add("Do you want to continue playing on this map?").pad(10f).row();

        winDialog.buttons.button("No", mindustry.gen.Icon.cancel, () -> {
            winDialog.hide();
            Vars.logic.reset();
            Vars.state.set(mindustry.core.GameState.State.menu);
        }).size(150f, 54f).pad(10f);

        winDialog.buttons.button("Continue", mindustry.gen.Icon.play, () -> {
            winDialog.hide();
            Vars.state.set(mindustry.core.GameState.State.playing);
            if (Vars.state.rules != null) {
                Vars.state.rules.canGameOver = false;
            }
        }).size(150f, 54f).pad(10f);

        winDialog.show();
    }

    private static void checkForUpdates() {
        try {
            Http.get("https://api.github.com/repos/mnchetra/MapGenerator/releases/latest")
                .header("User-Agent", "MindustryMod")
                .error(t -> Log.err("Failed to check for MapGenerator updates: @", t.getMessage()))
                .submit(response -> {
                    try {
                        String jsonStr = response.getResultAsString();
                        arc.util.serialization.Jval json = arc.util.serialization.Jval.read(jsonStr);
                        String latestTag = json.getString("tag_name", "").replace("v", "").trim();
                        String currentVersion = "1.1";

                        if (!latestTag.isEmpty() && isNewerVersion(latestTag, currentVersion)) {
                            Log.info("MapGenerator update available: @ (Current: @)", latestTag, currentVersion);
                            Core.app.post(() -> {
                                if (Vars.ui != null && Vars.ui.hudfrag != null) {
                                    Vars.ui.hudfrag.showToast("[gold]MapGenerator Update Available![]\n[accent]Version " + latestTag + " is available on GitHub![]");
                                }
                            });
                        }
                    } catch (Throwable t) {
                        // Ignore parse errors
                    }
                });
        } catch (Throwable t) {
            // Ignore network errors
        }
    }

    private static boolean isNewerVersion(String latest, String current) {
        try {
            String[] l = latest.split("\\.");
            String[] c = current.split("\\.");
            for (int i = 0; i < Math.max(l.length, c.length); i++) {
                int lv = i < l.length ? Integer.parseInt(l[i].replaceAll("[^0-9]", "")) : 0;
                int cv = i < c.length ? Integer.parseInt(c[i].replaceAll("[^0-9]", "")) : 0;
                if (lv > cv) return true;
                if (lv < cv) return false;
            }
        } catch (Exception e) {
            return !latest.equalsIgnoreCase(current);
        }
        return false;
    }
}
