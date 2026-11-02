package com.genius.echos.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.genius.echos.EchosGame;
import com.genius.echos.gfx.Art;
import com.genius.echos.gfx.CharacterDrawer;
import com.genius.echos.gfx.WorldRenderer;
import com.genius.echos.net.ClientState;
import com.genius.echos.net.GameClient;
import com.genius.echos.net.GameServer;
import com.genius.echos.net.LanDiscovery;
import com.genius.echos.net.Protocol;
import com.genius.echos.ui.Ui;
import com.genius.echos.world.Inventory;
import com.genius.echos.world.Items;
import com.genius.echos.world.Rules;
import com.genius.echos.world.WorldMap;

import java.util.ArrayList;
import java.util.List;

/** Écran de jeu : monde, commandes tactiles, interface, boutiques et dépôt. */
public class GameScreen extends ScreenAdapter {
    private static final float VIEW_TILES = 11f;
    private static final Color GOLD = new Color(1f, 0.82f, 0.4f, 1f);
    private static final Color SOFT = new Color(0.82f, 0.82f, 0.9f, 1f);
    private static final Color BAD = new Color(1f, 0.45f, 0.4f, 1f);
    private static final Color[] TXT_COLORS = {
            Color.WHITE, new Color(1f, 0.35f, 0.3f, 1f), new Color(0.4f, 1f, 0.45f, 1f),
            new Color(1f, 0.85f, 0.3f, 1f), new Color(1f, 0.6f, 0.15f, 1f), new Color(0.55f, 0.75f, 1f, 1f)};

    private static final int P_NONE = -1, P_BAG = 10;   // sinon : type de boutique (0..3)

    private final EchosGame game;
    private final Ui ui;
    private final Art art;
    private final GameClient client;
    private final GameServer server;
    private final boolean lan;
    private final int cls, look;
    private final ClientState state = new ClientState();
    private final OrthographicCamera cam = new OrthographicCamera();
    private final SpriteBatch batch;
    private WorldRenderer world;
    private Texture minimap;
    private final List<String> localIps;

    private float time, posT, atkT, saveT, flushT, quitArmed, toastT;
    private String toast;
    private float lastSentX = -1, lastSentY = -1;
    private int lastSentDir = -1;
    private boolean lastSentMoving, wasSafe = true;

    // commandes tactiles
    private final boolean[] wasTouched = new boolean[5];
    private int joyPointer = -1;
    private float joyBaseX, joyBaseY, joyX, joyY;
    private boolean attackHeld;

    // fenêtres
    private int panel = P_NONE;
    private float tapX = -1, tapY = -1;
    private int selInv = -1, selEquip = -1, shopPage;

    public GameScreen(EchosGame game, GameClient client, GameServer server, boolean lan) {
        this.game = game;
        this.ui = game.ui;
        this.art = game.art;
        this.client = client;
        this.server = server;
        this.lan = lan;
        this.cls = game.save.cls();
        this.look = game.save.look();
        this.batch = ui.batch;
        this.localIps = server != null && lan ? LanDiscovery.localAddresses() : new ArrayList<>();
    }

