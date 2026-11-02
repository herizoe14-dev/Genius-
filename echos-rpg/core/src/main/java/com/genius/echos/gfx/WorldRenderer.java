package com.genius.echos.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.genius.echos.net.ClientState;
import com.genius.echos.net.Protocol;
import com.genius.echos.world.Items;
import com.genius.echos.world.Rules;
import com.genius.echos.world.WorldMap;

import java.util.ArrayList;
import java.util.List;

/** Dessin du monde : sol avec transitions, décor, bâtiments, personnages, effets. */
public final class WorldRenderer {
    private final Art art;
    private final WorldMap map;
    private final float[] tint;
    private final List<Item> items = new ArrayList<>();
    private final List<Item> pool = new ArrayList<>();

    /** Gardiens des boutiques (apparence fixe). */
    public static final int[][] KEEPERS = { // look, classe, armure
            {CharacterDrawer.look(0, 2, 3), 2, 2}, {CharacterDrawer.look(1, 0, 1), 0, 1},
            {CharacterDrawer.look(2, 1, 4), 0, 3}, {CharacterDrawer.look(3, 3, 5), 1, 2}};

    private static final int K_OBJ = 0, K_BUILDING = 1, K_PLAYER = 2, K_MOB = 3, K_KEEPER = 4;

    private static final class Item {
        int kind, a, b; float y; Object ref;
    }

