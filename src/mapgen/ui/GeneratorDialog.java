package mapgen.ui;

import arc.scene.ui.layout.*;
import arc.scene.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.gen.*;
import mindustry.core.*;
import mindustry.*;
import mapgen.generator.ProceduralGenerator;
import mapgen.generator.ProceduralGenerator.GameMode;
import mapgen.generator.ProceduralGenerator.Difficulty;
import mapgen.generator.ProceduralGenerator.TDMode;
import mapgen.generator.ProceduralGenerator.AttackMapSource;

public class GeneratorDialog extends BaseDialog {

    private GameMode selectedMode = GameMode.Survival;
    private Difficulty selectedDifficulty = Difficulty.Random;
    private TDMode selectedTDMode = TDMode.Limit;
    private AttackMapSource selectedAttackSource = AttackMapSource.Procedural;
    private mindustry.maps.Map selectedCustomMap = null;

    public GeneratorDialog() {
        super("Map Generator");
        addCloseButton();

        setup();
    }

    private void setup() {
        cont.clear();
        cont.pane(t -> {
            t.margin(10f);

            t.add("Game Mode:").left().row();
            for (GameMode mode : GameMode.values()) {
                t.button(b -> {
                    b.add(mode.displayName());
                }, mindustry.ui.Styles.defaultb, () -> {
                    selectedMode = mode;
                    setup();
                }).width(260f).height(46f).pad(3f).color(selectedMode == mode ? arc.graphics.Color.acid : arc.graphics.Color.white).row();
            }

            if (selectedMode == GameMode.Attack) {
                t.add("Attack Map Source:").left().padTop(15f).row();
                for (AttackMapSource src : AttackMapSource.values()) {
                    t.button(b -> {
                        b.add(src.displayName);
                    }, mindustry.ui.Styles.defaultb, () -> {
                        selectedAttackSource = src;
                        setup();
                    }).width(260f).height(46f).pad(3f).color(selectedAttackSource == src ? arc.graphics.Color.acid : arc.graphics.Color.white).row();
                }

                if (selectedAttackSource == AttackMapSource.Custom) {
                    t.add("Selected Map:").left().padTop(10f).row();
                    String mapName = selectedCustomMap != null ? selectedCustomMap.name() : "Random Custom Map";
                    t.button(b -> {
                        b.add(mapName);
                    }, mindustry.ui.Styles.defaultb, () -> {
                        showMapSelectionDialog();
                    }).width(260f).height(46f).pad(3f).color(arc.graphics.Color.sky).row();
                }
            }

            if (selectedMode == GameMode.TowerDefense || selectedMode == GameMode.Survival) {
                t.add(selectedMode == GameMode.TowerDefense ? "TD Mode:" : "Survival Mode:").left().padTop(15f).row();
                for (TDMode tdMode : TDMode.values()) {
                    t.button(b -> {
                        b.add(tdMode.displayName);
                    }, mindustry.ui.Styles.defaultb, () -> {
                        selectedTDMode = tdMode;
                        setup();
                    }).width(260f).height(46f).pad(3f).color(selectedTDMode == tdMode ? arc.graphics.Color.acid : arc.graphics.Color.white).row();
                }
            }
            
            if (selectedMode != GameMode.Sandbox) {
                t.add("Difficulty:").left().padTop(15f).row();
                for (Difficulty diff : Difficulty.values()) {
                    t.button(b -> {
                        b.add(diff.name());
                    }, mindustry.ui.Styles.defaultb, () -> {
                        selectedDifficulty = diff;
                        setup();
                    }).width(260f).height(46f).pad(3f)
                      .color(selectedDifficulty == diff ? arc.graphics.Color.acid : arc.graphics.Color.white)
                      .get().addListener(new arc.scene.ui.Tooltip(tip -> {
                          if (diff == Difficulty.Random) {
                              tip.add("[gold]Random Difficulty[]\n[lightgray]Has a chance to play custom .msav maps![]");
                          } else {
                              tip.add(diff.name() + " Difficulty");
                          }
                      }));
                    t.row();
                }
            }

        }).row();

        cont.button("Generate & Play", Icon.play, () -> {
            hide();
            Vars.ui.loadAnd(() -> {
                ProceduralGenerator.generateAndPlay(selectedMode, selectedDifficulty, selectedTDMode, selectedAttackSource, selectedCustomMap);
            });
        }).size(250f, 64f).padTop(20f);
    }

    private void showMapSelectionDialog() {
        BaseDialog mapDialog = new BaseDialog("Select Custom Map");
        mapDialog.addCloseButton();

        arc.struct.Seq<mindustry.maps.Map> maps = ProceduralGenerator.getAvailableCustomAttackMaps();

        mapDialog.cont.pane(t -> {
            t.margin(10f);

            t.button(b -> {
                b.add("Random Custom Map");
            }, mindustry.ui.Styles.defaultb, () -> {
                selectedCustomMap = null;
                mapDialog.hide();
                setup();
            }).width(280f).height(46f).pad(4f).color(selectedCustomMap == null ? arc.graphics.Color.acid : arc.graphics.Color.white).row();

            if (maps.isEmpty()) {
                t.add("[lightgray]No .msav custom maps found in assets/maps/ or custom maps folder.[]").pad(10f).row();
            } else {
                for (mindustry.maps.Map m : maps) {
                    t.button(b -> {
                        b.add(m.name());
                    }, mindustry.ui.Styles.defaultb, () -> {
                        selectedCustomMap = m;
                        mapDialog.hide();
                        setup();
                    }).width(280f).height(46f).pad(4f).color(selectedCustomMap == m ? arc.graphics.Color.acid : arc.graphics.Color.white).row();
                }
            }
        }).row();

        mapDialog.show();
    }
}