    // ================================================================ boucle

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.1f);
        time += dt;

        String line;
        while ((line = client.inbox.poll()) != null) state.process(line);
        if (client.isClosed()) { leave(client.getError()); return; }

        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (!state.welcomed) { renderConnecting(); return; }
        if (minimap == null) buildMinimap();
        if (world == null) world = new WorldRenderer(art, state.map);

        handleInput(dt);
        if (left) return;
        state.update(dt);
        persist(dt);

        renderWorld();
        renderHud(dt);
    }

    private void renderConnecting() {
        float tx = -1, ty = -1;
        if (Gdx.input.justTouched()) { tx = ui.uiX(Gdx.input.getX(), Gdx.input.getY()); ty = ui.uiY(Gdx.input.getX(), Gdx.input.getY()); }
        ui.begin();
        String dots = "...".substring(0, 1 + ((int) (time * 2)) % 3);
        ui.textCentered(ui.font, "Connexion à la partie" + dots, ui.w / 2f, Ui.H / 2f + 40, Color.WHITE);
        float bw = 300, bx = ui.w / 2f - bw / 2f, by = Ui.H / 2f - 90;
        ui.button("Annuler", bx, by, bw, 64, false);
        ui.end();
        if (Ui.inside(tx, ty, bx, by, bw, 64) || Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) leave(null);
    }

    // ================================================================ disposition des boutons

    private float bx(float fromRight) { return ui.w - fromRight; }
    private static final float ATK_Y = 135, ATK_R = 84, SK_Y = 80, SK_R = 54, HP_Y = 228, HP_R = 42, MP_Y = 290, MP_R = 42, BAG_Y = 64, BAG_R = 42, CHAT_Y = 392, CHAT_R = 36;
    private float atkX() { return bx(130); }
    private float skX() { return bx(290); }
    private float hpX() { return bx(262); }
    private float mpX() { return bx(132); }
    private float bagX() { return bx(408); }
    private float chatX() { return bx(60); }
    private float quitX() { return bx(130); }
    private static final float QUIT_Y = Ui.H - 62, QUIT_W = 115, QUIT_H = 48;
    private static final float ENTER_W = 330, ENTER_H = 68, ENTER_Y = 26;

    private static boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private WorldMap.Building nearShop() {
        return state.map == null ? null : state.map.shopAt(state.myX, state.myY, GameServer.SHOP_RANGE - 0.2f);
    }

    private boolean nearFountain() {
        float dx = state.myX - WorldMap.FOUNTAIN_X, dy = state.myY - WorldMap.FOUNTAIN_Y;
        return dx * dx + dy * dy < 3.4f * 3.4f;
    }

    // ================================================================ entrées

    private void handleInput(float dt) {
        attackHeld = false;
        tapX = tapY = -1;
        quitArmed = Math.max(0, quitArmed - dt);
        for (int p = 0; p < wasTouched.length; p++) {
            boolean down = Gdx.input.isTouched(p);
            if (down) {
                float ux = ui.uiX(Gdx.input.getX(p), Gdx.input.getY(p));
                float uy = ui.uiY(Gdx.input.getX(p), Gdx.input.getY(p));
                if (panel != P_NONE) {
                    if (!wasTouched[p]) { tapX = ux; tapY = uy; }
                } else if (p == joyPointer) {
                    joyX = ux; joyY = uy;
                } else {
                    if (inCircle(ux, uy, atkX(), ATK_Y, ATK_R * 1.15f)) attackHeld = true;
                    if (!wasTouched[p]) onTap(p, ux, uy);
                }
            } else if (p == joyPointer) joyPointer = -1;
            wasTouched[p] = down;
        }
        if (panel != P_NONE) joyPointer = -1;

        float mx = 0, my = 0;
        if (panel == P_NONE) {
            if (joyPointer >= 0) {
                float dx = joyX - joyBaseX, dy = joyY - joyBaseY;
                float len = (float) Math.sqrt(dx * dx + dy * dy), max = 75f;
                if (len > max) { joyBaseX = joyX - dx / len * max; joyBaseY = joyY - dy / len * max; dx = joyX - joyBaseX; dy = joyY - joyBaseY; }
                if (len > 8f) { mx = dx / max; my = dy / max; }
            }
            if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) mx -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) mx += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) my += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) my -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.SPACE) || Gdx.input.isKeyPressed(Input.Keys.J)) attackHeld = true;
            if (Gdx.input.isKeyJustPressed(Input.Keys.K)) client.send("SKILL");
            if (Gdx.input.isKeyJustPressed(Input.Keys.Q) || Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) client.send("QUSE", "hp");
            if (Gdx.input.isKeyJustPressed(Input.Keys.E) || Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) client.send("QUSE", "mp");
            if (Gdx.input.isKeyJustPressed(Input.Keys.I) || Gdx.input.isKeyJustPressed(Input.Keys.TAB)) openPanel(P_BAG);
            if (Gdx.input.isKeyJustPressed(Input.Keys.F) && nearShop() != null) openPanel(nearShop().type);
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.T)) askChat();
            if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) tryQuit();
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                || Gdx.input.isKeyJustPressed(Input.Keys.I) || Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            panel = P_NONE;
        }
        state.moveMe(mx, my, dt, cls);

        // si on s'éloigne de la boutique, on ferme sa fenêtre
        if (panel >= 0 && panel <= Items.SHOP_BANK) {
            WorldMap.Building b = nearShop();
            if (b == null || b.type != panel) panel = P_NONE;
        }

        posT -= dt;
        boolean changed = Math.abs(state.myX - lastSentX) > 0.01f || Math.abs(state.myY - lastSentY) > 0.01f
                || state.myMoving != lastSentMoving || state.myDir != lastSentDir;
        if (posT <= 0 && (changed || posT < -0.5f)) {
            posT = 0.05f;
            lastSentX = state.myX; lastSentY = state.myY; lastSentMoving = state.myMoving; lastSentDir = state.myDir;
            client.send("POS", Protocol.f(state.myX), Protocol.f(state.myY), state.myDir, state.myMoving ? 1 : 0);
        }

        atkT -= dt;
        if (attackHeld && atkT <= 0) { atkT = 0.12f; client.send("ATK"); }

        boolean safe = WorldMap.inSafeZone(state.myX, state.myY, 0);
        if (safe != wasSafe) {
            showToast(safe ? "Village d'Aube — zone sûre, aucun monstre n'entre ici" : "Tu quittes le village : prudence !");
            wasSafe = safe;
        }
    }

    private void showToast(String s) { toast = s; toastT = 3f; }

    private void onTap(int pointer, float ux, float uy) {
        if (inCircle(ux, uy, atkX(), ATK_Y, ATK_R * 1.15f)) return;
        if (inCircle(ux, uy, skX(), SK_Y, SK_R * 1.15f)) { client.send("SKILL"); return; }
        if (inCircle(ux, uy, hpX(), HP_Y, HP_R * 1.15f)) { client.send("QUSE", "hp"); return; }
        if (inCircle(ux, uy, mpX(), MP_Y, MP_R * 1.15f)) { client.send("QUSE", "mp"); return; }
        if (inCircle(ux, uy, bagX(), BAG_Y, BAG_R * 1.2f)) { openPanel(P_BAG); return; }
        if (inCircle(ux, uy, chatX(), CHAT_Y, CHAT_R * 1.25f)) { askChat(); return; }
        if (Ui.inside(ux, uy, quitX(), QUIT_Y, QUIT_W, QUIT_H)) { tryQuit(); return; }
        WorldMap.Building shop = nearShop();
        if (shop != null && Ui.inside(ux, uy, ui.w / 2f - ENTER_W / 2f, ENTER_Y, ENTER_W, ENTER_H)) { openPanel(shop.type); return; }
        if (ux < ui.w * 0.5f && joyPointer < 0) {
            joyPointer = pointer;
            joyBaseX = joyX = ux;
            joyBaseY = joyY = uy;
        }
    }

    private void openPanel(int p) {
        panel = p;
        selInv = -1; selEquip = -1; shopPage = 0;
    }

    private void tryQuit() {
        if (quitArmed > 0) leave(null);
        else quitArmed = 2.5f;
    }

    private void askChat() {
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override public void input(String text) {
                final String t = Protocol.cleanText(text, 120);
                Gdx.app.postRunnable(() -> { if (!t.isEmpty()) client.send("CHAT", t); });
            }
            @Override public void canceled() {}
        }, "Message", "", "Écris à ton groupe…");
    }

    // ================================================================ sauvegarde / sortie

    private void persist(float dt) {
        saveT -= dt;
        flushT -= dt;
        if (saveT <= 0) { saveT = 1f; storeAll(); }
        if (flushT <= 0) { flushT = 10f; game.save.flush(); }
    }

    private void storeAll() {
        if (state.statsReceived) game.save.store(cls, state.level, state.xp, state.gold);
        if (state.invReceived) game.save.storeItems(cls, state.inv.encode(), state.bank.encode(), state.weapon, state.armor);
    }

    private void saveNow() { storeAll(); game.save.flush(); }

    private boolean left;

    private void leave(String error) {
        if (left) return;
        left = true;
        saveNow();
        client.close();
        if (server != null) server.stop();
        String msg = error;
        if (state.kickReason != null) msg = state.kickReason;
        if (server != null && error != null && error.startsWith("Connexion perdue")) msg = null;
        game.setScreen(new MenuScreen(game, msg));
        dispose();
    }

    @Override public void pause() { saveNow(); }

    // ================================================================ monde

    private void buildMinimap() {
        Pixmap pm = new Pixmap(WorldMap.SIZE, WorldMap.SIZE, Pixmap.Format.RGBA8888);
        for (int y = 0; y < WorldMap.SIZE; y++)
            for (int x = 0; x < WorldMap.SIZE; x++)
                pm.drawPixel(x, WorldMap.SIZE - 1 - y, (Art.miniColor(state.map.get(x, y), state.map.obj(x, y)) << 8) | 0xff);
        for (WorldMap.Building b : state.map.buildings) {
            pm.setColor(b.type == WorldMap.B_FOUNTAIN ? 0x8fd0ffff : b.type == WorldMap.B_STALL ? 0xd04040ff : 0xc8553aff);
            pm.fillRectangle(b.x, WorldMap.SIZE - b.y - b.h, b.w, b.h);
        }
        minimap = new Texture(pm);
        pm.dispose();
    }

    private void renderWorld() {
        int sw = Gdx.graphics.getWidth(), sh = Math.max(1, Gdx.graphics.getHeight());
        float vh = VIEW_TILES, vw = VIEW_TILES * sw / (float) sh;
        cam.setToOrtho(false, vw, vh);
        float cx = Math.max(vw / 2f, Math.min(WorldMap.SIZE - vw / 2f, state.myX));
        float cy = Math.max(vh / 2f, Math.min(WorldMap.SIZE - vh / 2f, state.myY + 0.5f));
        float ppu = sh / vh; // aligne la caméra sur la grille de pixels (rendu net, sans couture)
        cam.position.set(Math.round(cx * ppu) / ppu, Math.round(cy * ppu) / ppu, 0);
        cam.update();
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        world.render(batch, cam, state, cls, look, time);
        batch.end();
    }

    // ================================================================ interface

    private float toUiX(float wx) { return (wx - cam.position.x) / cam.viewportWidth * ui.w + ui.w / 2f; }
    private float toUiY(float wy) { return (wy - cam.position.y) / cam.viewportHeight * Ui.H + Ui.H / 2f; }

    private boolean tapped(float x, float y, float w, float h) {
        if (Ui.inside(tapX, tapY, x, y, w, h)) { tapX = tapY = -1; return true; }
        return false;
    }

    private void renderHud(float dt) {
        ui.begin();
        float W = ui.w, H = Ui.H;

        // noms
        for (ClientState.Avatar a : state.players.values()) {
            boolean me = a.id == state.myId;
            float x = me ? state.myX : a.x, y = me ? state.myY : a.y;
            float ux = toUiX(x), uy = toUiY(y + 1.25f);
            ui.textCentered(ui.small, a.name + "  Nv " + a.level, ux, uy, me ? GOLD : Color.WHITE);
            if (!me) ui.bar(ux - 35, uy - 26, 70, 6, a.hp / (float) Math.max(1, a.maxHp), 0.3f, 0.9f, 0.35f);
        }
        for (WorldMap.Building b : state.map.buildings) {
            if (!b.isShop()) continue;
            float kx = b.doorX() + 1.5f, ky = b.doorY() + 0.15f;
            float ux = toUiX(kx), uy = toUiY(ky + 1.3f);
            if (ux < -100 || ux > W + 100 || uy < -50 || uy > H + 50) continue;
            ui.textCentered(ui.small, Items.KEEPER_NAMES[b.type] + " • " + Items.SHOP_NAMES[b.type], ux, uy, new Color(0.7f, 0.95f, 1f, 1f));
        }
        for (ClientState.Mob m : state.mobs.values())
            if (m.type == Rules.GOLEM) ui.textCentered(ui.small, Rules.MOB_NAMES[m.type], toUiX(m.x), toUiY(m.y + 2.7f), new Color(1f, 0.6f, 0.4f, 1f));
        for (ClientState.FloatText t : state.texts) {
            Color c = new Color(TXT_COLORS[Math.max(0, Math.min(TXT_COLORS.length - 1, t.color))]);
            c.a = 1f - Math.max(0, t.t - 0.5f) * 2f;
            ui.textCentered(t.color == Protocol.TXT_CRIT ? ui.font : ui.small, t.text, toUiX(t.x), toUiY(t.y + t.t * 0.9f), c);
        }

        drawPlayerPanel(W, H);
        drawChat(H);

        if (server != null && lan) {
            String ips = localIps.isEmpty() ? "?" : String.join(" / ", localIps);
            String s = "Partie hébergée • IP : " + ips + " • " + server.getPlayerCount() + " joueur(s)";
            float tw = ui.textWidth(ui.small, s) + 30;
            ui.rect(W / 2f - tw / 2f, H - 44, tw, 34, 0, 0, 0, 0.55f);
            ui.textCentered(ui.small, s, W / 2f, H - 27, new Color(0.7f, 1f, 0.75f, 1f));
        }
        toastT -= dt;
        if (toast != null && toastT > 0) {
            float a = Math.min(1f, toastT);
            float tw = ui.textWidth(ui.font, toast) + 40;
            ui.rect(W / 2f - tw / 2f, H - 120, tw, 46, 0.1f, 0.08f, 0.14f, 0.8f * a);
            ui.textCentered(ui.font, toast, W / 2f, H - 97, new Color(1f, 0.9f, 0.6f, a));
        } else if (nearFountain()) {
            String hint = "Fontaine : soigne rapidement les PV et les PM";
            float tw = ui.textWidth(ui.small, hint) + 30;
            ui.rect(W / 2f - tw / 2f, 112, tw, 32, 0, 0, 0, 0.5f);
            ui.textCentered(ui.small, hint, W / 2f, 128, new Color(0.6f, 0.9f, 1f, 1f));
        }

        drawMinimap(W, H);
        ui.button(quitArmed > 0 ? "Sûr ?" : "Quitter", quitX(), QUIT_Y, QUIT_W, QUIT_H, quitArmed > 0);
        if (quitArmed > 0) ui.textCentered(ui.small, "Touche encore pour quitter (progression sauvegardée)", W / 2f, H - 150, Color.WHITE);

        if (panel == P_NONE) drawControls(W);
        else drawPanel(W, H);
        ui.end();
    }

    private void drawPlayerPanel(float W, float H) {
        float px = 14, pw = 440, ph = 150, py = H - ph - 14;
        ui.panel(px, py, pw, ph, 0.85f);
        // portrait
        ui.rect(px + 10, py + 12, 110, 126, 0.25f, 0.2f, 0.3f, 1f);
        ui.rect(px + 12, py + 14, 106, 122, 0.45f, 0.62f, 0.85f, 1f);
        ui.rect(px + 12, py + 14, 106, 40, 0.42f, 0.66f, 0.3f, 1f);
        CharacterDrawer.draw(ui.batch, art, look, cls, Items.tier(state.armor), Items.tier(state.weapon), CharacterDrawer.DIR_DOWN, 1, px + 15, py + 8, 100, 125);
        float tx = px + 134;
        ui.text(ui.font, game.save.name() + "  Nv " + state.level, tx, py + ph - 12, Color.WHITE, Align.left);
        ui.text(ui.small, Rules.CLASS_NAMES[cls], tx, py + ph - 44, SOFT, Align.left);
        float bw = pw - 150;
        ui.bar(tx, py + 70, bw, 18, state.hp / (float) Math.max(1, state.maxHp), 0.86f, 0.24f, 0.24f);
        ui.textCentered(ui.small, "PV " + state.hp + " / " + state.maxHp, tx + bw / 2f, py + 79, Color.WHITE);
        ui.bar(tx, py + 44, bw, 16, state.mp / (float) Math.max(1, state.maxMp), 0.28f, 0.48f, 0.95f);
        ui.textCentered(ui.small, "PM " + state.mp + " / " + state.maxMp, tx + bw / 2f, py + 52, Color.WHITE);
        ui.bar(tx, py + 28, bw, 6, state.xp / (float) Math.max(1, state.xpNext), 0.95f, 0.78f, 0.3f);
        ui.batch.draw(art.coin, tx, py + 2, 22, 22);
        ui.text(ui.small, state.gold + "      ATQ " + state.atk + "   DÉF " + state.def, tx + 28, py + 21, GOLD, Align.left);
        if (state.buffT > 0) {
            ui.panel(px, py - 44, 210, 36, 0.8f);
            ui.batch.draw(art.icon(Items.STEW), px + 4, py - 42, 32, 32);
            ui.text(ui.small, "Repas : " + (int) Math.ceil(state.buffT) + " s", px + 42, py - 16, new Color(0.6f, 1f, 0.6f, 1f), Align.left);
        }
    }

    private void drawChat(float H) {
        float cy = H - 230;
        int start = Math.max(0, state.chat.size() - 6);
        for (int i = start; i < state.chat.size(); i++) {
            ClientState.ChatLine l = state.chat.get(i);
            if (l.age > 18f) continue;
            float a = Math.min(1f, (18f - l.age) / 3f);
            String s = l.name.isEmpty() ? "★ " + l.text : l.name + " : " + l.text;
            Color c = l.name.isEmpty() ? new Color(1f, 0.85f, 0.45f, a) : new Color(1f, 1f, 1f, a);
            ui.text(ui.small, s, 22, cy, c, Align.left);
            cy -= 27;
        }
    }

    private void drawMinimap(float W, float H) {
        float ms = 150, mx = W - ms - 16, my = H - ms - 76;
        ui.rect(mx - 3, my - 3, ms + 6, ms + 6, 0, 0, 0, 0.8f);
        ui.batch.draw(minimap, mx, my, ms, ms);
        float k = ms / WorldMap.SIZE;
        float vh = WorldMap.VILLAGE_HALF + 0.5f;
        ui.rect(mx + (WorldMap.VILLAGE_X - vh) * k, my + (WorldMap.VILLAGE_Y - vh) * k, vh * 2 * k, 1.5f, 1, 1, 1, 0.5f);
        for (ClientState.Mob m : state.mobs.values())
            if (m.type == Rules.GOLEM) ui.rect(mx + m.x * k - 3, my + m.y * k - 3, 6, 6, 1f, 0.3f, 0.2f, 1f);
        for (ClientState.Avatar a : state.players.values())
            if (a.id != state.myId) ui.rect(mx + a.x * k - 3, my + a.y * k - 3, 6, 6, 0.4f, 0.8f, 1f, 1f);
        ui.rect(mx + state.myX * k - 3, my + state.myY * k - 3, 6, 6, 1f, 0.9f, 0.2f, 1f);
    }

    private void drawControls(float W) {
        if (joyPointer >= 0) {
            ui.ringShape(joyBaseX, joyBaseY, 90, 1, 1, 1, 0.35f);
            ui.circle(joyX, joyY, 42, 1, 1, 1, 0.45f);
        } else {
            ui.ringShape(150, 150, 90, 1, 1, 1, 0.12f);
            ui.textCentered(ui.small, "Glisse ici pour bouger", 150, 150, new Color(1, 1, 1, 0.35f));
        }
        roundButton(atkX(), ATK_Y, ATK_R, attackHeld ? 0.95f : 0.78f, 0.34f, 0.24f, "ATTAQUE", ui.font, null, 0);
        int cost = Rules.SKILL_COST[cls];
        boolean ready = state.skillCd <= 0 && state.mp >= cost;
        roundButton(skX(), SK_Y, SK_R, ready ? 0.62f : 0.35f, ready ? 0.38f : 0.32f, ready ? 0.85f : 0.45f, null, ui.small, art.skillIcon, 0);
        if (state.skillCd > 0) {
            float frac = state.skillCd / Rules.SKILL_COOLDOWN[cls];
            ui.circle(skX(), SK_Y, SK_R * frac, 0, 0, 0, 0.45f);
            ui.textCentered(ui.font, String.valueOf((int) Math.ceil(state.skillCd)), skX(), SK_Y, Color.WHITE);
        }
        ui.textCentered(ui.small, cost + " PM", skX(), SK_Y - SK_R * 0.62f, new Color(0.8f, 0.88f, 1f, 1f));
        roundButton(hpX(), HP_Y, HP_R, 0.75f, 0.25f, 0.28f, null, ui.small, art.icon(Items.SMALL_HP), countType(Items.T_HP));
        roundButton(mpX(), MP_Y, MP_R, 0.28f, 0.38f, 0.8f, null, ui.small, art.icon(Items.SMALL_MP), countType(Items.T_MP));
        roundButton(bagX(), BAG_Y, BAG_R, 0.55f, 0.4f, 0.25f, null, ui.small, art.bag, 0);
        roundButton(chatX(), CHAT_Y, CHAT_R, 0.35f, 0.4f, 0.7f, null, ui.small, art.chatIcon, 0);

        WorldMap.Building shop = nearShop();
        if (shop != null) {
            float ex = W / 2f - ENTER_W / 2f;
            float pulse = 0.85f + 0.15f * (float) Math.sin(time * 5);
            ui.rect(ex - 4, ENTER_Y - 4, ENTER_W + 8, ENTER_H + 8, 1f, 0.82f, 0.4f, pulse);
            ui.button("Entrer : " + Items.SHOP_NAMES[shop.type], ex, ENTER_Y, ENTER_W, ENTER_H, true);
        }
    }

    private int countType(int type) {
        int n = 0;
        for (int i = 0; i < state.inv.size(); i++) {
            Items.Def d = Items.get(state.inv.ids[i]);
            if (d != null && d.type == type) n += state.inv.counts[i];
        }
        return n;
    }

    private void roundButton(float x, float y, float r, float red, float g, float b, String label, BitmapFont f, TextureRegion icon, int count) {
        ui.circle(x, y - 4, r, 0, 0, 0, 0.45f);
        ui.circle(x, y, r, red, g, b, 0.88f);
        ui.ringShape(x, y, r, 1, 1, 1, 0.35f);
        if (icon != null) ui.batch.draw(icon, x - r * 0.6f, y - r * 0.6f, r * 1.2f, r * 1.2f);
        if (label != null) ui.textCentered(f, label, x, y, Color.WHITE);
        if (count > 0) ui.text(ui.small, String.valueOf(count), x + r * 0.75f, y - r * 0.45f, Color.WHITE, Align.right);
    }

    // ================================================================ fenêtres (sac, boutiques, dépôt)

    private void drawPanel(float W, float H) {
        ui.rect(0, 0, W, H, 0, 0, 0, 0.55f);
        float pw = Math.min(1180, W - 30), ph = 640, px = (W - pw) / 2f, py = (H - ph) / 2f;
        ui.panel(px, py, pw, ph, 0.97f);
        String title = panel == P_BAG ? "Sac & équipement" : Items.SHOP_NAMES[panel] + " • " + Items.KEEPER_NAMES[panel];
        ui.text(ui.font, title, px + 24, py + ph - 18, GOLD, Align.left);
        ui.batch.draw(art.coin, px + pw - 270, py + ph - 48, 26, 26);
        ui.text(ui.font, String.valueOf(state.gold), px + pw - 238, py + ph - 20, GOLD, Align.left);
        float cx = px + pw - 70, cyy = py + ph - 64;
        ui.button("×", cx, cyy, 56, 52, false);
        boolean close = tapped(cx, cyy, 56, 52);
        if (panel != P_BAG) ui.text(ui.small, Items.SHOP_GREETING[panel], px + 24, py + ph - 58, SOFT, Align.left);

        if (panel == P_BAG) drawBag(px, py, pw, ph);
        else if (panel == Items.SHOP_BANK) drawBank(px, py, pw, ph);
        else drawShop(px, py, pw, ph);

        if (close || (tapX >= 0 && !Ui.inside(tapX, tapY, px, py, pw, ph))) panel = P_NONE;
    }

    /** Grille d'inventaire ; renvoie la case touchée ou -1. */
    private int grid(Inventory inv, float gx, float gy, int cols, int rows, float cell, int selected) {
        int hit = -1;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                if (i >= inv.size()) break;
                float x = gx + c * cell, y = gy + (rows - 1 - r) * cell;
                boolean sel = i == selected;
                ui.rect(x + 2, y + 2, cell - 4, cell - 4, sel ? 0.95f : 0.08f, sel ? 0.75f : 0.07f, sel ? 0.35f : 0.1f, 1f);
                ui.rect(x + 5, y + 5, cell - 10, cell - 10, 0.22f, 0.19f, 0.28f, 1f);
                if (inv.ids[i] != 0) {
                    itemIcon(inv.ids[i], x + 8, y + 8, cell - 16);
                    if (inv.counts[i] > 1) ui.text(ui.small, String.valueOf(inv.counts[i]), x + cell - 7, y + 26, Color.WHITE, Align.right);
                }
                if (tapped(x, y, cell, cell)) hit = i;
            }
        return hit;
    }

    private void itemIcon(int id, float x, float y, float size) {
        int t = Art.iconTint(id);
        ui.batch.setColor(((t >> 16) & 255) / 255f, ((t >> 8) & 255) / 255f, (t & 255) / 255f, 1f);
        ui.batch.draw(art.icon(id), x, y, size, size);
        ui.batch.setColor(Color.WHITE);
    }

    private void itemDetails(int id, float x, float y, float w) {
        Items.Def d = Items.get(id);
        if (d == null) return;
        ui.text(ui.font, d.name, x, y, Color.WHITE, Align.left);
        ui.text(ui.small, d.describe(), x, y - 34, SOFT, Align.left);
        String req = "";
        if (d.cls >= 0) req = "Pour : " + Rules.CLASS_NAMES[d.cls] + "   ";
        if (d.level > 1) req += "Niveau " + d.level + " requis";
        if (!req.isEmpty()) ui.text(ui.small, req, x, y - 62, d.level > state.level || (d.cls >= 0 && d.cls != cls) ? BAD : SOFT, Align.left);
    }

    private void drawBag(float px, float py, float pw, float ph) {
        // équipement
        float ex = px + 30, ey = py + ph - 250, cell = 96;
        int[] eq = {state.weapon, state.armor};
        String[] lbl = {"Arme", "Armure"};
        for (int k = 0; k < 2; k++) {
            float x = ex, y = ey - k * (cell + 40);
            boolean sel = selEquip == k;
            ui.rect(x, y, cell, cell, sel ? 0.95f : 0.1f, sel ? 0.75f : 0.09f, sel ? 0.35f : 0.12f, 1f);
            ui.rect(x + 4, y + 4, cell - 8, cell - 8, 0.24f, 0.21f, 0.3f, 1f);
            if (eq[k] != 0) itemIcon(eq[k], x + 10, y + 10, cell - 20);
            ui.text(ui.small, lbl[k], x + cell + 14, y + cell - 10, SOFT, Align.left);
            Items.Def d = Items.get(eq[k]);
            ui.text(ui.small, d == null ? "(vide)" : d.name, x + cell + 14, y + cell - 40, Color.WHITE, Align.left);
            if (tapped(x, y, cell, cell)) { selEquip = eq[k] != 0 ? k : -1; selInv = -1; }
        }
        ui.text(ui.small, "ATQ " + state.atk + "   DÉF " + state.def, ex, ey - 2 * (cell + 40) + 120, GOLD, Align.left);
        ui.text(ui.small, "PV " + state.maxHp + "   PM " + state.maxMp, ex, ey - 2 * (cell + 40) + 92, GOLD, Align.left);

        // sac
        float gcell = 76, gx = px + pw - 6 * gcell - 30, gy = py + 150;
        int hit = grid(state.inv, gx, gy, 6, 4, gcell, selInv);
        if (hit >= 0) { selInv = state.inv.ids[hit] != 0 ? hit : -1; selEquip = -1; }

        // détails + actions
        float dy = py + 112;
        if (selInv >= 0 && state.inv.valid(selInv)) {
            Items.Def d = Items.get(state.inv.ids[selInv]);
            itemDetails(d.id, px + 30, dy + 10, pw);
            String action = d.type == Items.T_WEAPON || d.type == Items.T_ARMOR ? "Équiper" : d.type == Items.T_FOOD ? "Manger" : "Boire";
            float bw = 220, bxx = px + pw - bw - 30, byy = py + 24;
            ui.button(action, bxx, byy, bw, 64, true);
            if (tapped(bxx, byy, bw, 64)) { client.send("USE", selInv); if (d.stackable() && state.inv.counts[selInv] > 1) { /* garde la sélection */ } else selInv = -1; }
        } else if (selEquip >= 0) {
            int id = selEquip == 0 ? state.weapon : state.armor;
            if (id != 0) {
                itemDetails(id, px + 30, dy + 10, pw);
                float bw = 220, bxx = px + pw - bw - 30, byy = py + 24;
                ui.button("Retirer", bxx, byy, bw, 64, false);
                if (tapped(bxx, byy, bw, 64)) { client.send("UNEQ", selEquip == 0 ? "w" : "a"); selEquip = -1; }
            } else selEquip = -1;
        } else {
            ui.text(ui.small, "Touche un objet pour le voir, l'utiliser ou l'équiper.", px + 30, dy, SOFT, Align.left);
        }
    }

    private void drawShop(float px, float py, float pw, float ph) {
        List<Items.Def> cat = Items.catalog(panel, cls);
        int perPage = 6, pages = (cat.size() + perPage - 1) / perPage;
        shopPage = Math.max(0, Math.min(shopPage, pages - 1));
        float lx = px + 22, lw = pw * 0.56f, rowH = 72, top = py + ph - 100;
        for (int i = 0; i < perPage; i++) {
            int idx = shopPage * perPage + i;
            if (idx >= cat.size()) break;
            Items.Def d = cat.get(idx);
            float y = top - (i + 1) * rowH;
            boolean canLevel = state.level >= d.level, canPay = state.gold >= d.price;
            ui.rect(lx, y + 3, lw, rowH - 6, 0.17f, 0.15f, 0.22f, 1f);
            itemIcon(d.id, lx + 8, y + 10, rowH - 20);
            ui.text(ui.small, d.name, lx + rowH + 4, y + rowH - 12, canLevel ? Color.WHITE : BAD, Align.left);
            ui.text(ui.small, d.describe() + (d.level > 1 ? "  •  Nv " + d.level : ""), lx + rowH + 4, y + rowH - 40, canLevel ? SOFT : BAD, Align.left);
            float bw = 150, bxx = lx + lw - bw - 8, byy = y + 12;
            ui.button(d.price + " or", bxx, byy, bw, rowH - 24, canLevel && canPay);
            if (tapped(bxx, byy, bw, rowH - 24)) client.send("BUY", d.id);
        }
        if (pages > 1) {
            float by = py + 22;
            ui.button("‹", lx, by, 70, 54, false);
            ui.button("›", lx + lw - 70, by, 70, 54, false);
            ui.textCentered(ui.small, "Page " + (shopPage + 1) + " / " + pages, lx + lw / 2f, by + 27, SOFT);
            if (tapped(lx, by, 70, 54)) shopPage = (shopPage + pages - 1) % pages;
            if (tapped(lx + lw - 70, by, 70, 54)) shopPage = (shopPage + 1) % pages;
        }
        // vente
        float gcell = 64, gx = px + pw - 6 * gcell - 24, gy = py + 210;
        ui.text(ui.small, "Ton sac (touche pour vendre)", gx, gy + 4 * gcell + 30, SOFT, Align.left);
        int hit = grid(state.inv, gx, gy, 6, 4, gcell, selInv);
        if (hit >= 0) selInv = state.inv.ids[hit] != 0 ? hit : -1;
        if (selInv >= 0 && state.inv.valid(selInv)) {
            Items.Def d = Items.get(state.inv.ids[selInv]);
            ui.text(ui.small, d.name, gx, gy - 14, Color.WHITE, Align.left);
            float bw = 6 * gcell, byy = py + 40;
            ui.button("Vendre : +" + d.sellPrice() + " or", gx, byy, bw, 64, true);
            if (tapped(gx, byy, bw, 64)) { client.send("SELL", selInv); if (state.inv.counts[selInv] <= 1) selInv = -1; }
        }
    }

    private void drawBank(float px, float py, float pw, float ph) {
        float c1 = 66, gx1 = px + 26, gy = py + 120;
        ui.text(ui.small, "Sac — touche pour déposer", gx1, gy + 4 * c1 + 30, SOFT, Align.left);
        int h1 = grid(state.inv, gx1, gy, 6, 4, c1, -1);
        if (h1 >= 0 && state.inv.ids[h1] != 0) client.send("DEP", h1);

        float c2 = 58, gx2 = px + pw - 8 * c2 - 26, gy2 = py + 80;
        ui.text(ui.small, "Dépôt (48 places) — touche pour reprendre", gx2, gy2 + 6 * c2 + 30, SOFT, Align.left);
        int h2 = grid(state.bank, gx2, gy2, 8, 6, c2, -1);
        if (h2 >= 0 && state.bank.ids[h2] != 0) client.send("WDR", h2);
        ui.text(ui.small, "Le dépôt est conservé avec ta sauvegarde, même hors du village.", px + 26, py + 50, SOFT, Align.left);
    }

    @Override
    public void dispose() {
        if (minimap != null) { minimap.dispose(); minimap = null; }
    }
}
