package com.genius.echos.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Carte du monde générée de façon déterministe à partir d'une graine
 * (identique chez le serveur et chez chaque client : rien à transférer).
 * Trois couches : sol, objets posés sur une case, et grands éléments (bâtiments…).
 */
public final class WorldMap {
    public static final int SIZE = 128;

    // ---- sol
    public static final byte WATER = 0, SAND = 1, DIRT = 2, PATH = 3, FIELD = 4, COBBLE = 5,
            STONE_FLOOR = 6, MOSS_STONE = 7, GRASS = 8, FLOWERS = 9, DARK_GRASS = 10;
    // ---- objets (une case)
    public static final byte NONE = 0, TREE = 1, PINE = 2, ROCK = 3, BUSH = 4, FENCE = 5, BARREL = 6,
            CRATE = 7, LAMP = 8, HAY = 9, RUIN_WALL = 10, PILLAR = 11, STUMP = 12, FLOWER_POT = 13;

    // ---- grands éléments
    public static final int B_APOTHECARY = 0, B_INN = 1, B_FORGE = 2, B_BANK = 3, B_FOUNTAIN = 4, B_STALL = 5;

    public static final class Building {
        public final int type, x, y, w, h;  // emprise au sol (cases pleines)
        public final float drawW, drawH;     // taille du dessin (en cases), aligné en bas à gauche
        Building(int type, int x, int y, int w, int h, float drawW, float drawH) {
            this.type = type; this.x = x; this.y = y; this.w = w; this.h = h; this.drawW = drawW; this.drawH = drawH;
        }
        /** Point devant la porte (pour entrer dans la boutique). */
        public float doorX() { return x + w / 2f; }
        public float doorY() { return y - 0.6f; }
        public boolean isShop() { return type <= B_BANK; }
    }

    public static final int VILLAGE_CX = SIZE / 2, VILLAGE_CY = SIZE / 2, VILLAGE_HALF = 15;
    public static final float VILLAGE_X = VILLAGE_CX + 0.5f, VILLAGE_Y = VILLAGE_CY + 0.5f;
    public static final float FOUNTAIN_X = VILLAGE_CX + 0.5f, FOUNTAIN_Y = VILLAGE_CY;
    public static final float RUINS_X = 108.5f, RUINS_Y = 108.5f;

    public final long seed;
    private final byte[] ground = new byte[SIZE * SIZE];
    private final byte[] obj = new byte[SIZE * SIZE];
    private final boolean[] solid = new boolean[SIZE * SIZE];
    public final List<Building> buildings;

    public WorldMap(long seed) {
        this.seed = seed;
        List<Building> b = new ArrayList<>();
        generate(b);
        buildings = Collections.unmodifiableList(b);
        for (int i = 0; i < SIZE * SIZE; i++) solid[i] = ground[i] == WATER || isSolidObj(obj[i]);
        for (Building bd : buildings)
            for (int y = bd.y; y < bd.y + bd.h; y++)
                for (int x = bd.x; x < bd.x + bd.w; x++) if (in(x, y)) solid[y * SIZE + x] = true;
    }

    private static boolean in(int x, int y) { return x >= 0 && y >= 0 && x < SIZE && y < SIZE; }

    public byte get(int x, int y) { return in(x, y) ? ground[y * SIZE + x] : WATER; }
    public byte obj(int x, int y) { return in(x, y) ? obj[y * SIZE + x] : NONE; }
    public boolean solid(int x, int y) { return !in(x, y) || solid[y * SIZE + x]; }

    private void setG(int x, int y, byte t) { if (in(x, y)) ground[y * SIZE + x] = t; }
    private void setO(int x, int y, byte t) { if (in(x, y)) obj[y * SIZE + x] = t; }

    public static boolean isSolidObj(byte o) {
        return o == TREE || o == PINE || o == ROCK || o == BUSH || o == FENCE || o == BARREL || o == CRATE
                || o == LAMP || o == HAY || o == RUIN_WALL || o == PILLAR || o == STUMP || o == FLOWER_POT;
    }

