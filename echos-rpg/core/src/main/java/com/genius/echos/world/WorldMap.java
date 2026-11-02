package com.genius.echos.world;

import java.util.Random;

/**
 * Carte du monde générée de façon déterministe à partir d'une graine.
 * Le serveur et chaque client génèrent exactement la même carte à partir de la graine
 * envoyée à la connexion : inutile de transférer la carte sur le réseau.
 * (Uniquement de l'arithmétique simple : résultat identique sur toutes les JVM.)
 */
public final class WorldMap {
    public static final int SIZE = 128;

    public static final byte WATER = 0, SAND = 1, GRASS = 2, TREE = 3, PATH = 4, STONE = 5,
            WALL = 6, HOUSE = 7, FOUNTAIN = 8, FLOWERS = 9, DARK_GRASS = 10, ROCK = 11;

    public static final float VILLAGE_X = SIZE / 2f, VILLAGE_Y = SIZE / 2f;
    public static final float RUINS_X = 108.5f, RUINS_Y = 108.5f;

    public final long seed;
    private final byte[] tiles = new byte[SIZE * SIZE];

    public WorldMap(long seed) {
        this.seed = seed;
        generate();
    }

    public byte get(int x, int y) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return WATER;
        return tiles[y * SIZE + x];
    }

    private void set(int x, int y, byte t) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        tiles[y * SIZE + x] = t;
    }

    public static boolean isSolid(byte t) {
        return t == WATER || t == TREE || t == WALL || t == HOUSE || t == FOUNTAIN || t == ROCK;
    }

    public boolean isSolidAt(float x, float y) {
        return isSolid(get((int) Math.floor(x), (int) Math.floor(y)));
    }

    /** Une entité de rayon r peut-elle se tenir en (x,y) ? */
    public boolean canStand(float x, float y, float r) {
        return !isSolidAt(x - r, y - r) && !isSolidAt(x + r, y - r)
                && !isSolidAt(x - r, y + r) && !isSolidAt(x + r, y + r);
    }

    // ---------------------------------------------------------------- génération

    private float[] lattice(Random rnd, int cells) {
        float[] g = new float[(cells + 1) * (cells + 1)];
        for (int i = 0; i < g.length; i++) g[i] = rnd.nextFloat();
        return g;
    }

    private static float smooth(float t) { return t * t * (3f - 2f * t); }

    private static float sample(float[] g, int cells, float x, float y) {
        float fx = x * cells / SIZE, fy = y * cells / SIZE;
        int ix = Math.min((int) fx, cells - 1), iy = Math.min((int) fy, cells - 1);
        float tx = smooth(fx - ix), ty = smooth(fy - iy);
        int w = cells + 1;
        float a = g[iy * w + ix], b = g[iy * w + ix + 1];
        float c = g[(iy + 1) * w + ix], d = g[(iy + 1) * w + ix + 1];
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * ty;
    }

    private void generate() {
        Random rnd = new Random(seed);
        float[] h1 = lattice(rnd, 8), h2 = lattice(rnd, 16), h3 = lattice(rnd, 32);
        float[] f1 = lattice(rnd, 12), f2 = lattice(rnd, 24);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float h = sample(h1, 8, x, y) * 0.6f + sample(h2, 16, x, y) * 0.3f + sample(h3, 32, x, y) * 0.1f;
                // île : on abaisse les bords
                float dx = (x - SIZE / 2f) / (SIZE / 2f), dy = (y - SIZE / 2f) / (SIZE / 2f);
                float edge = Math.max(Math.abs(dx), Math.abs(dy));
                h -= Math.max(0f, edge - 0.72f) * 2.2f;
                float forest = sample(f1, 12, x, y) * 0.7f + sample(f2, 24, x, y) * 0.3f;

                byte t;
                if (h < 0.30f) t = WATER;
                else if (h < 0.35f) t = SAND;
                else if (h > 0.70f) t = rnd.nextFloat() < 0.35f ? ROCK : STONE;
                else if (forest > 0.58f) t = rnd.nextFloat() < 0.42f ? TREE : DARK_GRASS;
                else t = rnd.nextFloat() < 0.04f ? FLOWERS : GRASS;
                set(x, y, t);
            }
        }

        buildVillage(rnd);
        buildRuins(rnd);
        // chemins vers les quatre directions + vers les ruines
        carvePath((int) VILLAGE_X, (int) VILLAGE_Y, (int) RUINS_X, (int) RUINS_Y, rnd);
        carvePath((int) VILLAGE_X, (int) VILLAGE_Y, 20, 64, rnd);
        carvePath((int) VILLAGE_X, (int) VILLAGE_Y, 64, 18, rnd);
        carvePath((int) VILLAGE_X, (int) VILLAGE_Y, 108, 40, rnd);
        carvePath((int) VILLAGE_X, (int) VILLAGE_Y, 30, 105, rnd);
    }

    private void buildVillage(Random rnd) {
        int cx = (int) VILLAGE_X, cy = (int) VILLAGE_Y;
        for (int y = cy - 9; y <= cy + 9; y++)
            for (int x = cx - 9; x <= cx + 9; x++) {
                int d = Math.max(Math.abs(x - cx), Math.abs(y - cy));
                set(x, y, d <= 7 ? GRASS : (get(x, y) == WATER || isSolid(get(x, y)) ? GRASS : get(x, y)));
            }
        for (int i = -7; i <= 7; i++) { set(cx + i, cy, PATH); set(cx, cy + i, PATH); }
        for (int y = cy - 2; y <= cy + 2; y++) for (int x = cx - 2; x <= cx + 2; x++) set(x, y, PATH);
        set(cx, cy, FOUNTAIN);
        int[][] houses = {{-6, -6}, {3, -6}, {-6, 4}, {4, 4}};
        for (int[] hpos : houses)
            for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) set(cx + hpos[0] + x, cy + hpos[1] + y, HOUSE);
        for (int i = 0; i < 10; i++) set(cx - 7 + rnd.nextInt(15), cy - 7 + rnd.nextInt(15), FLOWERS);
        set(cx, cy, FOUNTAIN);
        for (int i = -7; i <= 7; i++) { if (get(cx + i, cy) != FOUNTAIN) set(cx + i, cy, PATH); if (get(cx, cy + i) != FOUNTAIN) set(cx, cy + i, PATH); }
    }

    private void buildRuins(Random rnd) {
        int cx = (int) RUINS_X, cy = (int) RUINS_Y;
        for (int y = cy - 7; y <= cy + 7; y++)
            for (int x = cx - 7; x <= cx + 7; x++) {
                int d = Math.max(Math.abs(x - cx), Math.abs(y - cy));
                if (d == 6 && rnd.nextFloat() < 0.7f && Math.abs(x - cx) > 1 && Math.abs(y - cy) > 1) set(x, y, WALL);
                else set(x, y, STONE);
            }
    }

    private void carvePath(int x0, int y0, int x1, int y1, Random rnd) {
        int x = x0, y = y0;
        int guard = 0;
        while ((x != x1 || y != y1) && guard++ < 1000) {
            if (x != x1 && (y == y1 || rnd.nextBoolean())) x += Integer.signum(x1 - x);
            else y += Integer.signum(y1 - y);
            byte t = get(x, y);
            if (t == FOUNTAIN || t == HOUSE || t == WALL) continue;
            if (t == WATER) set(x, y, SAND); // gué / pont de sable
            else if (t != STONE) set(x, y, PATH);
        }
    }

    /** Distance au centre du village. */
    public static float distToVillage(float x, float y) {
        float dx = x - VILLAGE_X, dy = y - VILLAGE_Y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
