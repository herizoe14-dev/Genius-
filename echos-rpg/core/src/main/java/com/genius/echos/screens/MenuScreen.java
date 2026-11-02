package com.genius.echos.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.genius.echos.EchosGame;
import com.genius.echos.Save;
import com.genius.echos.net.GameClient;
import com.genius.echos.net.GameServer;
import com.genius.echos.net.Protocol;
import com.genius.echos.ui.Ui;
import com.genius.echos.world.Rules;

import java.io.IOException;

/** Menu principal : nom, classe, solo / héberger / rejoindre. */
public class MenuScreen extends ScreenAdapter {
    private static final Color GOLD = new Color(1f, 0.82f, 0.4f, 1f);
    private static final Color SOFT = new Color(0.85f, 0.85f, 0.95f, 1f);
    private static final Color ERR = new Color(1f, 0.5f, 0.45f, 1f);

    private final EchosGame game;
    private final Ui ui;
    private String message;
    private float time;

    public MenuScreen(EchosGame game, String message) {
        this.game = game;
        this.ui = game.ui;
        this.message = message;
        game.platform.lanStop();
    }

    // ---------------------------------------------------------------- démarrage des parties

    static String hello(Save s) {
        int c = s.cls();
        return Protocol.join("HELLO", s.name(), c, s.level(c), s.xp(c), s.gold(c), s.potions(c), Protocol.VERSION);
    }

    private void playSolo() {
        try {
            GameServer server = new GameServer(game.save.worldSeed(), game.save.name(), false);
            server.start(0);
            GameClient client = new GameClient("127.0.0.1", server.getPort(), hello(game.save));
            game.setScreen(new GameScreen(game, client, server, false));
        } catch (IOException e) {
            message = "Impossible de lancer la partie : " + e.getMessage();
        }
    }

    private void host() {
        game.platform.lanStart();
        GameServer server = new GameServer(game.save.worldSeed(), game.save.name(), true);
        try {
            try {
                server.start(Protocol.TCP_PORT);
            } catch (IOException busy) {
                server = new GameServer(game.save.worldSeed(), game.save.name(), true);
                server.start(0);
            }
            GameClient client = new GameClient("127.0.0.1", server.getPort(), hello(game.save));
            game.setScreen(new GameScreen(game, client, server, true));
        } catch (IOException e) {
            game.platform.lanStop();
            message = "Impossible d'héberger : " + e.getMessage();
        }
    }

    // ---------------------------------------------------------------- rendu + entrées

    @Override
    public void render(float delta) {
        time += delta;
        Gdx.gl.glClearColor(0.07f, 0.06f, 0.11f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float W = ui.w, H = Ui.H;
        float tx = -1, ty = -1;
        if (Gdx.input.justTouched()) { tx = ui.uiX(Gdx.input.getX(), Gdx.input.getY()); ty = ui.uiY(Gdx.input.getX(), Gdx.input.getY()); }

        ui.begin();
        drawBackground(W, H);

        ui.textCentered(ui.big, "Échos d'Aube", W / 2f, H - 80, GOLD);
        ui.textCentered(ui.small, "RPG hors ligne • multijoueur sur le même Wi-Fi", W / 2f, H - 135, SOFT);

        // Nom
        float nw = 520, nx = W / 2f - nw / 2f, ny = H - 225;
        ui.button("Nom : " + game.save.name() + "   ✎", nx, ny, nw, 56, false);
        if (Ui.inside(tx, ty, nx, ny, nw, 56)) askName();

        // Classes
        int sel = game.save.cls();
        float cw = 250, ch = 210, gap = 26, total = cw * 3 + gap * 2, cx0 = W / 2f - total / 2f, cy = H - 470;
        for (int c = 0; c < 3; c++) {
            float x = cx0 + c * (cw + gap);
            boolean on = c == sel;
            ui.rect(x - 4, cy - 4, cw + 8, ch + 8, on ? GOLD : new Color(0, 0, 0, 0.7f));
            ui.rect(x, cy, cw, ch, 0.16f, 0.13f, 0.22f, 1f);
            float bob = on ? (float) Math.sin(time * 6) * 4 : 0;
            ui.sprite(game.art.heroes[c][on && ((int) (time * 4)) % 2 == 1 ? 1 : 0], x + cw / 2f - 40, cy + 92 + bob, 80, 80);
            ui.textCentered(ui.font, Rules.CLASS_NAMES[c], x + cw / 2f, cy + 72, on ? GOLD : Color.WHITE);
            ui.textCentered(ui.small, Rules.CLASS_DESC[c], x + cw / 2f, cy + 42, SOFT);
            ui.textCentered(ui.small, "Niveau " + game.save.level(c), x + cw / 2f, cy + 16, Color.LIGHT_GRAY);
            if (Ui.inside(tx, ty, x, cy, cw, ch)) game.save.setCls(c);
        }

        // Boutons
        float bw = 330, bh = 70, by = 78, bgap = 24, btot = bw * 3 + bgap * 2, bx0 = W / 2f - btot / 2f;
        String[] labels = {"Jouer seul", "Héberger (Wi-Fi)", "Rejoindre"};
        for (int i = 0; i < 3; i++) {
            float x = bx0 + i * (bw + bgap);
            ui.button(labels[i], x, by, bw, bh, i == 0);
            if (Ui.inside(tx, ty, x, by, bw, bh)) {
                ui.end();
                if (i == 0) playSolo();
                else if (i == 1) host();
                else game.setScreen(new JoinScreen(game));
                return;
            }
        }
        ui.textCentered(ui.small, "Héberger : les amis sur le même Wi-Fi (ou ton partage de connexion) te rejoignent.", W / 2f, 40, Color.GRAY);
        if (message != null) ui.textCentered(ui.font, message, W / 2f, by + bh + 40, ERR);
        ui.end();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) playSolo();
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
    }

    private void drawBackground(float W, float H) {
        // ciel crépusculaire en bandes + collines
        for (int i = 0; i < 12; i++) {
            float k = i / 11f;
            ui.rect(0, H * k, W, H / 11f + 1, 0.07f + 0.25f * (1 - k), 0.06f + 0.10f * (1 - k), 0.14f + 0.12f * (1 - k), 1f);
        }
        for (int i = 0; i < 40; i++) {
            float sx = (i * 197.3f) % W, sy = H * 0.55f + (i * 71.7f) % (H * 0.45f);
            float a = 0.4f + 0.4f * (float) Math.sin(time * 2 + i);
            ui.rect(sx, sy, 3, 3, 1, 1, 0.9f, a);
        }
        ui.circle(W * 0.82f, H * 0.18f, 90, 1f, 0.7f, 0.35f, 0.35f);
    }

    private void askName() {
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override public void input(String text) {
                final String n = Protocol.clean(text, 16);
                Gdx.app.postRunnable(() -> { if (!n.isEmpty()) game.save.setName(n); });
            }
            @Override public void canceled() {}
        }, "Ton nom de héros", game.save.name(), "Nom (16 caractères max)");
    }
}
