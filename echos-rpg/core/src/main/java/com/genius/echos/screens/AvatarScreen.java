package com.genius.echos.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.genius.echos.EchosGame;
import com.genius.echos.gfx.Art;
import com.genius.echos.gfx.CharacterDrawer;
import com.genius.echos.ui.Ui;
import com.genius.echos.world.Rules;

/** Création de l'avatar : peau, coiffure, couleur des cheveux. Aperçu animé. */
public class AvatarScreen extends ScreenAdapter {
    private static final Color GOLD = new Color(1f, 0.82f, 0.4f, 1f);
    private final EchosGame game;
    private final Ui ui;
    private int skin, style, color, dir;
    private float time;

    public AvatarScreen(EchosGame game) {
        this.game = game;
        this.ui = game.ui;
        int l = game.save.look();
        skin = CharacterDrawer.skin(l); style = CharacterDrawer.hairStyle(l); color = CharacterDrawer.hairColor(l);
    }

    @Override
    public void render(float delta) {
        time += delta;
        Gdx.gl.glClearColor(0.08f, 0.07f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float W = ui.w, H = Ui.H;
        float tx = -1, ty = -1;
        if (Gdx.input.justTouched()) { tx = ui.uiX(Gdx.input.getX(), Gdx.input.getY()); ty = ui.uiY(Gdx.input.getX(), Gdx.input.getY()); }
        int look = CharacterDrawer.look(skin, style, color);
        int cls = game.save.cls();

        ui.begin();
        ui.textCentered(ui.big, "Ton avatar", W / 2f, H - 60, GOLD);

        // aperçu : tourne sur lui-même et marche
        float pvx = W * 0.27f, pvy = 150;
        ui.panel(pvx - 180, pvy - 30, 360, 470, 0.9f);
        ui.rect(pvx - 170, pvy - 20, 340, 120, 0.42f, 0.66f, 0.3f, 1f);
        int[] dirs = {CharacterDrawer.DIR_DOWN, CharacterDrawer.DIR_RIGHT, CharacterDrawer.DIR_UP, CharacterDrawer.DIR_LEFT};
        int d = dirs[((int) (time / 1.6f)) % 4];
        int frame = new int[]{0, 1, 2, 1}[((int) (time * 6)) % 4];
        ui.batch.setColor(0, 0, 0, 0.3f);
        ui.batch.draw(game.art.shadow, pvx - 90, pvy + 6, 180, 50);
        ui.batch.setColor(Color.WHITE);
        CharacterDrawer.draw(ui.batch, game.art, look, cls, 2, 2, d, frame, pvx - 140, pvy, 280, 350);
        ui.textCentered(ui.small, Rules.CLASS_NAMES[cls] + " (la tenue change avec l'armure)", pvx, pvy - 50, Color.LIGHT_GRAY);

        // options
        float ox = W * 0.52f, ow = Math.min(560, W * 0.44f), oy = H - 200;
        String[][] rows = {
                {"Peau", Art.SKIN_NAMES[skin]},
                {"Coiffure", Art.HAIR_STYLE_NAMES[style]},
                {"Cheveux", Art.HAIR_COLOR_NAMES[color]}};
        int[] swatch = {Art.SKIN[skin], -1, Art.HAIR[color]};
        for (int r = 0; r < 3; r++) {
            float y = oy - r * 120;
            ui.text(ui.font, rows[r][0], ox, y + 84, Color.WHITE, com.badlogic.gdx.utils.Align.left);
            ui.button("‹", ox, y - 20, 80, 64, false);
            ui.button("›", ox + ow - 80, y - 20, 80, 64, false);
            ui.panel(ox + 92, y - 20, ow - 184, 64, 0.9f);
            ui.textCentered(ui.font, rows[r][1], ox + ow / 2f + (swatch[r] >= 0 ? 20 : 0), y + 12, GOLD);
            if (swatch[r] >= 0) {
                int c = swatch[r];
                ui.rect(ox + 110, y - 6, 36, 36, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f);
            }
            int delta2 = Ui.inside(tx, ty, ox, y - 20, 80, 64) ? -1 : Ui.inside(tx, ty, ox + ow - 80, y - 20, 80, 64) ? 1 : 0;
            if (delta2 != 0) {
                if (r == 0) skin = Math.floorMod(skin + delta2, 4);
                else if (r == 1) style = Math.floorMod(style + delta2, 4);
                else color = Math.floorMod(color + delta2, 6);
            }
        }
        float bw = 260, by = 50;
        ui.button("Aléatoire", ox, by, bw, 66, false);
        ui.button("Valider", ox + ow - bw, by, bw, 66, true);
        ui.end();

        if (Ui.inside(tx, ty, ox, by, bw, 66)) { skin = (int) (Math.random() * 4); style = (int) (Math.random() * 4); color = (int) (Math.random() * 6); }
        if (Ui.inside(tx, ty, ox + ow - bw, by, bw, 66) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            game.save.setLook(CharacterDrawer.look(skin, style, color));
            game.setScreen(new MenuScreen(game, null));
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new MenuScreen(game, null));
        }
    }
}
