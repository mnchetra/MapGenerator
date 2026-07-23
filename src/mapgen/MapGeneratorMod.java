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
                                // If enemy unit gets wedged in a wall block, push it downward onto the track!
                                u.vel.set(0f, -2f);
                                u.trns(0f, -8f);
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
