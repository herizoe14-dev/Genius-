package com.genius.echos.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.genius.echos.EchosGame;
import com.genius.echos.gfx.Art;
import com.genius.echos.net.ClientState;
import com.genius.echos.net.GameClient;
import com.genius.echos.net.GameServer;
import com.genius.echos.net.LanDiscovery;
import com.genius.echos.net.Protocol;
import com.genius.echos.ui.Ui;
import com.genius.echos.world.Rules;
import com.genius.echos.world.WorldMap;

import java.util.ArrayList;
import java.util.List;

/** Écran de jeu : monde, personnages, commandes tactiles et interface. */
public class GameScreen extends ScreenAdapter {
    private static final float VIEW_TILES = 11f;
    private static final Color GOLD = new Color(1f, 0.82f, 0.4f, 1f);
    private static final Color[] TXT_COLORS = {
            Color.WHITE, new Color(1f, 0.35f, 0.3f, 1f), new Color(0.4f, 1f, 0.45f, 1f),
            new Color(1f, 0.85f, 0.3f, 1f), new Color(1f, 0.6f, 0.15f, 1f), new Color(0.5f, 0.8f, 1f, 1f)};

    private final EchosGame game;
    private final Ui ui;
    private final Art art;
    private final GameClient client;
    private final GameServer server;
    private final boolean lan;
    private final int cls;
    private final ClientState state = new ClientState();
    private final OrthographicCamera cam = new OrthographicCamera();
    private final SpriteBatch batch;
    private Texture minimap;
    private final List<String> localIps;

    private float time, posT, atkT, saveT, flushT, quitArmed;
    private float lastSentX = -1, lastSentY = -1;
    private boolean lastSentMoving;

    // commandes tactiles
    private final boolean[] wasTouched = new boolean[5];
    private int joyPointer = -1;
    private float joyBaseX, joyBaseY, joyX, joyY;
    private boolean attackHeld;

