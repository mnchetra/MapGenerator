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

public class GeneratorDialog extends BaseDialog {

    private GameMode selectedMode = GameMode.Survival;
    private Difficulty selectedDifficulty = Difficulty.Normal;
    private TDMode selectedTDMode = TDMode.Limit;

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
                    }).width(260f).height(46f).pad(3f).color(selectedDifficulty == diff ? arc.graphics.Color.acid : arc.graphics.Color.white).row();
                }
            }

        }).row();

        cont.button("Generate & Play", Icon.play, () -> {
            hide();
            Vars.ui.loadAnd(() -> {
                ProceduralGenerator.generateAndPlay(selectedMode, selectedDifficulty, selectedTDMode);
            });
        }).size(250f, 64f).padTop(20f);
    }
}
