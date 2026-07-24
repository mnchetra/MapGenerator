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
}