    public boolean isSolidAt(float x, float y) {
        return solid((int) Math.floor(x), (int) Math.floor(y));
    }

    /** Une entité de rayon r peut-elle se tenir en (x,y) ? */
    public boolean canStand(float x, float y, float r) {
        return !isSolidAt(x - r, y - r) && !isSolidAt(x + r, y - r)
                && !isSolidAt(x - r, y + r) && !isSolidAt(x + r, y + r);
    }

    /** Zone de sécurité du village (palissade comprise) : aucun monstre n'y entre. */
    public static boolean inSafeZone(float x, float y, float margin) {
        float h = VILLAGE_HALF + 0.5f + margin;
        return Math.abs(x - VILLAGE_X) <= h && Math.abs(y - VILLAGE_Y) <= h;
    }

    public Building shopAt(float px, float py, float radius) {
        for (Building b : buildings) {
            if (!b.isShop()) continue;
            float dx = px - b.doorX(), dy = py - b.doorY();
            if (dx * dx + dy * dy <= radius * radius) return b;
        }
        return null;
    }

    public Building building(int type) {
        for (Building b : buildings) if (b.type == type) return b;
        return null;
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

    private void generate(List<Building> out) {
        Random rnd = new Random(seed);
        float[] h1 = lattice(rnd, 8), h2 = lattice(rnd, 16), h3 = lattice(rnd, 32);
        float[] f1 = lattice(rnd, 12), f2 = lattice(rnd, 24);
        float[] d1 = lattice(rnd, 20), d2 = lattice(rnd, 40);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float h = sample(h1, 8, x, y) * 0.6f + sample(h2, 16, x, y) * 0.3f + sample(h3, 32, x, y) * 0.1f;
                float dx = (x - SIZE / 2f) / (SIZE / 2f), dy = (y - SIZE / 2f) / (SIZE / 2f);
                float edge = Math.max(Math.abs(dx), Math.abs(dy));
                h -= Math.max(0f, edge - 0.72f) * 2.2f;
                float forest = sample(f1, 12, x, y) * 0.7f + sample(f2, 24, x, y) * 0.3f;
                float dirt = sample(d1, 20, x, y) * 0.65f + sample(d2, 40, x, y) * 0.35f;

                byte g; byte o = NONE;
                float r = rnd.nextFloat(), r2 = rnd.nextFloat();
                if (h < 0.30f) g = WATER;
                else if (h < 0.35f) g = SAND;
                else if (h > 0.70f) {
                    g = r2 < 0.5f ? STONE_FLOOR : (dirt > 0.5f ? DIRT : MOSS_STONE);
                    if (r < 0.22f) o = ROCK; else if (r < 0.27f) o = PINE;
                } else if (forest > 0.58f) {
                    g = DARK_GRASS;
                    if (r < 0.40f) o = (h > 0.55f || r2 < 0.35f) ? PINE : TREE;
                    else if (r < 0.46f) o = BUSH;
                    else if (r < 0.48f) o = STUMP;
                } else {
                    g = dirt > 0.66f ? DIRT : (r2 < 0.035f ? FLOWERS : GRASS);
                    if (r < 0.018f) o = TREE; else if (r < 0.03f) o = BUSH; else if (r < 0.036f) o = ROCK;
                }
                setG(x, y, g);
                setO(x, y, o);
            }
        }

