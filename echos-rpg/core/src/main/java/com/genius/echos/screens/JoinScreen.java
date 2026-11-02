package com.genius.echos.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.genius.echos.EchosGame;
import com.genius.echos.net.GameClient;
import com.genius.echos.net.LanDiscovery;
import com.genius.echos.net.Protocol;
import com.genius.echos.ui.Ui;

import java.util.List;

/** Recherche automatique des parties sur le réseau local + saisie manuelle d'une IP. */
public class JoinScreen extends ScreenAdapter {
    private final EchosGame game;
    private final Ui ui;
    private final LanDiscovery.Scanner scanner;
    private float time;

    public JoinScreen(EchosGame game) {
        this.game = game;
        this.ui = game.ui;
        game.platform.lanStart();
        scanner = new LanDiscovery.Scanner(Protocol.UDP_PORT);
    }

    private void join(String ip, int port) {
        scanner.stop();
        GameClient client = new GameClient(ip, port, MenuScreen.hello(game.save));
        game.setScreen(new GameScreen(game, client, null, true));
    }

    @Override
    public void render(float delta) {
        time += delta;
        Gdx.gl.glClearColor(0.08f, 0.07f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float W = ui.w, H = Ui.H;
        float tx = -1, ty = -1;
        if (Gdx.input.justTouched()) { tx = ui.uiX(Gdx.input.getX(), Gdx.input.getY()); ty = ui.uiY(Gdx.input.getX(), Gdx.input.getY()); }

        ui.begin();
        ui.textCentered(ui.big, "Rejoindre une partie", W / 2f, H - 70, new Color(1f, 0.82f, 0.4f, 1f));
        String dots = "...".substring(0, 1 + ((int) (time * 2)) % 3);
        ui.textCentered(ui.small, "Recherche sur le réseau local" + dots + "  (même Wi-Fi ou partage de connexion)", W / 2f, H - 125, Color.LIGHT_GRAY);

        List<LanDiscovery.Host> hosts = scanner.hosts();
        float lw = Math.min(760, W - 80), lx = W / 2f - lw / 2f, ly = H - 230;
        if (hosts.isEmpty()) {
            ui.panel(lx, ly - 120, lw, 200, 0.85f);
            ui.textCentered(ui.font, "Aucune partie trouvée pour l'instant", W / 2f, ly + 20, Color.WHITE);
            ui.textCentered(ui.small, "Sur l'autre appareil : menu → « Héberger (Wi-Fi) ».", W / 2f, ly - 25, Color.LIGHT_GRAY);
            ui.textCentered(ui.small, "Vérifie que vous êtes connectés au même Wi-Fi.", W / 2f, ly - 60, Color.LIGHT_GRAY);
        } else {
            int i = 0;
            for (LanDiscovery.Host h : hosts) {
                if (i >= 4) break;
                float y = ly - i * 92;
                ui.button("", lx, y - 30, lw, 78, false);
                ui.text(ui.font, "⚔  Partie de " + h.name, lx + 24, y + 30, Color.WHITE, com.badlogic.gdx.utils.Align.left);
                ui.text(ui.small, h.address + "   •   " + h.players + " joueur(s)", lx + 24, y - 6, Color.LIGHT_GRAY, com.badlogic.gdx.utils.Align.left);
                ui.text(ui.font, "Rejoindre ›", lx + lw - 24, y + 14, new Color(1f, 0.82f, 0.4f, 1f), com.badlogic.gdx.utils.Align.right);
                if (Ui.inside(tx, ty, lx, y - 30, lw, 78)) { ui.end(); join(h.address, h.port); return; }
                i++;
            }
        }

        float bw = 330, bh = 66, by = 50;
        ui.button("‹ Retour", W / 2f - bw - 15, by, bw, bh, false);
        ui.button("Entrer une IP", W / 2f + 15, by, bw, bh, false);
        ui.end();

        if (Ui.inside(tx, ty, W / 2f - bw - 15, by, bw, bh) || Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            scanner.stop();
            game.setScreen(new MenuScreen(game, null));
            return;
        }
        if (Ui.inside(tx, ty, W / 2f + 15, by, bw, bh)) askIp();
    }

    private void askIp() {
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override public void input(String text) {
                final String t = text.trim();
                Gdx.app.postRunnable(() -> {
                    if (t.isEmpty()) return;
                    String ip = t; int port = Protocol.TCP_PORT;
                    int colon = t.lastIndexOf(':');
                    if (colon > 0) { ip = t.substring(0, colon); port = Protocol.i(t.substring(colon + 1), Protocol.TCP_PORT); }
                    game.save.setLastIp(t);
                    join(ip, port);
                });
            }
            @Override public void canceled() {}
        }, "Adresse IP de l'hôte", game.save.lastIp(), "ex. 192.168.1.20");
    }

    @Override public void hide() { scanner.stop(); }
}