    public WorldRenderer(Art art, WorldMap map) {
        this.art = art;
        this.map = map;
        tint = new float[WorldMap.SIZE * WorldMap.SIZE];
        java.util.Random r = new java.util.Random(map.seed ^ 0x5eed);
        int cells = 20;
        float[] g = new float[(cells + 1) * (cells + 1)];
        for (int i = 0; i < g.length; i++) g[i] = r.nextFloat();
        for (int y = 0; y < WorldMap.SIZE; y++)
            for (int x = 0; x < WorldMap.SIZE; x++) {
                float fx = x * cells / (float) WorldMap.SIZE, fy = y * cells / (float) WorldMap.SIZE;
                int ix = (int) fx, iy = (int) fy;
                float tx = fx - ix, ty = fy - iy;
                tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
                float a = g[iy * (cells + 1) + ix], b = g[iy * (cells + 1) + ix + 1], c = g[(iy + 1) * (cells + 1) + ix], d = g[(iy + 1) * (cells + 1) + ix + 1];
                float n = (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
                tint[y * WorldMap.SIZE + x] = 0.92f + n * 0.14f;
            }
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private Item obtain() {
        Item it = pool.isEmpty() ? new Item() : pool.remove(pool.size() - 1);
        items.add(it);
        return it;
    }

    public void render(Batch batch, OrthographicCamera cam, ClientState s, int myCls, int myLook, float time) {
        float vw = cam.viewportWidth, vh = cam.viewportHeight;
        int x0 = (int) Math.floor(cam.position.x - vw / 2f) - 1, x1 = (int) Math.ceil(cam.position.x + vw / 2f) + 1;
        int y0 = (int) Math.floor(cam.position.y - vh / 2f) - 1, y1 = (int) Math.ceil(cam.position.y + vh / 2f) + 1;

        drawGround(batch, x0, x1, y0, y1, time);

        // ---- liste des éléments à trier par profondeur
        pool.addAll(items);
        items.clear();
        for (int y = y0 - 3; y <= y1 + 1; y++)
            for (int x = x0 - 2; x <= x1 + 2; x++) {
                byte o = map.obj(x, y);
                if (o == WorldMap.NONE) continue;
                Item it = obtain(); it.kind = K_OBJ; it.a = x; it.b = y; it.y = y + 0.3f; it.ref = null;
            }
        for (WorldMap.Building b : map.buildings) {
            if (b.x > x1 + 1 || b.x + b.drawW < x0 - 1 || b.y > y1 + 1 || b.y + b.drawH < y0 - 1) continue;
            Item it = obtain(); it.kind = K_BUILDING; it.ref = b; it.y = b.y;
            if (b.isShop()) { Item k = obtain(); k.kind = K_KEEPER; k.ref = b; k.y = b.doorY() + 0.1f; }
        }
        for (ClientState.Mob m : s.mobs.values()) { Item it = obtain(); it.kind = K_MOB; it.ref = m; it.y = m.y; }
        for (ClientState.Avatar av : s.players.values()) { Item it = obtain(); it.kind = K_PLAYER; it.ref = av; it.y = av.id == s.myId ? s.myY : av.y; }
        if (!s.players.containsKey(s.myId)) { Item it = obtain(); it.kind = K_PLAYER; it.ref = null; it.y = s.myY; }
        items.sort((p, q) -> Float.compare(q.y, p.y));

        for (Item it : items) {
            switch (it.kind) {
                case K_OBJ: drawObj(batch, it.a, it.b); break;
                case K_BUILDING: drawBuilding(batch, (WorldMap.Building) it.ref, time); break;
                case K_KEEPER: {
                    WorldMap.Building b = (WorldMap.Building) it.ref;
                    int[] k = KEEPERS[b.type];
                    float kx = b.doorX() + 1.5f, ky = b.doorY() + 0.15f;
                    batch.draw(art.shadow, kx - 0.4f, ky - 0.3f, 0.8f, 0.3f);
                    CharacterDrawer.draw(batch, art, k[0], k[1], k[2], 0, CharacterDrawer.DIR_DOWN, 1, kx - 0.5f, ky - 0.22f, 1f, 1.25f);
                    break;
                }
                case K_MOB: drawMob(batch, (ClientState.Mob) it.ref); break;
                default: {
                    ClientState.Avatar av = (ClientState.Avatar) it.ref;
                    if (av == null || av.id == s.myId) {
                        int wt = Items.tier(s.weapon), at = Items.tier(s.armor);
                        drawHero(batch, myLook, myCls, at, wt, s.myDir, s.myMoving, s.walkAnim, s.myX, s.myY);
                    } else drawHero(batch, av.look, av.cls, av.armorTier, av.weaponTier, av.dir, av.moving, av.anim, av.x, av.y);
                }
            }
        }
        drawFx(batch, s, time);
        batch.setColor(Color.WHITE);
    }

    // ================================================================ sol

    private void drawGround(Batch batch, int x0, int x1, int y0, int y1, float time) {
        int[] kinds = {WorldMap.SAND, WorldMap.DIRT, WorldMap.GRASS, WorldMap.DARK_GRASS};
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                byte t = map.get(x, y);
                float k = (x >= 0 && y >= 0 && x < WorldMap.SIZE && y < WorldMap.SIZE) ? tint[y * WorldMap.SIZE + x] : 1f;
                batch.setColor(k, k, k, 1f);
                TextureRegion r;
                if (t == WorldMap.WATER) r = art.water[(Math.floorMod(x + y * 3, 3) + (int) (time * 2.2f)) % 3];
                else { TextureRegion[] v = art.ground[t]; r = v[Math.floorMod(hash(x, y), v.length)]; }
                batch.draw(r, x, y, 1.002f, 1.002f);

                int p = Art.priority(t);
                for (int kind : kinds) {
                    boolean up = cover(x, y + 1, p, kind), left = cover(x - 1, y, p, kind), down = cover(x, y - 1, p, kind), right = cover(x + 1, y, p, kind);
                    if (!(up || left || down || right || cover(x - 1, y + 1, p, kind) || cover(x - 1, y - 1, p, kind) || cover(x + 1, y - 1, p, kind) || cover(x + 1, y + 1, p, kind))) continue;
                    TextureRegion e = art.edge[kind], c = art.corner[kind];
                    if (up) rot(batch, e, x, y, 0);
                    if (left) rot(batch, e, x, y, 90);
                    if (down) rot(batch, e, x, y, 180);
                    if (right) rot(batch, e, x, y, 270);
                    if (!up && !left && cover(x - 1, y + 1, p, kind)) rot(batch, c, x, y, 0);
                    if (!down && !left && cover(x - 1, y - 1, p, kind)) rot(batch, c, x, y, 90);
                    if (!down && !right && cover(x + 1, y - 1, p, kind)) rot(batch, c, x, y, 180);
                    if (!up && !right && cover(x + 1, y + 1, p, kind)) rot(batch, c, x, y, 270);
                }
            }
        batch.setColor(Color.WHITE);
    }

    private boolean cover(int x, int y, int myPrio, int kind) {
        byte n = map.get(x, y);
        return Art.edgeKind(n) == kind && Art.priority(n) > myPrio;
    }

    private static void rot(Batch b, TextureRegion r, float x, float y, float deg) {
        b.draw(r, x, y, 0.5f, 0.5f, 1.002f, 1.002f, 1f, 1f, deg);
    }

    // ================================================================ décor

    private void shadow(Batch b, float cx, float y, float w) {
        b.draw(art.shadow, cx - w / 2f, y - w * 0.14f, w, w * 0.32f);
    }

    private void sprite(Batch b, TextureRegion r, float cx, float y) {
        float w = r.getRegionWidth() / 32f, h = r.getRegionHeight() / 32f;
        b.draw(r, cx - w / 2f, y, w, h);
    }

    private void drawObj(Batch b, int x, int y) {
        byte o = map.obj(x, y);
        int v = hash(x, y) & 1;
        float cx = x + 0.5f, by = y + 0.08f;
        switch (o) {
            case WorldMap.TREE: shadow(b, cx, by + 0.1f, 1.8f); sprite(b, art.tree[v], cx, by); break;
            case WorldMap.PINE: shadow(b, cx, by + 0.1f, 1.4f); sprite(b, art.pine[v], cx, by); break;
            case WorldMap.BUSH: shadow(b, cx, by + 0.1f, 1.1f); sprite(b, art.bush[v], cx, by); break;
            case WorldMap.ROCK: shadow(b, cx, by + 0.1f, 1.0f); sprite(b, art.rock[v], cx, by); break;
            case WorldMap.STUMP: sprite(b, art.stump, cx, by + 0.1f); break;
            case WorldMap.BARREL: shadow(b, cx, by + 0.1f, 0.8f); sprite(b, art.barrel, cx, by); break;
            case WorldMap.CRATE: shadow(b, cx, by + 0.1f, 0.9f); sprite(b, art.crate, cx, by); break;
            case WorldMap.LAMP: shadow(b, cx, by + 0.1f, 0.5f); sprite(b, art.lamp, cx, by); break;
            case WorldMap.HAY: shadow(b, cx, by + 0.1f, 1.2f); sprite(b, art.hay, cx, by); break;
            case WorldMap.FLOWER_POT: sprite(b, art.flowerPot, cx, by); break;
            case WorldMap.RUIN_WALL: sprite(b, art.ruinWall, cx, y); break;
            case WorldMap.PILLAR: shadow(b, cx, by + 0.1f, 0.8f); sprite(b, art.pillar, cx, by); break;
            case WorldMap.FENCE: {
                if (map.obj(x + 1, y) == WorldMap.FENCE) b.draw(art.railH, cx, y + 0.15f, 34 / 32f, 22 / 32f);
                if (map.obj(x, y + 1) == WorldMap.FENCE) b.draw(art.railV, cx - 5 / 32f, y + 0.4f, 10 / 32f, 34 / 32f);
                sprite(b, art.post, cx, y + 0.05f);
                break;
            }
            default: break;
        }
    }

    private void drawBuilding(Batch b, WorldMap.Building bd, float time) {
        switch (bd.type) {
            case WorldMap.B_FOUNTAIN: {
                b.draw(art.fountain, bd.x - 0.06f, bd.y - 0.45f, 100 / 32f, 100 / 32f);
                for (int i = 0; i < 6; i++) { // scintillements
                    float ph = (time * 0.9f + i / 6f) % 1f;
                    float sx = bd.x + 1.5f + (float) Math.sin(i * 2.1f + time) * 0.9f, sy = bd.y + 0.35f + (float) Math.cos(i * 1.3f) * 0.25f;
                    b.setColor(1, 1, 1, 0.8f * (1 - ph));
                    b.draw(art.circle, sx - 0.04f, sy + ph * 0.15f, 0.08f, 0.08f);
                }
                b.setColor(Color.WHITE);
                break;
            }
            case WorldMap.B_STALL:
                shadow(b, bd.x + 1.5f, bd.y + 0.15f, 3f);
                b.draw(art.stall, bd.x - 0.03f, bd.y - 0.1f, 98 / 32f, 86 / 32f);
                break;
            default: {
                b.setColor(0, 0, 0, 0.25f);
                b.draw(art.white, bd.x + 0.2f, bd.y - 0.3f, bd.drawW, 0.35f);
                b.setColor(Color.WHITE);
                b.draw(art.buildings[bd.type], bd.x, bd.y, bd.drawW, bd.drawH);
                if (bd.type == WorldMap.B_FORGE || bd.type == WorldMap.B_INN) { // fumée de cheminée
                    float chx = bd.x + (bd.type == WorldMap.B_FORGE ? 142 : 150) / 32f, chy = bd.y + bd.drawH - (bd.type == WorldMap.B_FORGE ? 6 : 14) / 32f;
                    for (int i = 0; i < 6; i++) {
                        float ph = (time * 0.35f + i / 6f) % 1f;
                        float r = 0.15f + ph * 0.35f;
                        float sx = chx + (float) Math.sin(ph * 5 + i) * 0.2f + ph * 0.4f, sy = chy + ph * 1.8f;
                        b.setColor(0.75f, 0.75f, 0.78f, 0.55f * (1 - ph));
                        b.draw(art.circle, sx - r, sy - r, r * 2, r * 2);
                    }
                    b.setColor(Color.WHITE);
                }
            }
        }
    }

    // ================================================================ personnages

    private void drawHero(Batch b, int look, int cls, int armorTier, int weaponTier, int dir, boolean moving, float anim, float x, float y) {
        b.draw(art.shadow, x - 0.38f, y - 0.3f, 0.76f, 0.28f);
        int frame = moving ? new int[]{0, 1, 2, 1}[((int) (anim * 9)) % 4] : 1;
        float bob = moving ? Math.abs((float) Math.sin(anim * 9 * Math.PI / 2)) * 0.03f : 0;
        CharacterDrawer.draw(b, art, look, cls, armorTier, weaponTier, dir, frame, x - 0.5f, y - 0.22f + bob, 1f, 1.25f);
    }

    private void drawMob(Batch b, ClientState.Mob m) {
        TextureRegion r = art.mobs[m.type][((int) (m.anim * (m.type == Rules.SLIME ? 3 : 6))) % 2];
        float w = r.getRegionWidth() / 32f, h = r.getRegionHeight() / 32f;
        b.draw(art.shadow, m.x - w * 0.4f, m.y - 0.28f, w * 0.8f, 0.3f * Math.max(1, w * 0.7f));
        if (m.hurt > 0) b.setColor(1f, 0.45f, 0.45f, 1f);
        float base = m.y - 0.22f;
        if (m.facing < 0 && m.type != Rules.SLIME) b.draw(r, m.x + w / 2f, base, -w, h);
        else b.draw(r, m.x - w / 2f, base, w, h);
        b.setColor(Color.WHITE);
        if (m.hp < m.maxHp) {
            float bw = Math.max(0.8f, w * 0.7f), bx = m.x - bw / 2f, by = base + h + 0.05f;
            b.setColor(0, 0, 0, 0.8f); b.draw(art.white, bx - 0.02f, by - 0.02f, bw + 0.04f, 0.12f);
            b.setColor(0.9f, 0.2f, 0.2f, 1f); b.draw(art.white, bx, by, bw * m.hp / (float) m.maxHp, 0.08f);
            b.setColor(Color.WHITE);
        }
    }

    // ================================================================ effets

    private void drawFx(Batch batch, ClientState s, float time) {
        for (ClientState.Fx fx : s.fxs) {
            float k = fx.t / fx.life;
            switch (fx.kind) {
                case Protocol.FX_SLASH: {
                    float r = 0.35f + k * 0.5f;
                    batch.setColor(1f, 1f, 0.9f, 1f - k);
                    batch.draw(art.ring, fx.x2 - r, fx.y2 - r + 0.3f, r * 2, r * 2);
                    break;
                }
                case Protocol.FX_ARROW: {
                    float x = fx.x1 + (fx.x2 - fx.x1) * k, y = fx.y1 + (fx.y2 - fx.y1) * k + 0.4f;
                    float ang = (float) Math.toDegrees(Math.atan2(fx.y2 - fx.y1, fx.x2 - fx.x1));
                    batch.setColor(0.95f, 0.88f, 0.65f, 1f);
                    batch.draw(art.white, x - 0.25f, y - 0.03f, 0.25f, 0.03f, 0.5f, 0.06f, 1, 1, ang);
                    break;
                }
                case Protocol.FX_FIREBALL: {
                    float x = fx.x1 + (fx.x2 - fx.x1) * k, y = fx.y1 + (fx.y2 - fx.y1) * k + 0.4f;
                    batch.setColor(1f, 0.5f, 0.1f, 1f); batch.draw(art.circle, x - 0.22f, y - 0.22f, 0.44f, 0.44f);
                    batch.setColor(1f, 0.95f, 0.5f, 1f); batch.draw(art.circle, x - 0.11f, y - 0.11f, 0.22f, 0.22f);
                    if (k > 0.7f) {
                        float r = Rules.CLASS_SPLASH[Rules.MAGE] * (k - 0.7f) / 0.3f;
                        batch.setColor(1f, 0.5f, 0.1f, 0.7f);
                        batch.draw(art.ring, fx.x2 - r, fx.y2 - r + 0.3f, r * 2, r * 2);
                    }
                    break;
                }
                case Protocol.FX_WHIRL: {
                    float r = 0.5f + k * Rules.SKILL_RADIUS[Rules.WARRIOR];
                    batch.setColor(1f, 0.85f, 0.5f, 1f - k);
                    batch.draw(art.ring, fx.x1 - r, fx.y1 - r + 0.3f, r * 2, r * 2);
                    batch.setColor(1f, 1f, 1f, 0.6f * (1 - k));
                    batch.draw(art.ring, fx.x1 - r * 0.7f, fx.y1 - r * 0.7f + 0.3f, r * 1.4f, r * 1.4f);
                    break;
                }
                case Protocol.FX_METEOR: {
                    if (k < 0.55f) {
                        float kk = k / 0.55f;
                        float x = fx.x2 + 2.5f * (1 - kk), y = fx.y2 + 4f * (1 - kk);
                        batch.setColor(1f, 0.4f, 0.1f, 1f); batch.draw(art.circle, x - 0.4f, y - 0.4f, 0.8f, 0.8f);
                        batch.setColor(1f, 0.9f, 0.4f, 1f); batch.draw(art.circle, x - 0.2f, y - 0.2f, 0.4f, 0.4f);
                    } else {
                        float kk = (k - 0.55f) / 0.45f, r = Rules.SKILL_RADIUS[Rules.MAGE] * (0.3f + kk * 0.7f);
                        batch.setColor(1f, 0.45f, 0.1f, 0.5f * (1 - kk)); batch.draw(art.circle, fx.x2 - r, fx.y2 - r, r * 2, r * 2);
                        batch.setColor(1f, 0.8f, 0.3f, 1 - kk); batch.draw(art.ring, fx.x2 - r, fx.y2 - r, r * 2, r * 2);
                    }
                    break;
                }
                case Protocol.FX_BITE: {
                    float r = 0.25f + k * 0.2f;
                    batch.setColor(1f, 0.2f, 0.2f, 1f - k);
                    batch.draw(art.ring, fx.x2 - r, fx.y2 - r + 0.3f, r * 2, r * 2);
                    break;
                }
                case Protocol.FX_HEAL: case Protocol.FX_MANA: {
                    boolean mana = fx.kind == Protocol.FX_MANA;
                    for (int i = 0; i < 5; i++) {
                        float ox = (float) Math.sin(i * 1.7f + fx.x1) * 0.35f, oy = k * 1.0f + i * 0.1f;
                        if (mana) batch.setColor(0.45f, 0.65f, 1f, 1f - k); else batch.setColor(0.4f, 1f, 0.5f, 1f - k);
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
}
