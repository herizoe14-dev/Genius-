package com.genius.echos.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.genius.echos.world.WorldMap;

import java.util.Random;

/**
 * Tous les graphismes du jeu sont dessinés par le code (pixel art original),
 * dans une seule texture pour un rendu rapide.
 */
public final class Art implements Disposable {
    private static final int CELL = 16;
    private final Pixmap pm = new Pixmap(512, 512, Pixmap.Format.RGBA8888);
    private final Random rnd = new Random(7);
    private int cursor = 0;
    public final Texture texture = new Texture(512, 512, Pixmap.Format.RGBA8888);
    /** Formes lisses (filtrage linéaire) pour les boutons et effets. */
    private final Texture shapes;

    public final TextureRegion[] grass = new TextureRegion[3];
    public TextureRegion darkGrass, flowers, sand, path, stone, tree, rock, wall, roof, houseWall, fountain;
    public final TextureRegion[] water = new TextureRegion[2];
    /** [classe][image] */
    public final TextureRegion[][] heroes = new TextureRegion[3][2];
    /** [type][image] */
    public final TextureRegion[][] mobs = new TextureRegion[4][2];
    public TextureRegion white, circle, shadow, ring;

    public Art() {
        pm.setBlending(Pixmap.Blending.SourceOver);
        for (int i = 0; i < 3; i++) grass[i] = cell(this::drawGrass, i);
        darkGrass = cell((x, y, v) -> { noise(x, y, 0x2f6b2a, 0x285d24, 0x37782f); }, 0);
        flowers = cell((x, y, v) -> {
            drawGrass(x, y, 0);
            int[] cols = {0xf2d14b, 0xe86a8a, 0xffffff, 0x9b7bf2};
            for (int i = 0; i < 4; i++) {
                int fx = x + 2 + rnd.nextInt(12), fy = y + 2 + rnd.nextInt(12);
                px(fx, fy, cols[rnd.nextInt(cols.length)]);
                px(fx + 1, fy, 0xfff6c0);
            }
        }, 0);
        sand = cell((x, y, v) -> noise(x, y, 0xe3cf8f, 0xd6bf7c, 0xeedc9f), 0);
        for (int i = 0; i < 2; i++) water[i] = cell(this::drawWater, i);
        path = cell((x, y, v) -> {
            noise(x, y, 0xb8955f, 0xa98652, 0xc4a36d);
            for (int i = 0; i < 4; i++) px(x + rnd.nextInt(16), y + rnd.nextInt(16), 0x8d7046);
        }, 0);
        stone = cell((x, y, v) -> {
            noise(x, y, 0x8b8f98, 0x7d818a, 0x979ba3);
            rect(x, y + 7, 16, 1, 0x5f636b); rect(x + 7, y, 1, 7, 0x5f636b); rect(x + 3, y + 8, 1, 8, 0x5f636b);
        }, 0);
        tree = cell((x, y, v) -> {
            drawGrass(x, y, 1);
            ellipse(x + 8, y + 14, 6, 2, 0x1d3d18);
            rect(x + 7, y + 10, 3, 5, 0x6b4426);
            circ(x + 8, y + 7, 6, 0x1f5a24);
            circ(x + 6, y + 5, 4, 0x2e7a31);
            circ(x + 10, y + 6, 3, 0x2a6e2c);
            px(x + 5, y + 3, 0x5aa850); px(x + 6, y + 4, 0x5aa850);
        }, 0);
        rock = cell((x, y, v) -> {
            noise(x, y, 0x8b8f98, 0x7d818a, 0x979ba3);
            circ(x + 8, y + 9, 6, 0x5d6068);
            circ(x + 7, y + 8, 5, 0x767a83);
            px(x + 5, y + 6, 0xa3a7af); px(x + 6, y + 5, 0xa3a7af);
        }, 0);
        wall = cell((x, y, v) -> {
            rect(x, y, 16, 16, 0x6c6f78);
            for (int r = 0; r < 4; r++) {
                rect(x, y + r * 4 + 3, 16, 1, 0x464850);
                int off = (r % 2) * 4;
                for (int c = 0; c < 3; c++) rect(x + off + c * 8, y + r * 4, 1, 3, 0x464850);
            }
        }, 0);
        roof = cell((x, y, v) -> {
            rect(x, y, 16, 16, 0xa8433a);
            for (int r = 0; r < 16; r += 3) rect(x, y + r, 16, 1, 0x7e2f29);
            rect(x, y + 15, 16, 1, 0x5a201c);
        }, 0);
        houseWall = cell((x, y, v) -> {
            rect(x, y, 16, 16, 0xd9c49a);
            rect(x, y, 16, 1, 0x9e8a63);
            rect(x + 3, y + 4, 4, 4, 0x5b8fc7); rect(x + 3, y + 4, 4, 1, 0x3b3b3b);
            rect(x + 10, y + 7, 4, 9, 0x7a4a26); px(x + 13, y + 11, 0xf0d060);
        }, 0);
        fountain = cell((x, y, v) -> {
            rect(x, y, 16, 16, 0xb8955f);
            circ(x + 8, y + 8, 7, 0x9aa0aa);
            circ(x + 8, y + 8, 5, 0x3f8fd8);
            circ(x + 8, y + 8, 2, 0xbfe6ff);
            px(x + 8, y + 3, 0xffffff);
        }, 0);

        int[] tunic = {0xb23a3a, 0x3a9a4a, 0x4a4ab8};
        int[] hair = {0x5a3a1e, 0xe0b040, 0xeeeeee};
        for (int c = 0; c < 3; c++)
            for (int f = 0; f < 2; f++) {
                final int cc = c, ff = f;
                heroes[c][f] = cell((x, y, v) -> drawHero(x, y, cc, ff, tunic[cc], hair[cc]), 0);
            }
        for (int f = 0; f < 2; f++) {
            final int ff = f;
            mobs[0][f] = cell((x, y, v) -> drawSlime(x, y, ff), 0);
            mobs[1][f] = cell((x, y, v) -> drawWolf(x, y, ff), 0);
            mobs[2][f] = cell((x, y, v) -> drawSkeleton(x, y, ff), 0);
        }
        // Golem : 32x32 (bloc de 2x2 cellules alignées)
        if ((cursor % 32) % 2 == 1) cursor++;
        if ((cursor / 32) % 2 == 1) cursor += 32;
        for (int f = 0; f < 2; f++) {
            int cx = (cursor % 32) * CELL, cy = (cursor / 32) * CELL;
            drawGolem(cx, cy, f);
            mobs[3][f] = new TextureRegion(texture, cx, cy, 32, 32);
            cursor += 2;
        }

        // Formes utilitaires (64x64)
        int bx = 384, by = 448;
        pm.setColor(Color.WHITE);
        pm.fillRectangle(bx, by, 4, 4);
        pm.fillCircle(bx + 32, by + 32, 31);
        whiteRegion = new int[]{bx + 1, by + 1, 2, 2};
        circleRegion = new int[]{bx, by, 64, 64};
        // anneau
        int rx = 448, ry = 448;
        pm.setColor(Color.WHITE);
        pm.fillCircle(rx + 32, ry + 32, 31);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fillCircle(rx + 32, ry + 32, 25);
        pm.setBlending(Pixmap.Blending.SourceOver);
        ringRegion = new int[]{rx, ry, 64, 64};
        // ombre
        int sx = 384, sy = 416;
        for (int yy = 0; yy < 16; yy++)
            for (int xx = 0; xx < 32; xx++) {
                float dx = (xx - 15.5f) / 16f, dy = (yy - 7.5f) / 8f;
                float d = dx * dx + dy * dy;
                if (d < 1f) { pm.setColor(0, 0, 0, 0.35f * (1f - d)); pm.drawPixel(sx + xx, sy + yy); }
            }
        shadowRegion = new int[]{sx, sy, 32, 16};

        texture.draw(pm, 0, 0);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        white = reg(whiteRegion);
        shadow = reg(shadowRegion);

        Pixmap sp = new Pixmap(256, 128, Pixmap.Format.RGBA8888);
        sp.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                float d = (float) Math.sqrt((x - 63.5f) * (x - 63.5f) + (y - 63.5f) * (y - 63.5f));
                float aFill = Math.max(0f, Math.min(1f, 62f - d));
                float aRing = Math.max(0f, Math.min(1f, 62f - d)) * Math.max(0f, Math.min(1f, d - 51f));
                sp.setColor(1, 1, 1, aFill); sp.drawPixel(x, y);
                sp.setColor(1, 1, 1, aRing); sp.drawPixel(128 + x, y);
            }
        shapes = new Texture(sp, true);
        shapes.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        sp.dispose();
        circle = new TextureRegion(shapes, 0, 0, 128, 128);
        ring = new TextureRegion(shapes, 128, 0, 128, 128);
        pm.dispose();
    }

    private int[] whiteRegion, circleRegion, ringRegion, shadowRegion;

    private TextureRegion reg(int[] r) { return new TextureRegion(texture, r[0], r[1], r[2], r[3]); }

    public TextureRegion tile(byte t, int x, int y, float time, WorldMap map) {
        switch (t) {
            case WorldMap.WATER: return water[((int) (time * 1.5f) + x + y) % 2 == 0 ? 0 : 1];
            case WorldMap.SAND: return sand;
            case WorldMap.TREE: return tree;
            case WorldMap.PATH: return path;
            case WorldMap.STONE: return stone;
            case WorldMap.WALL: return wall;
            case WorldMap.HOUSE: return map.get(x, y + 1) == WorldMap.HOUSE ? houseWall : roof;
            case WorldMap.FOUNTAIN: return fountain;
            case WorldMap.FLOWERS: return flowers;
            case WorldMap.DARK_GRASS: return darkGrass;
            case WorldMap.ROCK: return rock;
            default: return grass[((x * 7 + y * 13) & 7) % 3];
        }
    }

    /** Couleur d'une case pour la mini-carte. */
    public static int miniColor(byte t) {
        switch (t) {
            case WorldMap.WATER: return 0x3f7fc8;
            case WorldMap.SAND: return 0xe3cf8f;
            case WorldMap.TREE: return 0x1f5a24;
            case WorldMap.PATH: return 0xb8955f;
            case WorldMap.STONE: return 0x8b8f98;
            case WorldMap.WALL: case WorldMap.ROCK: return 0x5d6068;
            case WorldMap.HOUSE: return 0xa8433a;
            case WorldMap.FOUNTAIN: return 0x8fd0ff;
            case WorldMap.DARK_GRASS: return 0x2f6b2a;
            default: return 0x4f9a3f;
        }
    }

    // ---------------------------------------------------------------- dessin

    private interface Painter { void paint(int x, int y, int variant); }

    private TextureRegion cell(Painter p, int variant) {
        int cx = (cursor % 32) * CELL, cy = (cursor / 32) * CELL;
        cursor++;
        p.paint(cx, cy, variant);
        return new TextureRegion(texture, cx, cy, CELL, CELL);
    }

    private void px(int x, int y, int rgb) {
        pm.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
        pm.drawPixel(x, y);
    }

    private void rect(int x, int y, int w, int h, int rgb) {
        pm.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
        pm.fillRectangle(x, y, w, h);
    }

    private void circ(int x, int y, int r, int rgb) {
        pm.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
        pm.fillCircle(x, y, r);
    }

    private void ellipse(int cx, int cy, int rx, int ry, int rgb) {
        for (int y = -ry; y <= ry; y++)
            for (int x = -rx; x <= rx; x++)
                if ((x * x) / (float) (rx * rx) + (y * y) / (float) (ry * ry) <= 1f) px(cx + x, cy + y, rgb);
    }

    private void noise(int x, int y, int base, int dark, int light) {
        rect(x, y, CELL, CELL, base);
        for (int i = 0; i < 18; i++) px(x + rnd.nextInt(16), y + rnd.nextInt(16), rnd.nextBoolean() ? dark : light);
    }

    private void drawGrass(int x, int y, int v) {
        noise(x, y, 0x4f9a3f, 0x438a35, 0x5cab4a);
        for (int i = 0; i < 3 + v; i++) {
            int gx = x + 1 + rnd.nextInt(14), gy = y + 2 + rnd.nextInt(13);
            px(gx, gy, 0x3a7a2e); px(gx, gy - 1, 0x6cbd58);
        }
    }

    private void drawWater(int x, int y, int v) {
        rect(x, y, 16, 16, 0x2f6fb8);
        for (int i = 0; i < 3; i++) {
            int wy = y + 3 + i * 5 + v;
            int wx = x + (i * 5 + v * 3) % 10;
            rect(wx, wy, 5, 1, 0x6fb0ee);
        }
    }

    private void drawHero(int x, int y, int cls, int frame, int tunic, int hair) {
        int skin = 0xf0c8a0;
        // jambes
        if (frame == 0) { rect(x + 6, y + 12, 2, 3, 0x3b2f2f); rect(x + 9, y + 12, 2, 3, 0x3b2f2f); }
        else { rect(x + 5, y + 12, 2, 3, 0x3b2f2f); rect(x + 10, y + 12, 2, 2, 0x3b2f2f); }
        // corps
        rect(x + 5, y + 7, 7, 6, tunic);
        rect(x + 5, y + 11, 7, 1, 0x3b2a1a);
        // tête
        rect(x + 5, y + 2, 7, 6, skin);
        rect(x + 5, y + 1, 7, 2, hair);
        rect(x + 5, y + 2, 1, 3, hair);
        px(x + 10, y + 4, 0x202020);
        if (cls == 2) { // chapeau de mage
            rect(x + 4, y + 2, 9, 1, 0x30308a);
            rect(x + 6, y, 5, 2, 0x30308a);
            px(x + 8, y - 0, 0xf0d060);
        }
        // arme
        if (cls == 0) { rect(x + 13, y + 3, 1, 8, 0xd8dde6); rect(x + 12, y + 10, 3, 1, 0x8a6a2a); }
        else if (cls == 1) { rect(x + 13, y + 4, 1, 8, 0x8a5a2a); px(x + 12, y + 3, 0x8a5a2a); px(x + 12, y + 12, 0x8a5a2a); rect(x + 12, y + 4, 1, 8, 0xdddddd); }
        else { rect(x + 13, y + 3, 1, 11, 0x7a4a26); circ(x + 13, y + 2, 1, 0x7fd8ff); }
    }

    private void drawSlime(int x, int y, int f) {
        int h = f == 0 ? 6 : 5, w = f == 0 ? 5 : 6;
        ellipse(x + 8, y + 15 - h, w + 1, h, 0x2f9a3a);
        ellipse(x + 8, y + 15 - h, w, h - 1, 0x55c95a);
        px(x + 6, y + 13 - h, 0xffffff); px(x + 10, y + 13 - h, 0xffffff);
        px(x + 6, y + 14 - h, 0x102010); px(x + 10, y + 14 - h, 0x102010);
        px(x + 5, y + 11 - h, 0xb8f0b0);
    }

    private void drawWolf(int x, int y, int f) {
        int g = 0x7d7f86, d = 0x55575e;
        rect(x + 3, y + 7, 9, 5, g);
        rect(x + 11, y + 5, 4, 4, g); rect(x + 14, y + 7, 2, 2, d);
        px(x + 12, y + 4, d); px(x + 14, y + 4, d);
        px(x + 13, y + 6, 0xffd040);
        rect(x + 1, y + 6, 2, 2, d);
        if (f == 0) { rect(x + 4, y + 12, 2, 3, d); rect(x + 10, y + 12, 2, 3, d); }
        else { rect(x + 3, y + 12, 2, 3, d); rect(x + 11, y + 12, 2, 3, d); }
        rect(x + 3, y + 7, 9, 1, 0x9a9ca3);
    }

    private void drawSkeleton(int x, int y, int f) {
        int b = 0xe8e4d8, d = 0x9a968a;
        rect(x + 5, y + 1, 6, 6, b);
        px(x + 7, y + 3, 0x101010); px(x + 9, y + 3, 0x101010);
        rect(x + 6, y + 6, 4, 1, d);
        rect(x + 7, y + 7, 2, 5, b);
        for (int i = 0; i < 3; i++) rect(x + 5, y + 8 + i * 1, 6, 1, i % 2 == 0 ? b : d);
        if (f == 0) { rect(x + 6, y + 12, 1, 3, b); rect(x + 9, y + 12, 1, 3, b); }
        else { rect(x + 5, y + 12, 1, 3, b); rect(x + 10, y + 12, 1, 3, b); }
        rect(x + 12, y + 2, 1, 10, 0xb0b5c0); rect(x + 11, y + 9, 3, 1, 0x6a4a2a);
    }

    private void drawGolem(int x, int y, int f) {
        int s = 0x7a6a58, d = 0x564a3c, m = 0x55d0a0;
        int o = f == 0 ? 0 : 1;
        rect(x + 8, y + 9 + o, 16, 14, s);
        rect(x + 10, y + 3 + o, 12, 8, s);
        rect(x + 12, y + 6 + o, 2, 2, m); rect(x + 18, y + 6 + o, 2, 2, m);
        rect(x + 3, y + 10 + o, 5, 11, d); rect(x + 24, y + 10 - o, 5, 11, d);
        rect(x + 10, y + 23, 5, 7, d); rect(x + 17, y + 23, 5, 7, d);
        for (int i = 0; i < 6; i++) px(x + 9 + rnd.nextInt(14), y + 10 + rnd.nextInt(12), d);
        rect(x + 14, y + 14 + o, 4, 4, m);
    }

    @Override public void dispose() { texture.dispose(); shapes.dispose(); }
}