    public GameScreen(EchosGame game, GameClient client, GameServer server, boolean lan) {
        this.game = game;
        this.ui = game.ui;
        this.art = game.art;
        this.client = client;
        this.server = server;
        this.lan = lan;
        this.cls = game.save.cls();
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

        handleInput(dt);
        if (left) return;
        state.update(dt);
        persist(dt);

        renderWorld();
        renderHud();
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

    // ================================================================ entrées

    private float btnAttackX() { return ui.w - 125; }
    private static final float BTN_ATTACK_Y = 135, BTN_ATTACK_R = 82;
    private float btnPotX() { return ui.w - 275; }
    private static final float BTN_POT_Y = 78, BTN_POT_R = 52;
    private float btnBuyX() { return ui.w - 275; }
    private static final float BTN_BUY_Y = 205, BTN_BUY_R = 46;
    private float btnChatX() { return ui.w - 95; }
    private static final float BTN_CHAT_Y = 290, BTN_CHAT_R = 40;
    private float btnQuitX() { return ui.w - 130; }
    private static final float BTN_QUIT_Y = Ui.H - 62, BTN_QUIT_W = 115, BTN_QUIT_H = 48;

    private static boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private boolean nearFountain() {
        float dx = state.myX - (WorldMap.VILLAGE_X + 0.5f), dy = state.myY - (WorldMap.VILLAGE_Y + 0.5f);
        return dx * dx + dy * dy < 3.0f * 3.0f;
    }

    private void handleInput(float dt) {
        attackHeld = false;
        quitArmed = Math.max(0, quitArmed - dt);
        for (int p = 0; p < wasTouched.length; p++) {
            boolean down = Gdx.input.isTouched(p);
            if (down) {
                float ux = ui.uiX(Gdx.input.getX(p), Gdx.input.getY(p));
                float uy = ui.uiY(Gdx.input.getX(p), Gdx.input.getY(p));
                if (p == joyPointer) {
                    joyX = ux; joyY = uy;
                } else {
                    if (inCircle(ux, uy, btnAttackX(), BTN_ATTACK_Y, BTN_ATTACK_R * 1.15f)) attackHeld = true;
                    if (!wasTouched[p]) onTap(p, ux, uy);
                }
            } else if (p == joyPointer) {
                joyPointer = -1;
            }
            wasTouched[p] = down;
        }

        // Déplacement : joystick + clavier
        float mx = 0, my = 0;
        if (joyPointer >= 0) {
            float dx = joyX - joyBaseX, dy = joyY - joyBaseY;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float max = 75f;
            if (len > max) { joyBaseX = joyX - dx / len * max; joyBaseY = joyY - dy / len * max; dx = joyX - joyBaseX; dy = joyY - joyBaseY; len = max; }
            if (len > 8f) { mx = dx / max; my = dy / max; }
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) mx -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) mx += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) my += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) my -= 1;
        state.moveMe(mx, my, dt, cls);

        posT -= dt;
        boolean changed = Math.abs(state.myX - lastSentX) > 0.01f || Math.abs(state.myY - lastSentY) > 0.01f || state.myMoving != lastSentMoving;
        if (posT <= 0 && (changed || posT < -0.5f)) {
            posT = 0.05f;
            lastSentX = state.myX; lastSentY = state.myY; lastSentMoving = state.myMoving;
            client.send("POS", Protocol.f(state.myX), Protocol.f(state.myY), state.myFacing, state.myMoving ? 1 : 0);
        }

        if (Gdx.input.isKeyPressed(Input.Keys.SPACE) || Gdx.input.isKeyPressed(Input.Keys.J)) attackHeld = true;
        atkT -= dt;
        if (attackHeld && atkT <= 0) {
            atkT = 0.12f;
            client.send("ATK");
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q) || Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) client.send("POT");
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) client.send("BUY");
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.T)) askChat();
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) tryQuit();
    }

    private void onTap(int pointer, float ux, float uy) {
        if (inCircle(ux, uy, btnAttackX(), BTN_ATTACK_Y, BTN_ATTACK_R * 1.15f)) return;
        if (inCircle(ux, uy, btnPotX(), BTN_POT_Y, BTN_POT_R * 1.15f)) { client.send("POT"); return; }
        if (nearFountain() && inCircle(ux, uy, btnBuyX(), BTN_BUY_Y, BTN_BUY_R * 1.15f)) { client.send("BUY"); return; }
        if (inCircle(ux, uy, btnChatX(), BTN_CHAT_Y, BTN_CHAT_R * 1.2f)) { askChat(); return; }
        if (Ui.inside(ux, uy, btnQuitX(), BTN_QUIT_Y, BTN_QUIT_W, BTN_QUIT_H)) { tryQuit(); return; }
        if (ux < ui.w * 0.55f && joyPointer < 0) {
            joyPointer = pointer;
            joyBaseX = joyX = ux;
            joyBaseY = joyY = uy;
        }
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
        if (!state.statsReceived) return;
        saveT -= dt;
        flushT -= dt;
        if (saveT <= 0) { saveT = 1f; game.save.store(cls, state.level, state.xp, state.gold, state.potions); }
        if (flushT <= 0) { flushT = 10f; game.save.flush(); }
    }

    private void saveNow() {
        if (state.statsReceived) {
            game.save.store(cls, state.level, state.xp, state.gold, state.potions);
            game.save.flush();
        }
    }

    private boolean left;

    private void leave(String error) {
        if (left) return;
        left = true;
        saveNow();
        client.close();
        if (server != null) server.stop();
        String msg = error;
        if (state.kickReason != null) msg = state.kickReason;
        if (server != null && error != null && error.startsWith("Connexion perdue")) msg = null; // on a quitté soi-même
        game.setScreen(new MenuScreen(game, msg));
        dispose();
    }

    @Override public void pause() { saveNow(); }

    // ================================================================ monde

    private void buildMinimap() {
        Pixmap pm = new Pixmap(WorldMap.SIZE, WorldMap.SIZE, Pixmap.Format.RGBA8888);
        for (int y = 0; y < WorldMap.SIZE; y++)
            for (int x = 0; x < WorldMap.SIZE; x++) {
                int c = Art.miniColor(state.map.get(x, y));
                pm.drawPixel(x, WorldMap.SIZE - 1 - y, (c << 8) | 0xff);
            }
        minimap = new Texture(pm);
        pm.dispose();
    }

    private void renderWorld() {
        float aspect = Gdx.graphics.getWidth() / (float) Math.max(1, Gdx.graphics.getHeight());
        float vh = VIEW_TILES, vw = VIEW_TILES * aspect;
        cam.setToOrtho(false, vw, vh);
        float cx = Math.max(vw / 2f, Math.min(WorldMap.SIZE - vw / 2f, state.myX));
        float cy = Math.max(vh / 2f, Math.min(WorldMap.SIZE - vh / 2f, state.myY));
        cam.position.set(cx, cy, 0);
        cam.update();
        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        WorldMap map = state.map;
        int x0 = (int) Math.floor(cx - vw / 2f) - 1, x1 = (int) Math.ceil(cx + vw / 2f) + 1;
        int y0 = (int) Math.floor(cy - vh / 2f) - 1, y1 = (int) Math.ceil(cy + vh / 2f) + 1;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                byte t = map.get(x, y);
                // les éléments posés sur l'herbe (arbres, rochers) ont leur propre fond
                batch.draw(art.tile(t, x, y, time, map), x, y, 1.001f, 1.001f);
            }

        // entités triées par profondeur (y décroissant)
        List<Object> ents = new ArrayList<>();
        ents.addAll(state.mobs.values());
        for (ClientState.Avatar a : state.players.values()) if (a.id != state.myId) ents.add(a);
        ents.add(state);
        ents.sort((a, b) -> Float.compare(entY(b), entY(a)));
        for (Object o : ents) {
            if (o instanceof ClientState.Mob) drawMob((ClientState.Mob) o);
            else if (o instanceof ClientState.Avatar) {
                ClientState.Avatar a = (ClientState.Avatar) o;
                drawHero(a.cls, a.x, a.y, a.facing, a.moving, a.anim);
            } else drawHero(cls, state.myX, state.myY, state.myFacing, state.myMoving, time);
        }

        drawFx();
        batch.end();
    }

    private float entY(Object o) {
        if (o instanceof ClientState.Mob) return ((ClientState.Mob) o).y;
        if (o instanceof ClientState.Avatar) return ((ClientState.Avatar) o).y;
        return state.myY;
    }

    private void drawHero(int c, float x, float y, int facing, boolean moving, float anim) {
        batch.setColor(Color.WHITE);
        batch.draw(art.shadow, x - 0.4f, y - 0.32f, 0.8f, 0.3f);
        TextureRegion r = art.heroes[c][moving && ((int) (anim * 8)) % 2 == 1 ? 1 : 0];
        float bob = moving ? Math.abs((float) Math.sin(anim * 12)) * 0.06f : 0;
        float w = 1.1f;
        if (facing < 0) batch.draw(r, x + w / 2f, y - 0.22f + bob, -w, w);
        else batch.draw(r, x - w / 2f, y - 0.22f + bob, w, w);
    }

    private void drawMob(ClientState.Mob m) {
        float size = m.type == Rules.GOLEM ? 2.2f : 1f;
        batch.draw(art.shadow, m.x - size * 0.4f, m.y - 0.3f, size * 0.8f, 0.3f * size);
        TextureRegion r = art.mobs[m.type][((int) (m.anim * (m.type == Rules.SLIME ? 3 : 6))) % 2];
        if (m.hurt > 0) batch.setColor(1f, 0.4f, 0.4f, 1f);
        float base = m.y - (m.type == Rules.GOLEM ? 0.3f : 0.2f);
        if (m.facing < 0) batch.draw(r, m.x + size / 2f, base, -size, size);
        else batch.draw(r, m.x - size / 2f, base, size, size);
        batch.setColor(Color.WHITE);
        if (m.hp < m.maxHp) {
            float bw = size * 0.8f, bx = m.x - bw / 2f, by = base + size + 0.05f;
            batch.setColor(0, 0, 0, 0.8f); batch.draw(art.white, bx - 0.02f, by - 0.02f, bw + 0.04f, 0.12f);
            batch.setColor(0.9f, 0.2f, 0.2f, 1f); batch.draw(art.white, bx, by, bw * m.hp / (float) m.maxHp, 0.08f);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawFx() {
        for (ClientState.Fx fx : state.fxs) {
            float k = fx.t / fx.life;
            switch (fx.kind) {
                case Protocol.FX_SLASH: {
                    float r = 0.35f + k * 0.45f;
                    batch.setColor(1f, 1f, 1f, 1f - k);
                    batch.draw(art.ring, fx.x2 - r, fx.y2 - r + 0.2f, r * 2, r * 2);
                    break;
                }
                case Protocol.FX_ARROW: {
                    float x = fx.x1 + (fx.x2 - fx.x1) * k, y = fx.y1 + (fx.y2 - fx.y1) * k + 0.3f;
                    batch.setColor(0.95f, 0.9f, 0.7f, 1f);
                    batch.draw(art.circle, x - 0.09f, y - 0.09f, 0.18f, 0.18f);
                    break;
                }
                case Protocol.FX_FIREBALL: {
                    float x = fx.x1 + (fx.x2 - fx.x1) * k, y = fx.y1 + (fx.y2 - fx.y1) * k + 0.3f;
                    batch.setColor(1f, 0.55f, 0.1f, 1f);
                    batch.draw(art.circle, x - 0.2f, y - 0.2f, 0.4f, 0.4f);
                    batch.setColor(1f, 0.95f, 0.5f, 1f);
                    batch.draw(art.circle, x - 0.1f, y - 0.1f, 0.2f, 0.2f);
                    if (k > 0.7f) {
                        float r = Rules.CLASS_SPLASH[Rules.MAGE] * (k - 0.7f) / 0.3f;
                        batch.setColor(1f, 0.5f, 0.1f, 0.6f * (1 - k) / 0.3f + 0.1f);
                        batch.draw(art.ring, fx.x2 - r, fx.y2 - r + 0.2f, r * 2, r * 2);
                    }
                    break;
                }
                case Protocol.FX_BITE: {
                    float r = 0.25f + k * 0.2f;
                    batch.setColor(1f, 0.2f, 0.2f, 1f - k);
                    batch.draw(art.ring, fx.x2 - r, fx.y2 - r + 0.2f, r * 2, r * 2);
                    break;
                }
                case Protocol.FX_HEAL: {
                    for (int i = 0; i < 4; i++) {
                        float ox = (float) Math.sin(i * 1.7f + fx.x1) * 0.35f;
                        float oy = k * 0.9f + i * 0.12f;
                        batch.setColor(0.4f, 1f, 0.5f, 1f - k);
                        batch.draw(art.circle, fx.x1 + ox - 0.06f, fx.y1 + oy, 0.12f, 0.12f);
                    }
                    break;
                }
                case Protocol.FX_LEVELUP: {
                    float r = 0.3f + k * 1.6f;
                    batch.setColor(1f, 0.85f, 0.3f, 1f - k);
                    batch.draw(art.ring, fx.x1 - r, fx.y1 - r + 0.3f, r * 2, r * 2);
                    break;
                }
                default: break;
            }
        }
        batch.setColor(Color.WHITE);
    }

    // ================================================================ interface

    private float toUiX(float wx) { return (wx - cam.position.x) / cam.viewportWidth * ui.w + ui.w / 2f; }
    private float toUiY(float wy) { return (wy - cam.position.y) / cam.viewportHeight * Ui.H + Ui.H / 2f; }

    private void renderHud() {
        ui.begin();
        float W = ui.w, H = Ui.H;

        // noms au-dessus des joueurs
        for (ClientState.Avatar a : state.players.values()) {
            float x = a.id == state.myId ? state.myX : a.x, y = a.id == state.myId ? state.myY : a.y;
            float ux = toUiX(x), uy = toUiY(y + 1.05f);
            ui.textCentered(ui.small, a.name + "  Nv " + a.level, ux, uy, a.id == state.myId ? GOLD : Color.WHITE);
            if (a.id != state.myId) ui.bar(ux - 35, uy - 26, 70, 6, a.hp / (float) Math.max(1, a.maxHp), 0.3f, 0.9f, 0.35f);
        }
        // nom du golem
        for (ClientState.Mob m : state.mobs.values())
            if (m.type == Rules.GOLEM) ui.textCentered(ui.small, Rules.MOB_NAMES[m.type], toUiX(m.x), toUiY(m.y + 2.4f), new Color(1f, 0.6f, 0.4f, 1f));

        // textes flottants
        for (ClientState.FloatText t : state.texts) {
            Color c = new Color(TXT_COLORS[Math.max(0, Math.min(TXT_COLORS.length - 1, t.color))]);
            c.a = 1f - Math.max(0, t.t - 0.5f) * 2f;
            ui.textCentered(t.color == Protocol.TXT_CRIT ? ui.font : ui.small, t.text, toUiX(t.x), toUiY(t.y + t.t * 0.9f), c);
        }

        // panneau joueur
        float px = 16, ph = 122, py = H - ph - 16, pw = 350;
        ui.panel(px, py, pw, ph, 0.82f);
        ui.text(ui.font, game.save.name() + " • " + Rules.CLASS_NAMES[cls] + " Nv " + state.level, px + 14, py + ph - 14, Color.WHITE, Align.left);
        ui.bar(px + 14, py + 56, pw - 28, 18, state.hp / (float) Math.max(1, state.maxHp), 0.85f, 0.22f, 0.22f);
        ui.textCentered(ui.small, state.hp + " / " + state.maxHp, px + pw / 2f, py + 65, Color.WHITE);
        ui.bar(px + 14, py + 38, pw - 28, 8, state.xp / (float) Math.max(1, state.xpNext), 0.35f, 0.65f, 1f);
        ui.text(ui.small, "● " + state.gold + " or      ✚ " + state.potions + " potion(s)", px + 14, py + 26, GOLD, Align.left);

        // chat
        float cy = py - 22;
        int start = Math.max(0, state.chat.size() - 6);
        for (int i = start; i < state.chat.size(); i++) {
            ClientState.ChatLine l = state.chat.get(i);
            if (l.age > 18f) continue;
            float a = Math.min(1f, (18f - l.age) / 3f);
            String s = l.name.isEmpty() ? "★ " + l.text : l.name + " : " + l.text;
            Color c = l.name.isEmpty() ? new Color(1f, 0.85f, 0.45f, a) : new Color(1f, 1f, 1f, a);
            ui.text(ui.small, s, 24, cy, c, Align.left);
            cy -= 28;
        }

        // bandeau hôte
        if (server != null && lan) {
            String ips = localIps.isEmpty() ? "?" : String.join(" / ", localIps);
            String s = "Partie hébergée • IP : " + ips + " • " + server.getPlayerCount() + " joueur(s)";
            float tw = ui.textWidth(ui.small, s) + 30;
            ui.rect(W / 2f - tw / 2f, H - 44, tw, 34, 0, 0, 0, 0.55f);
            ui.textCentered(ui.small, s, W / 2f, H - 27, new Color(0.7f, 1f, 0.75f, 1f));
        }
        if (nearFountain()) {
            String s = "Fontaine : soins rapides • potion " + Rules.POTION_PRICE + " or";
            ui.textCentered(ui.small, s, W / 2f, H - 66, new Color(0.6f, 0.9f, 1f, 1f));
        }

        // mini-carte
        float ms = 150, mx = W - ms - 16, my = H - ms - 76;
        ui.rect(mx - 3, my - 3, ms + 6, ms + 6, 0, 0, 0, 0.8f);
        ui.batch.draw(minimap, mx, my, ms, ms);
        float k = ms / WorldMap.SIZE;
        for (ClientState.Mob m : state.mobs.values())
            if (m.type == Rules.GOLEM) ui.rect(mx + m.x * k - 3, my + m.y * k - 3, 6, 6, 1f, 0.3f, 0.2f, 1f);
        for (ClientState.Avatar a : state.players.values())
            if (a.id != state.myId) ui.rect(mx + a.x * k - 3, my + a.y * k - 3, 6, 6, 0.4f, 0.8f, 1f, 1f);
        ui.rect(mx + state.myX * k - 3, my + state.myY * k - 3, 6, 6, 1f, 0.9f, 0.2f, 1f);

        // bouton quitter
        ui.button(quitArmed > 0 ? "Sûr ?" : "Quitter", btnQuitX(), BTN_QUIT_Y, BTN_QUIT_W, BTN_QUIT_H, quitArmed > 0);
        if (quitArmed > 0) ui.textCentered(ui.small, "Touche encore pour quitter (progression sauvegardée)", W / 2f, H - 100, Color.WHITE);

        // joystick
        if (joyPointer >= 0) {
            ui.ringShape(joyBaseX, joyBaseY, 90, 1, 1, 1, 0.35f);
            ui.circle(joyX, joyY, 42, 1, 1, 1, 0.45f);
        } else {
            ui.ringShape(150, 150, 90, 1, 1, 1, 0.12f);
            ui.textCentered(ui.small, "Glisse ici pour bouger", 150, 150, new Color(1, 1, 1, 0.35f));
        }

        // boutons d'action
        roundButton(btnAttackX(), BTN_ATTACK_Y, BTN_ATTACK_R, attackHeld ? 0.95f : 0.75f, 0.35f, 0.25f, "ATTAQUE", ui.font);
        roundButton(btnPotX(), BTN_POT_Y, BTN_POT_R, 0.3f, 0.7f, 0.35f, "✚ " + state.potions, ui.font);
        if (nearFountain()) roundButton(btnBuyX(), BTN_BUY_Y, BTN_BUY_R, 0.85f, 0.65f, 0.2f, "Acheter", ui.small);
        roundButton(btnChatX(), BTN_CHAT_Y, BTN_CHAT_R, 0.35f, 0.4f, 0.75f, "Chat", ui.small);

        ui.end();
    }

    private void roundButton(float x, float y, float r, float red, float g, float b, String label, com.badlogic.gdx.graphics.g2d.BitmapFont f) {
        ui.circle(x, y - 4, r, 0, 0, 0, 0.45f);
        ui.circle(x, y, r, red, g, b, 0.85f);
        ui.ringShape(x, y, r, 1, 1, 1, 0.35f);
        ui.textCentered(f, label, x, y, Color.WHITE);
    }

    @Override
    public void dispose() {
        if (minimap != null) { minimap.dispose(); minimap = null; }
    }
}