        buildRuins(rnd);
        // routes de terre depuis les portes du village
        int cx = VILLAGE_CX, cy = VILLAGE_CY, e = VILLAGE_HALF;
        carvePath(cx, cy + e + 1, 64, 120, rnd);
        carvePath(cx, cy - e - 1, 64, 10, rnd);
        carvePath(cx - e - 1, cy, 10, 64, rnd);
        carvePath(cx + e + 1, cy, (int) RUINS_X, (int) RUINS_Y - 7, rnd);
        carvePath(cx + e + 1, cy, 116, 30, rnd);
        carvePath(cx, cy - e - 1, 30, 108, rnd);
        buildVillage(rnd, out);
    }

    private void buildVillage(Random rnd, List<Building> out) {
        int cx = VILLAGE_CX, cy = VILLAGE_CY, e = VILLAGE_HALF;
        // terrain dégagé
        for (int y = cy - e - 2; y <= cy + e + 2; y++)
            for (int x = cx - e - 2; x <= cx + e + 2; x++) {
                setO(x, y, NONE);
                byte g = get(x, y);
                int d = Math.max(Math.abs(x - cx), Math.abs(y - cy));
                if (d <= e || g == WATER || g == SAND || g == STONE_FLOOR || g == MOSS_STONE || g == DARK_GRASS)
                    setG(x, y, rnd.nextFloat() < 0.04f ? FLOWERS : GRASS);
            }
        // palissade avec 4 portes
        for (int i = -e; i <= e; i++) {
            boolean gate = Math.abs(i) <= 1;
            if (!gate) { setO(cx + i, cy + e, FENCE); setO(cx + i, cy - e, FENCE); setO(cx - e, cy + i, FENCE); setO(cx + e, cy + i, FENCE); }
        }
        // grandes allées pavées (largeur 3) + place centrale
        for (int i = -e; i <= e; i++)
            for (int k = -1; k <= 1; k++) { setG(cx + i, cy + k, COBBLE); setG(cx + k, cy + i, COBBLE); }
        for (int y = cy - 4; y <= cy + 4; y++)
            for (int x = cx - 4; x <= cx + 4; x++)
                if (Math.abs(x - cx) + Math.abs(y - cy) <= 6) setG(x, y, COBBLE);
        // routes de terre à l'extérieur des portes
        for (int k = -1; k <= 1; k++)
            for (int i = e + 1; i <= e + 3; i++) {
                setG(cx + i, cy + k, PATH); setG(cx - i, cy + k, PATH); setG(cx + k, cy + i, PATH); setG(cx + k, cy - i, PATH);
                setO(cx + i, cy + k, NONE); setO(cx - i, cy + k, NONE); setO(cx + k, cy + i, NONE); setO(cx + k, cy - i, NONE);
            }

        // fontaine (3x2) au centre
        out.add(new Building(B_FOUNTAIN, cx - 1, cy - 1, 3, 2, 3f, 3f));

        // 4 boutiques (6x4), portes vers le bas
        int[][] shops = {{B_APOTHECARY, cx - 12, cy + 5}, {B_INN, cx + 6, cy + 5}, {B_FORGE, cx - 12, cy - 10}, {B_BANK, cx + 6, cy - 10}};
        for (int[] s : shops) {
            Building b = new Building(s[0], s[1], s[2], 6, 4, 6f, 7f);
            out.add(b);
            // sol pavé devant la porte et allée vers la grande rue
            int dx = (int) b.doorX();
            for (int x = dx - 2; x <= dx + 1; x++) for (int y = b.y - 2; y < b.y; y++) setG(x, y, COBBLE);
            int ry = b.y - 2;
            if (b.y > cy) { // au nord : allée vers le bas jusqu'à la rue horizontale
                for (int y = cy + 2; y < b.y; y++) { setG(dx - 1, y, COBBLE); setG(dx, y, COBBLE); }
            } else {        // au sud : allée horizontale jusqu'à la rue verticale
                int step = Integer.signum(cx - dx);
                for (int x = dx; x != cx; x += step) { setG(x, ry, COBBLE); setG(x, ry + 1, COBBLE); }
            }
            // décor autour
            setO(b.x - 1, b.y, BARREL);
            setO(b.x + b.w, b.y, CRATE);
            setO(b.x + b.w, b.y + 1, BARREL);
            setO(dx - 3, b.y - 1, FLOWER_POT);
            setO(dx + 2, b.y - 1, FLOWER_POT);
        }

        // étals du marché
        out.add(new Building(B_STALL, cx - 6, cy - 6, 3, 1, 3f, 2.6f));
        out.add(new Building(B_STALL, cx + 4, cy - 6, 3, 1, 3f, 2.6f));

        // lampadaires de la place
        setO(cx - 3, cy + 3, LAMP); setO(cx + 3, cy + 3, LAMP); setO(cx - 3, cy - 3, LAMP); setO(cx + 3, cy - 3, LAMP);

        // champs cultivés au nord, avec meules de foin
        for (int y = cy + 11; y <= cy + 13; y++) {
            for (int x = cx - 5; x <= cx - 3; x++) setG(x, y, FIELD);
            for (int x = cx + 3; x <= cx + 5; x++) setG(x, y, FIELD);
        }
        setO(cx - 6, cy + 13, HAY); setO(cx + 6, cy + 13, HAY);

        // arbres dans les coins du village
        int[][] trees = {{-13, 13}, {-11, 13}, {13, 13}, {11, 13}, {-13, -13}, {13, -13}, {-13, -2}, {13, 3}, {-9, 14}, {9, 14}};
        for (int[] t : trees) setO(cx + t[0], cy + t[1], rnd.nextBoolean() ? TREE : PINE);
        // massifs fleuris
        for (int i = 0; i < 26; i++) {
            int x = cx - e + 1 + rnd.nextInt(2 * e - 1), y = cy - e + 1 + rnd.nextInt(2 * e - 1);
            if (get(x, y) == GRASS && obj(x, y) == NONE) setG(x, y, FLOWERS);
        }
        for (int i = 0; i < 8; i++) {
            int x = cx - e + 1 + rnd.nextInt(2 * e - 1), y = cy - e + 1 + rnd.nextInt(2 * e - 1);
            if (get(x, y) == GRASS && obj(x, y) == NONE && Math.abs(x - cx) > 2 && Math.abs(y - cy) > 2) setO(x, y, BUSH);
        }
        // pas d'objet sur les emprises des bâtiments ni devant les portes
        for (Building b : out) {
            for (int y = b.y - 2; y < b.y + b.h + 3; y++)
                for (int x = b.x; x < b.x + b.w; x++) if (obj(x, y) != FENCE) setO(x, y, NONE);
        }
    }

    private void buildRuins(Random rnd) {
        int cx = (int) RUINS_X, cy = (int) RUINS_Y;
        for (int y = cy - 8; y <= cy + 8; y++)
            for (int x = cx - 8; x <= cx + 8; x++) {
                int d = Math.max(Math.abs(x - cx), Math.abs(y - cy));
                setO(x, y, NONE);
                setG(x, y, rnd.nextFloat() < 0.35f ? MOSS_STONE : STONE_FLOOR);
                if (d == 6 && rnd.nextFloat() < 0.65f && Math.abs(x - cx) > 1 && Math.abs(y - cy) > 1) setO(x, y, RUIN_WALL);
                if (d == 3 && Math.abs(x - cx) == 3 && Math.abs(y - cy) == 3) setO(x, y, PILLAR);
            }
    }

    private void carvePath(int x0, int y0, int x1, int y1, Random rnd) {
        int x = x0, y = y0, guard = 0;
        while ((x != x1 || y != y1) && guard++ < 1000) {
            if (x != x1 && (y == y1 || rnd.nextBoolean())) x += Integer.signum(x1 - x);
            else y += Integer.signum(y1 - y);
            byte t = get(x, y);
            byte o = obj(x, y);
            if (o != NONE && o != FENCE && o != RUIN_WALL && o != PILLAR) setO(x, y, NONE);
            if (t == WATER) setG(x, y, SAND);
            else if (t != STONE_FLOOR && t != MOSS_STONE && t != COBBLE) setG(x, y, PATH);
        }
    }

    /** Distance au centre du village. */
    public static float distToVillage(float x, float y) {
        float dx = x - VILLAGE_X, dy = y - VILLAGE_Y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
