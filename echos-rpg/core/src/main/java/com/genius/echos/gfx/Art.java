package com.genius.echos.gfx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.genius.echos.world.Items;
import com.genius.echos.world.WorldMap;

/**
 * Tous les graphismes du jeu sont dessinés par le code (art original, 32 px par case),
 * regroupés dans une grande texture pour un rendu rapide.
 */
public final class Art implements Disposable {
    public static final int T = 32;
    private static final int OUT = 0x231a1c; // couleur de contour

    private final Canvas cv = new Canvas(2048, 2048);
    public final Texture texture = new Texture(2048, 2048, Pixmap.Format.RGBA8888);
    private final Texture shapes;

    // ---- sol
    public final TextureRegion[][] ground = new TextureRegion[11][];
    public final TextureRegion[] water = new TextureRegion[3];
    /** Bordures irrégulières, indexées par type de sol « dominant ». */
    public final TextureRegion[] edge = new TextureRegion[11], corner = new TextureRegion[11];
    // ---- objets
    public TextureRegion[] tree = new TextureRegion[2], pine = new TextureRegion[2], bush = new TextureRegion[2], rock = new TextureRegion[2];
    public TextureRegion stump, barrel, crate, lamp, hay, flowerPot, ruinWall, pillar, post, railH, railV, fountain, stall;
    public final TextureRegion[] buildings = new TextureRegion[4];
    // ---- personnages : [direction 0 bas,1 haut,2 côté][image 0..2]
    public final TextureRegion[][] body = new TextureRegion[3][3], top = new TextureRegion[3][3], bottom = new TextureRegion[3][3];
    public final TextureRegion[][] hair = new TextureRegion[4][3];
    public final TextureRegion[][] weapon = new TextureRegion[3][3];
    // ---- monstres [type][image]
    public final TextureRegion[][] mobs = new TextureRegion[4][2];
    // ---- icônes
    private final TextureRegion[] itemIcons = new TextureRegion[300];
    public final TextureRegion[] weaponIcon = new TextureRegion[3], armorIcon = new TextureRegion[3];
    public TextureRegion coin, bag, chatIcon, skillIcon;
    public TextureRegion white, circle, ring, shadow;

    // Palettes d'avatar
    public static final int[] SKIN = {0xf6d2b0, 0xe2ae84, 0xb47a52, 0x7a4e32};
    public static final int[] HAIR = {0x3a2618, 0x8a5a2a, 0xe8c060, 0xc8502a, 0x2a2a3a, 0xe8e8f0};
    public static final String[] HAIR_STYLE_NAMES = {"Courte", "En pics", "Longue", "Chignon"};
    public static final String[] SKIN_NAMES = {"Claire", "Dorée", "Mate", "Foncée"};
    public static final String[] HAIR_COLOR_NAMES = {"Brun", "Châtain", "Blond", "Roux", "Noir", "Argent"};
    public static final int[] CLASS_COLOR = {0xb8403a, 0x4a9a4a, 0x4a5ac8};
    public static final int[] TIER_TINT = {0xc8b8a0, 0xa07850, 0xd8dce6, 0xf0c850, 0x7fd0ff, 0xff9a50};

    public Art() {
        paintGround();
        paintProps();
        paintBuildings();
        paintCharacters();
        paintMonsters();
        paintIcons();
        int[] wp = cv.alloc(4, 4);
        cv.rect(wp[0], wp[1], 4, 4, 0xffffff);
        white = reg(wp[0] + 1, wp[1] + 1, 2, 2);
        int[] sp = cv.alloc(64, 24);
        for (int j = 0; j < 24; j++)
            for (int i = 0; i < 64; i++) {
                float dx = (i - 31.5f) / 32f, dy = (j - 11.5f) / 12f, d = dx * dx + dy * dy;
                if (d < 1f) cv.setA(sp[0] + i, sp[1] + j, 0, 0.42f * (1f - d));
            }
        shadow = reg(sp[0], sp[1], 64, 24);

        Pixmap pm = new Pixmap(2048, 2048, Pixmap.Format.RGBA8888);
        cv.copyTo(pm);
        texture.draw(pm, 0, 0);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();

        Pixmap s = new Pixmap(256, 128, Pixmap.Format.RGBA8888);
        s.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                float d = (float) Math.sqrt((x - 63.5f) * (x - 63.5f) + (y - 63.5f) * (y - 63.5f));
                float aFill = Math.max(0f, Math.min(1f, 62f - d));
                float aRing = aFill * Math.max(0f, Math.min(1f, d - 51f));
                s.setColor(1, 1, 1, aFill); s.drawPixel(x, y);
                s.setColor(1, 1, 1, aRing); s.drawPixel(128 + x, y);
            }
        shapes = new Texture(s, true);
        shapes.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        s.dispose();
        circle = new TextureRegion(shapes, 0, 0, 128, 128);
        ring = new TextureRegion(shapes, 128, 0, 128, 128);
    }

    private TextureRegion reg(int x, int y, int w, int h) { return new TextureRegion(texture, x, y, w, h); }

    public TextureRegion icon(int itemId) {
        Items.Def d = Items.get(itemId);
        if (d == null) return white;
        if (d.type == Items.T_WEAPON) return weaponIcon[d.cls];
        if (d.type == Items.T_ARMOR) return armorIcon[d.cls];
        return itemIcons[itemId];
    }

    /** Teinte à appliquer à une icône (équipements : selon le palier). */
    public static int iconTint(int itemId) {
        Items.Def d = Items.get(itemId);
        if (d == null || (d.type != Items.T_WEAPON && d.type != Items.T_ARMOR)) return 0xffffff;
        return TIER_TINT[d.tier];
    }

    // ================================================================ sol

    /** Priorité d'affichage : le sol le plus « haut » déborde sur ses voisins. */
    public static int priority(byte t) {
        switch (t) {
            case WorldMap.WATER: return 0;
            case WorldMap.SAND: return 1;
            case WorldMap.STONE_FLOOR: case WorldMap.MOSS_STONE: return 2;
            case WorldMap.COBBLE: case WorldMap.FIELD: return 3;
            case WorldMap.DIRT: case WorldMap.PATH: return 4;
            case WorldMap.GRASS: case WorldMap.FLOWERS: return 6;
            default: return 7;
        }
    }

    /** Type de bordure utilisé pour un sol dominant (ou -1 si aucune). */
    public static int edgeKind(byte t) {
        switch (t) {
            case WorldMap.SAND: return WorldMap.SAND;
            case WorldMap.DIRT: case WorldMap.PATH: return WorldMap.DIRT;
            case WorldMap.GRASS: case WorldMap.FLOWERS: return WorldMap.GRASS;
            case WorldMap.DARK_GRASS: return WorldMap.DARK_GRASS;
            default: return -1;
        }
    }

    private interface TilePainter { void paint(int x, int y, int variant); }

    private TextureRegion[] tiles(int n, TilePainter p) {
        TextureRegion[] out = new TextureRegion[n];
        for (int v = 0; v < n; v++) {
            int[] a = cv.alloc(T, T);
            p.paint(a[0], a[1], v);
            cv.extrude(a[0], a[1], T, T);
            out[v] = reg(a[0], a[1], T, T);
        }
        return out;
    }

    private void noiseFill(int x, int y, int c1, int c2, int c3, int period, float contrast) {
        float[] g = cv.tileNoise(period), g2 = cv.tileNoise(period * 2);
        for (int j = 0; j < T; j++)
            for (int i = 0; i < T; i++) {
                float n = Canvas.noiseAt(g, period, i / (float) T, j / (float) T) * 0.65f
                        + Canvas.noiseAt(g2, period * 2, i / (float) T, j / (float) T) * 0.35f;
                n = 0.5f + (n - 0.5f) * contrast;
                int c = n < 0.5f ? Canvas.mix(c2, c1, n * 2) : Canvas.mix(c1, c3, (n - 0.5f) * 2);
                cv.set(x + i, y + j, c);
            }
    }

    private void grassPaint(int x, int y, int base, int dark, int light, int blade, int bladeDark, int blades) {
        noiseFill(x, y, base, dark, light, 4, 1.4f);
        for (int k = 0; k < blades; k++) {
            int bx = cv.rnd.nextInt(T), by = cv.rnd.nextInt(T);
            int len = 2 + cv.rnd.nextInt(3);
            boolean lightBlade = cv.rnd.nextFloat() < 0.6f;
            for (int l = 0; l < len; l++) {
                int lx = (bx + (l == len - 1 && cv.rnd.nextBoolean() ? 1 : 0)) % T, ly = Math.floorMod(by - l, T);
                cv.set(x + lx, y + ly, l == len - 1 ? (lightBlade ? blade : light) : (lightBlade ? light : bladeDark));
            }
            cv.set(x + bx, y + (by + 1) % T, bladeDark);
        }
    }

    private void pebbles(int x, int y, int n, int col) {
        for (int k = 0; k < n; k++) {
            int px = 2 + cv.rnd.nextInt(T - 4), py = 2 + cv.rnd.nextInt(T - 4);
            int r = 1 + cv.rnd.nextInt(2);
            cv.ellipse(x + px, y + py, r + 0.5f, r * 0.8f + 0.3f, col);
            cv.set(x + px - 1, y + py - 1, Canvas.mix(col, 0xffffff, 0.4f));
            cv.set(x + px, y + py + r, Canvas.shade(col, 0.6f));
        }
    }

    /** Pavés : cellules de Voronoï raccordables, ombrées. */
    private void stonesPaint(int x, int y, int n, int stone, int gap, float mossChance) {
        float[][] pts = new float[n][2];
        for (int k = 0; k < n; k++) { pts[k][0] = cv.rnd.nextFloat() * T; pts[k][1] = cv.rnd.nextFloat() * T; }
        float[] tint = new float[n];
        for (int k = 0; k < n; k++) tint[k] = 0.88f + cv.rnd.nextFloat() * 0.22f;
        float[] g = cv.tileNoise(4);
        for (int j = 0; j < T; j++)
            for (int i = 0; i < T; i++) {
                float d1 = 1e9f, d2 = 1e9f; int best = 0; float bdx = 0, bdy = 0;
                for (int k = 0; k < n; k++)
                    for (int oy = -1; oy <= 1; oy++)
                        for (int ox = -1; ox <= 1; ox++) {
                            float dx = i + 0.5f - (pts[k][0] + ox * T), dy = j + 0.5f - (pts[k][1] + oy * T);
                            float d = dx * dx + dy * dy;
                            if (d < d1) { d2 = d1; d1 = d; best = k; bdx = dx; bdy = dy; } else if (d < d2) d2 = d;
                        }
                float edgeDist = (float) Math.sqrt(d2) - (float) Math.sqrt(d1);
                int c;
                if (edgeDist < 1.6f) {
                    c = Canvas.noiseAt(g, 4, i / (float) T, j / (float) T) < mossChance ? 0x5e7e34 : gap;
                } else {
                    float lightK = (-bdx - bdy) / 14f;
                    c = Canvas.shade(stone, tint[best] * (1f + lightK * 0.18f));
                    if (edgeDist < 2.6f) c = (bdx + bdy < 0) ? Canvas.mix(c, 0xffffff, 0.18f) : Canvas.shade(c, 0.82f);
                }
                cv.set(x + i, y + j, c);
            }
    }

    /** Dalles rectangulaires fissurées. */
    private void flagPaint(int x, int y, int stone, int gap, boolean moss) {
        int[] rows = {0, 11, 22, 32};
        for (int r = 0; r < 3; r++) {
            int off = cv.rnd.nextInt(T);
            int cx = 0;
            while (cx < T) {
                int w = 9 + cv.rnd.nextInt(8);
                float t = 0.86f + cv.rnd.nextFloat() * 0.2f;
                for (int j = rows[r]; j < rows[r + 1]; j++)
                    for (int i = cx; i < Math.min(T, cx + w); i++) {
                        int gx = (i + off) % T;
                        boolean border = j == rows[r] || i == cx;
                        int c = border ? gap : Canvas.shade(stone, t);
                        if (!border && (j == rows[r] + 1 || i == cx + 1)) c = Canvas.mix(c, 0xffffff, 0.15f);
                        if (!border && (j == rows[r + 1] - 1)) c = Canvas.shade(c, 0.85f);
                        cv.set(x + gx, y + j, c);
                    }
                cx += w;
            }
        }
        for (int k = 0; k < 3; k++) { // fissures
            float cx = cv.rnd.nextFloat() * T, cy = cv.rnd.nextFloat() * T;
            for (int l = 0; l < 7; l++) {
                cv.set(x + Math.floorMod((int) cx, T), y + Math.floorMod((int) cy, T), Canvas.shade(gap, 1.1f));
                cx += cv.rnd.nextFloat() * 2 - 0.5f; cy += cv.rnd.nextFloat() * 2 - 1;
            }
        }
        if (moss) {
            float[] g = cv.tileNoise(4);
            for (int j = 0; j < T; j++)
                for (int i = 0; i < T; i++) {
                    float n = Canvas.noiseAt(g, 4, i / (float) T, j / (float) T);
                    if (n > 0.6f) cv.set(x + i, y + j, n > 0.72f ? 0x8fbe46 : 0x6f9c36);
                    else if (n > 0.57f) cv.set(x + i, y + j, 0x4e6e2a);
                }
        }
    }

    private void paintGround() {
        ground[WorldMap.GRASS] = tiles(4, (x, y, v) -> grassPaint(x, y, 0x7aa83a, 0x638f2e, 0x92c04a, 0xb0d85e, 0x4f7e28, 34));
        ground[WorldMap.FLOWERS] = tiles(2, (x, y, v) -> {
            grassPaint(x, y, 0x7aa83a, 0x638f2e, 0x92c04a, 0xb0d85e, 0x4f7e28, 26);
            int[] cols = {0xffffff, 0xf4d24a, 0xf08aa8, 0xb48af0, 0xff7a4a};
            for (int k = 0; k < 5; k++) {
                int fx = 3 + cv.rnd.nextInt(26), fy = 3 + cv.rnd.nextInt(26), c = cols[cv.rnd.nextInt(cols.length)];
                cv.set(x + fx - 1, y + fy, c); cv.set(x + fx + 1, y + fy, c); cv.set(x + fx, y + fy - 1, c); cv.set(x + fx, y + fy + 1, c);
                cv.set(x + fx, y + fy, 0xffe070);
                cv.set(x + fx, y + fy + 2, 0x3e6a22);
            }
        });
        ground[WorldMap.DARK_GRASS] = tiles(3, (x, y, v) -> {
            grassPaint(x, y, 0x5a8a30, 0x467426, 0x6c9c3a, 0x8cbc48, 0x365e1e, 30);
            if (v == 2) for (int k = 0; k < 4; k++) cv.ellipse(x + 4 + cv.rnd.nextInt(24), y + 4 + cv.rnd.nextInt(24), 2, 1.2f, 0x8a6a3a);
        });
        ground[WorldMap.DIRT] = tiles(2, (x, y, v) -> { noiseFill(x, y, 0xb4895a, 0x9b7247, 0xc9a06c, 4, 1.3f); pebbles(x, y, 4, 0xa89c8a); });
        ground[WorldMap.PATH] = tiles(2, (x, y, v) -> {
            noiseFill(x, y, 0xc4a06c, 0xab8656, 0xd6b47e, 4, 1.1f);
            pebbles(x, y, 6, 0xb0a490);
        });
        ground[WorldMap.SAND] = tiles(2, (x, y, v) -> {
            noiseFill(x, y, 0xe6cf92, 0xd8bc7a, 0xf2e0aa, 4, 1.2f);
            for (int k = 0; k < 14; k++) cv.set(x + cv.rnd.nextInt(T), y + cv.rnd.nextInt(T), cv.rnd.nextBoolean() ? 0xc8ab6a : 0xfff0c4);
        });
        ground[WorldMap.COBBLE] = tiles(3, (x, y, v) -> stonesPaint(x, y, 8, 0xbcb19a, 0x7a6e58, 0.18f));
        ground[WorldMap.STONE_FLOOR] = tiles(2, (x, y, v) -> flagPaint(x, y, 0xa8987a, 0x5e5240, false));
        ground[WorldMap.MOSS_STONE] = tiles(2, (x, y, v) -> flagPaint(x, y, 0xa09276, 0x5a4e3c, true));
        ground[WorldMap.FIELD] = tiles(1, (x, y, v) -> {
            noiseFill(x, y, 0x7a5434, 0x62422a, 0x8e6640, 4, 1.0f);
            for (int r = 0; r < 4; r++) {
                int ry = r * 8 + 4;
                cv.rect(x, y + ry + 2, T, 1, 0x553822);
                for (int k = 0; k < 4; k++) {
                    int px = x + k * 8 + 4;
                    cv.ellipse(px - 2, y + ry, 2.5f, 1.6f, 0x4f8f2e);
                    cv.ellipse(px + 2, y + ry, 2.5f, 1.6f, 0x5fa83a);
                    cv.ellipse(px, y + ry - 2, 2f, 2f, 0x76c046);
                    cv.set(px, y + ry - 3, 0xa8e070);
                }
            }
        });
        for (int f = 0; f < 3; f++) {
            final int ff = f;
            int[] a = cv.alloc(T, T);
            noiseFill(a[0], a[1], 0x3c8cc8, 0x2f74ae, 0x4c9ed6, 4, 1.0f);
            for (int k = 0; k < 6; k++) {
                int wy = (k * 6 + ff * 2) % T, wx = (k * 11 + ff * 3) % T;
                for (int l = 0; l < 6; l++) cv.set(a[0] + (wx + l) % T, a[1] + wy, l == 0 || l == 5 ? 0x6ab4e4 : 0x9ad6f6);
            }
            cv.extrude(a[0], a[1], T, T);
            water[f] = reg(a[0], a[1], T, T);
        }
        ground[WorldMap.WATER] = new TextureRegion[]{water[0]};

        // Bordures irrégulières : bord (vers le haut) et coin (haut-gauche)
        int[][] kinds = {
                {WorldMap.GRASS, 0x7aa83a, 0x638f2e, 0x92c04a},
                {WorldMap.DARK_GRASS, 0x5a8a30, 0x467426, 0x6c9c3a},
                {WorldMap.DIRT, 0xb4895a, 0x9b7247, 0xc9a06c},
                {WorldMap.SAND, 0xe6cf92, 0xd8bc7a, 0xf2e0aa}};
        for (int[] k : kinds) {
            boolean grassy = k[0] == WorldMap.GRASS || k[0] == WorldMap.DARK_GRASS;
            float[] g = cv.tileNoise(8);
            int[] a = cv.alloc(T, T);
            fringe(a[0], a[1], k, grassy, g, false);
            edge[k[0]] = reg(a[0], a[1], T, T);
            int[] b = cv.alloc(T, T);
            fringe(b[0], b[1], k, grassy, g, true);
            corner[k[0]] = reg(b[0], b[1], T, T);
        }
    }

    private void fringe(int x, int y, int[] k, boolean grassy, float[] g, boolean isCorner) {
        int lip = Canvas.shade(k[2], 0.75f);
        for (int j = 0; j < T; j++)
            for (int i = 0; i < T; i++) {
                float n = Canvas.noiseAt(g, 8, i / (float) T, j / 64f);
                float limit, dist;
                if (isCorner) {
                    float d = (float) Math.sqrt(i * i + j * j);
                    limit = 8f + n * 7f; dist = limit - d;
                } else {
                    limit = 6f + n * 8f; dist = limit - j;
                }
                if (dist >= 0) {
                    int c = Canvas.mix(k[2], k[3], cv.rnd.nextFloat() * 0.5f);
                    if (cv.rnd.nextFloat() < 0.3f) c = k[1];
                    if (dist < 1.6f) c = grassy ? Canvas.shade(k[1], 0.85f) : lip;
                    if (grassy && dist < 3 && dist >= 1.6f && cv.rnd.nextFloat() < 0.25f) c = 0xa8d058;
                    cv.set(x + i, y + j, c);
                } else if (dist > -2.5f) {
                    cv.setA(x + i, y + j, 0x1a1408, 0.28f * (1f + dist / 2.5f)); // ombre portée
                }
            }
        if (grassy) { // touffes qui dépassent
            for (int t = 0; t < 6; t++) {
                int bx = cv.rnd.nextInt(T);
                float n = Canvas.noiseAt(g, 8, bx / (float) T, 0);
                int by = isCorner ? (int) Math.max(0, Math.sqrt(Math.max(0, (8 + n * 7) * (8 + n * 7) - bx * bx))) : (int) (6 + n * 8);
                if (isCorner && bx > 14) continue;
                cv.set(x + bx, y + by, k[3]);
                cv.set(x + bx, y + by + 1, k[1]);
            }
        }
    }

    // ================================================================ objets du décor

    private int[] spr(int w, int h) { return cv.alloc(w, h); }

    private void paintProps() {
        int[][] treeCols = {{0x86b83c, 0x5e8c2a}, {0x5e9e3a, 0x3e7a2a}};
        for (int v = 0; v < 2; v++) {
            int[] a = spr(72, 88); int x = a[0], y = a[1];
            int trunk = 0x6b4428;
            cv.rect(x + 31, y + 56, 11, 28, trunk);
            cv.rect(x + 31, y + 56, 3, 28, 0x87583a);
            cv.rect(x + 39, y + 56, 3, 28, 0x4e3020);
            cv.tri(x + 26, y + 86, x + 32, y + 74, x + 34, y + 86, trunk);
            cv.tri(x + 47, y + 86, x + 40, y + 74, x + 39, y + 86, 0x5a3a24);
            int base = treeCols[v][0];
            float[][] balls = {{36, 40, 19}, {20, 42, 15}, {52, 42, 15}, {27, 26, 16}, {45, 26, 16}, {36, 16, 15}, {28, 52, 13}, {45, 52, 13}};
            for (float[] b : balls) cv.ball(x + b[0], y + b[1], b[2], base);
            for (int k = 0; k < 70; k++) {
                int px = x + 6 + cv.rnd.nextInt(60), py = y + 4 + cv.rnd.nextInt(58);
                if (Canvas.alpha(cv.get(px, py)) == 0) continue;
                boolean hi = (px - x) + (py - y) < 70;
                int c = cv.get(px, py) & 0xffffff;
                cv.set(px, py, hi ? Canvas.mix(c, 0xffffff, 0.25f) : Canvas.shade(c, 0.8f));
                cv.set(px + 1, py, Canvas.shade(c, 0.85f));
            }
            cv.outline(x, y, 72, 88, OUT);
            tree[v] = reg(x, y, 72, 88);
        }
        for (int v = 0; v < 2; v++) {
            int[] a = spr(56, 88); int x = a[0], y = a[1];
            cv.rect(x + 24, y + 68, 8, 18, 0x5e3c24);
            cv.rect(x + 24, y + 68, 2, 18, 0x7a5032);
            int base = v == 0 ? 0x3e8a42 : 0x2f7440;
            float[][] tiers = {{28, 2, 14, 30}, {28, 14, 21, 50}, {28, 30, 27, 72}};
            for (float[] t : tiers) {
                cv.tri(x + t[0], y + t[1], x + t[0] - t[2], y + t[3], x + t[0], y + t[3], Canvas.mix(base, 0xffffff, 0.15f));
                cv.tri(x + t[0], y + t[1], x + t[0], y + t[3], x + t[0] + t[2], y + t[3], Canvas.shade(base, 0.8f));
                for (int s = 0; s < 6; s++) {
                    float sx = x + t[0] - t[2] + s * (t[2] * 2 / 6f) + 2;
                    cv.tri(sx - 3, y + t[3] - 2, sx + 3, y + t[3] - 2, sx, y + t[3] + 3, s < 3 ? Canvas.mix(base, 0xffffff, 0.1f) : Canvas.shade(base, 0.75f));
                }
                for (int k = 0; k < 10; k++) {
                    int px = x + (int) (t[0] - t[2] / 2 + cv.rnd.nextFloat() * t[2]), py = y + (int) (t[1] + 6 + cv.rnd.nextFloat() * (t[3] - t[1] - 8));
                    if (Canvas.alpha(cv.get(px, py)) > 0) cv.set(px, py, 0x6cb860);
                }
            }
            cv.outline(x, y, 56, 88, 0x162a16);
            pine[v] = reg(x, y, 56, 88);
        }
        for (int v = 0; v < 2; v++) {
            int[] a = spr(38, 32); int x = a[0], y = a[1];
            int base = v == 0 ? 0x6aa83a : 0x58983a;
            cv.ball(x + 12, y + 19, 10, base); cv.ball(x + 26, y + 19, 10, base); cv.ball(x + 19, y + 12, 10, base);
            if (v == 1) for (int k = 0; k < 6; k++) cv.ellipse(x + 8 + cv.rnd.nextInt(22), y + 8 + cv.rnd.nextInt(16), 1.6f, 1.6f, 0xd83a4a);
            cv.outline(x, y, 38, 32, OUT);
            bush[v] = reg(x, y, 38, 32);
        }
        for (int v = 0; v < 2; v++) {
            int[] a = spr(36, 28); int x = a[0], y = a[1];
            int base = v == 0 ? 0x9a978e : 0x8c8478;
            cv.ball(x + 15, y + 16, 11, base); cv.ball(x + 24, y + 18, 8, Canvas.shade(base, 0.95f));
            for (int i = 0; i < 36; i++) for (int j = 22; j < 28; j++) if (Canvas.alpha(cv.get(x + i, y + j)) > 0) cv.set(x + i, y + j, Canvas.shade(base, 0.6f));
            if (v == 1) for (int k = 0; k < 8; k++) cv.set(x + 8 + cv.rnd.nextInt(14), y + 6 + cv.rnd.nextInt(5), 0x7aa83a);
            cv.outline(x, y, 36, 28, OUT);
            rock[v] = reg(x, y, 36, 28);
        }
        { int[] a = spr(30, 24); int x = a[0], y = a[1];
            cv.rect(x + 4, y + 8, 22, 12, 0x6b4428); cv.rect(x + 4, y + 8, 5, 12, 0x86583a);
            cv.ellipse(x + 15, y + 8, 11, 5, 0xc89a64); cv.ellipse(x + 15, y + 8, 7, 3, 0xb0824e); cv.ellipse(x + 15, y + 8, 3, 1.4f, 0xc89a64);
            cv.ellipse(x + 15, y + 20, 11, 3, 0x6b4428);
            cv.outline(x, y, 30, 24, OUT); stump = reg(x, y, 30, 24); }
        { int[] a = spr(28, 34); int x = a[0], y = a[1];
            for (int j = 4; j < 32; j++) {
                float bulge = 1.5f * (float) Math.sin(Math.PI * (j - 4) / 28f);
                for (int i = (int) (4 - bulge); i < 24 + bulge; i++) {
                    float l = (i - 4) / 20f;
                    cv.set(x + i, y + j, Canvas.shade(0x9a6436, 1.15f - l * 0.45f));
                }
            }
            for (int s = 0; s < 4; s++) cv.rect(x + 8 + s * 4, y + 6, 1, 25, 0x7a4c28);
            cv.rect(x + 2, y + 10, 24, 2, 0x50505a); cv.rect(x + 2, y + 25, 24, 2, 0x50505a);
            cv.ellipse(x + 14, y + 5, 10, 3.5f, 0xb07a48); cv.ellipse(x + 14, y + 5, 7, 2, 0x8a5a30);
            cv.outline(x, y, 28, 34, OUT); barrel = reg(x, y, 28, 34); }
        { int[] a = spr(30, 30); int x = a[0], y = a[1];
            cv.rect(x + 2, y + 2, 26, 8, 0xc8945a); cv.rect(x + 2, y + 10, 26, 18, 0xa8743e);
            for (int s = 0; s < 4; s++) cv.rect(x + 2, y + 13 + s * 4, 26, 1, 0x7e5428);
            cv.thick(x + 4, y + 12, x + 26, y + 26, 1, 0x7e5428); cv.thick(x + 26, y + 12, x + 4, y + 26, 1, 0x7e5428);
            cv.rect(x + 2, y + 10, 26, 1, 0x6a4420);
            cv.outline(x, y, 30, 30, OUT); crate = reg(x, y, 30, 30); }
        { int[] a = spr(20, 68); int x = a[0], y = a[1];
            cv.rect(x + 9, y + 16, 3, 48, 0x3a3a46); cv.rect(x + 9, y + 16, 1, 48, 0x5a5a6a);
            cv.rect(x + 5, y + 62, 11, 5, 0x3a3a46);
            cv.rect(x + 4, y + 4, 13, 14, 0x2e2e38);
            cv.rect(x + 6, y + 6, 9, 10, 0xffd870); cv.rect(x + 7, y + 7, 4, 4, 0xfff4c0);
            cv.tri(x + 2, y + 5, x + 10.5f, y - 1 + 1, x + 19, y + 5, 0x2e2e38);
            cv.outline(x, y, 20, 68, OUT); lamp = reg(x, y, 20, 68); }
        { int[] a = spr(42, 34); int x = a[0], y = a[1];
            cv.ball(x + 21, y + 20, 15, 0xe0bc58); cv.ball(x + 12, y + 24, 9, 0xd4ae4c); cv.ball(x + 31, y + 24, 9, 0xd0a848);
            for (int k = 0; k < 40; k++) { int px = x + 6 + cv.rnd.nextInt(30), py = y + 6 + cv.rnd.nextInt(24); if (Canvas.alpha(cv.get(px, py)) > 0) cv.line(px, py, px + 2, py - 1, cv.rnd.nextBoolean() ? 0xf4dc84 : 0xb08a30); }
            cv.outline(x, y, 42, 34, OUT); hay = reg(x, y, 42, 34); }
        { int[] a = spr(26, 28); int x = a[0], y = a[1];
            cv.tri(x + 5, y + 14, x + 21, y + 14, x + 18, y + 27, 0xc0643a); cv.tri(x + 5, y + 14, x + 18, y + 27, x + 8, y + 27, 0xa8522e);
            cv.rect(x + 4, y + 13, 18, 3, 0xd8784a);
            int[] fc = {0xe83a5a, 0xf4d24a, 0xffffff};
            for (int k = 0; k < 5; k++) { int fx = x + 6 + k * 3, fy = y + 5 + (k % 2) * 3; cv.ball(fx, fy, 2.4f, fc[k % 3]); cv.set(fx, fy + 3, 0x3e7a2a); cv.set(fx, fy + 4, 0x3e7a2a); }
            cv.ellipse(x + 13, y + 12, 9, 2, 0x4e8a2e);
            cv.outline(x, y, 26, 28, OUT); flowerPot = reg(x, y, 26, 28); }
        { int[] a = spr(34, 46); int x = a[0], y = a[1];
            for (int r = 0; r < 4; r++) for (int s = 0; s < 3; s++) {
                int bx = x + 1 + s * 11 - (r % 2) * 5, by = y + 10 + r * 9;
                cv.rect(Math.max(x + 1, bx), by, Math.min(10, x + 33 - Math.max(x + 1, bx)), 8, Canvas.shade(0x9a9080, 0.85f + cv.rnd.nextFloat() * 0.25f));
            }
            cv.rect(x + 1, y + 4, 32, 7, 0xb4ac9a);
            for (int k = 0; k < 12; k++) cv.ball(x + 2 + cv.rnd.nextInt(30), y + 4 + cv.rnd.nextInt(5), 2.5f, 0x6f9c36);
            cv.outline(x, y, 34, 46, OUT); ruinWall = reg(x, y, 34, 46); }
        { int[] a = spr(28, 62); int x = a[0], y = a[1];
            cv.rect(x + 2, y + 54, 24, 7, 0x8a8478);
            for (int j = 10; j < 54; j++) for (int i = 6; i < 22; i++) cv.set(x + i, y + j, Canvas.shade(0xb4ac9a, 1.1f - (i - 6) / 16f * 0.4f));
            for (int i = 8; i < 22; i += 4) cv.rect(x + i, y + 12, 1, 42, 0x8a8478);
            cv.tri(x + 6, y + 10, x + 22, y + 10, x + 22, y + 4, 0xb4ac9a);
            for (int k = 0; k < 8; k++) cv.ball(x + 8 + cv.rnd.nextInt(12), y + 40 + cv.rnd.nextInt(14), 2.2f, 0x6f9c36);
            cv.outline(x, y, 28, 62, OUT); pillar = reg(x, y, 28, 62); }
        { int[] a = spr(12, 38); int x = a[0], y = a[1];
            cv.rect(x + 2, y + 6, 8, 30, 0x8a5e36); cv.rect(x + 2, y + 6, 3, 30, 0xa8784a); cv.rect(x + 8, y + 6, 2, 30, 0x664226);
            cv.tri(x + 2, y + 6, x + 6, y + 1, x + 10, y + 6, 0x9a6a40);
            cv.outline(x, y, 12, 38, OUT); post = reg(x, y, 12, 38); }
        { int[] a = spr(34, 22); int x = a[0], y = a[1];
            cv.rect(x + 1, y + 3, 32, 5, 0x9a6a40); cv.rect(x + 1, y + 3, 32, 1, 0xb88858);
            cv.rect(x + 1, y + 13, 32, 5, 0x9a6a40); cv.rect(x + 1, y + 13, 32, 1, 0xb88858);
            cv.outline(x, y, 34, 22, OUT); railH = reg(x, y, 34, 22); }
        { int[] a = spr(10, 34); int x = a[0], y = a[1];
            cv.rect(x + 2, y + 1, 6, 32, 0x9a6a40); cv.rect(x + 2, y + 1, 2, 32, 0xb88858);
            cv.outline(x, y, 10, 34, OUT); railV = reg(x, y, 10, 34); }

        // fontaine
        { int[] a = spr(100, 100); int x = a[0], y = a[1];
            cv.ellipse(x + 50, y + 70, 46, 26, 0x8e887c);
            cv.ellipse(x + 50, y + 67, 46, 25, 0xc4bcaa);
            cv.ellipse(x + 50, y + 68, 38, 19, 0x7e786c);
            for (int j = -18; j <= 18; j++) for (int i = -37; i <= 37; i++) {
                float dx = i / 37f, dy = j / 18f;
                if (dx * dx + dy * dy <= 1) cv.set(x + 50 + i, y + 69 + j, Canvas.mix(0x2f7ab8, 0x58b0e8, 0.5f - dy * 0.5f));
            }
            for (int k = 0; k < 10; k++) { int wx = x + 22 + cv.rnd.nextInt(56), wy = y + 60 + cv.rnd.nextInt(18); cv.rect(wx, wy, 5, 1, 0xb8e8ff); }
            cv.rect(x + 43, y + 30, 14, 40, 0xb4ac9a); cv.rect(x + 43, y + 30, 4, 40, 0xd0c8b4); cv.rect(x + 53, y + 30, 4, 40, 0x948c7c);
            cv.ellipse(x + 50, y + 30, 20, 7, 0xc4bcaa); cv.ellipse(x + 50, y + 29, 15, 4, 0x4c9ed6);
            cv.rect(x + 47, y + 12, 6, 16, 0xb4ac9a); cv.ball(x + 50, y + 11, 5, 0xd0c8b4);
            for (int s = -1; s <= 1; s += 2) for (int j = 0; j < 30; j++) {
                float fx = x + 50 + s * (18 + j * 0.5f), fy = y + 32 + j;
                cv.blend((int) fx, (int) fy, 0xd8f4ff, 0.7f); cv.blend((int) fx + s, (int) fy, 0x9ad6f6, 0.5f);
            }
            cv.outline(x, y, 100, 100, OUT); fountain = reg(x, y, 100, 100); }

        // étal de marché
        { int[] a = spr(98, 86); int x = a[0], y = a[1];
            cv.rect(x + 8, y + 26, 5, 58, 0x7a5030); cv.rect(x + 85, y + 26, 5, 58, 0x7a5030);
            cv.rect(x + 4, y + 52, 90, 26, 0xa8743e); cv.rect(x + 4, y + 52, 90, 4, 0xc8945a);
            for (int s = 0; s < 5; s++) cv.rect(x + 4, y + 58 + s * 4, 90, 1, 0x7e5428);
            int[] fruits = {0xe84a3a, 0xf4a83a, 0x9ad04a, 0xe8d04a, 0xa84ad0};
            for (int k = 0; k < 14; k++) cv.ball(x + 12 + k * 5.5f, y + 50 - (k % 3) * 3, 3.6f, fruits[k % fruits.length]);
            for (int i = 0; i < 98; i++) for (int j = 4; j < 30; j++) {
                float bottomEdge = 26 + 3 * (float) Math.abs(Math.sin(i * Math.PI / 12));
                if (j > bottomEdge) continue;
                boolean red = (i / 12) % 2 == 0;
                int c = red ? 0xd04040 : 0xf4ece0;
                cv.set(x + i, y + j, Canvas.shade(c, 1.05f - j / 60f));
            }
            cv.outline(x, y, 98, 86, OUT); stall = reg(x, y, 98, 86); }
    }

    // ================================================================ bâtiments

    private void paintBuildings() {
        int[] roofs = {0x3f86b0, 0xc8553a, 0x5e6270, 0xc98a3a};
        for (int b = 0; b < 4; b++) {
            int[] a = spr(192, 224);
            house(a[0], a[1], b, roofs[b]);
            buildings[b] = reg(a[0], a[1], 192, 224);
        }
    }

    private void house(int x, int y, int kind, int roofColor) {
        int wallTop = y + 118, bottom = y + 224;
        int wl = x + 10, wr = x + 182;
        boolean stoneWall = kind == 2;
        // mur
        for (int j = wallTop; j < bottom; j++)
            for (int i = wl; i < wr; i++) {
                int c = stoneWall ? 0x9a948a : 0xeadfc4;
                float n = cv.rnd.nextFloat();
                cv.set(i, j, n < 0.12f ? Canvas.shade(c, 0.94f) : c);
            }
        if (stoneWall) {
            for (int r = 0; (wallTop + r * 12) < bottom; r++)
                for (int s = -1; s < 9; s++) {
                    int bx = wl + s * 22 + (r % 2) * 11, by = wallTop + r * 12;
                    for (int j = by; j < Math.min(bottom, by + 11); j++)
                        for (int i = Math.max(wl, bx); i < Math.min(wr, bx + 21); i++)
                            cv.set(i, j, Canvas.shade(0x9a948a, 0.82f + ((bx * 7 + by * 3) & 15) / 60f + (j == by ? 0.12f : 0)));
                }
        } else {
            int beam = 0x6e4b2e;
            for (int[] p : new int[][]{{wl, 6}, {wl + 50, 6}, {wr - 56, 6}, {wr - 6, 6}}) cv.rect(p[0], wallTop, p[1], bottom - wallTop, beam);
            cv.rect(wl, wallTop, wr - wl, 6, beam);
            cv.rect(wl, wallTop + 48, wr - wl, 5, beam);
            cv.thick(wl + 6, wallTop + 48, wl + 50, wallTop + 6, 1.8f, beam);
            cv.thick(wr - 56, wallTop + 6, wr - 6, wallTop + 48, 1.8f, beam);
        }
        // soubassement en pierre
        for (int s = 0; s < 12; s++) {
            int bx = wl + s * 15, by = bottom - 16;
            cv.rect(bx, by, 14, 15, Canvas.shade(0x8a8478, 0.85f + cv.rnd.nextFloat() * 0.3f));
            cv.rect(bx, by, 14, 2, 0xb0a898);
        }
        // fenêtres
        for (int side = 0; side < 2; side++) {
            int fx = side == 0 ? wl + 14 : wr - 46, fy = wallTop + 58;
            int glass = kind == 2 ? 0xffa040 : 0x7fb4d8;
            cv.rect(fx - 8, fy - 2, 6, 30, kind == 1 ? 0x3e7a3a : 0x3a5a8a);
            cv.rect(fx + 34, fy - 2, 6, 30, kind == 1 ? 0x3e7a3a : 0x3a5a8a);
            cv.rect(fx - 2, fy - 3, 36, 32, 0x5a3a22);
            cv.rect(fx, fy, 32, 26, glass);
            cv.thick(fx + 4, fy + 22, fx + 18, fy + 4, 1.2f, Canvas.mix(glass, 0xffffff, 0.5f));
            cv.rect(fx + 15, fy, 2, 26, 0x5a3a22); cv.rect(fx, fy + 12, 32, 2, 0x5a3a22);
            cv.rect(fx - 3, fy + 27, 38, 6, 0x7a4c28);
            for (int k = 0; k < 7; k++) cv.ball(fx + 1 + k * 5, fy + 26, 2.6f, k % 2 == 0 ? 0xe83a4a : 0xf4d24a);
        }
        // porte
        int dc = x + 96, dw = 36, dt = bottom - 58;
        cv.rect(dc - dw / 2 - 4, dt - 2, dw + 8, 58, kind == 2 ? 0x5a5650 : 0x5a3a22);
        cv.ellipse(dc, dt + 2, dw / 2f + 4, 12, kind == 2 ? 0x5a5650 : 0x5a3a22);
        for (int j = dt - 8; j < bottom - 2; j++)
            for (int i = dc - dw / 2; i < dc + dw / 2; i++) {
                float dx = (i + 0.5f - dc) / (dw / 2f), dy = (j - dt) / 10f;
                if (j < dt && dx * dx + dy * dy > 1) continue;
                int c = 0x8a5a32;
                if (((i - dc + 100) % 9) == 0) c = 0x6a4224;
                cv.set(i, j, Canvas.shade(c, 1.05f - (i - dc + dw / 2) / (float) dw * 0.25f));
            }
        if (kind == 3) { cv.rect(dc - dw / 2, dt + 10, dw, 3, 0x40404a); cv.rect(dc - dw / 2, dt + 34, dw, 3, 0x40404a); }
        cv.ball(dc + 11, dt + 28, 2.2f, 0xf0c850);
        cv.rect(dc - 24, bottom - 3, 48, 3, 0xb0a898);
        // ombre sous l'avant-toit
        cv.grade(wl, wallTop, wr - wl, 14, 0.55f, 1f);
        // enseigne
        int sx = dc + 30, sy = wallTop + 16;
        cv.rect(sx, sy, 22, 3, 0x40404a);
        cv.rect(sx + 18, sy + 2, 1, 6, 0x40404a); cv.rect(sx + 4, sy + 2, 1, 6, 0x40404a);
        cv.rect(sx - 2, sy + 8, 28, 24, 0x7a4c28); cv.rect(sx, sy + 10, 24, 20, 0xd8b884);
        signIcon(sx + 12, sy + 20, kind);

        // toit en tuiles écailles
        int rTop = y + 8, rBot = y + 130;
        for (int j = rTop; j < rBot; j++) {
            float t = (j - rTop) / (float) (rBot - rTop);
            float left = x + 30 - 30 * t, right = x + 162 + 30 * t;
            int row = (j - rTop) / 9, lv = (j - rTop) % 9, off = (row % 2) * 7;
            for (int i = (int) left; i < right; i++) {
                int sxl = Math.floorMod(i - x + off, 14);
                float u = (sxl - 7) / 7f;
                float curve = 9 - 3.5f * (float) Math.sqrt(Math.max(0, 1 - u * u));
                float shingle = 0.9f + (((i - x + off) / 14 * 31 + row * 17) % 7) / 40f;
                int c = Canvas.shade(roofColor, shingle * (1.12f - t * 0.3f));
                if (lv >= curve) c = Canvas.shade(roofColor, 0.55f);
                else if (lv >= curve - 1.3f) c = Canvas.shade(c, 0.8f);
                else if (lv < 1.5f) c = Canvas.mix(c, 0xffffff, 0.12f);
                float edgeK = Math.min(i - left, right - i);
                if (edgeK < 4) c = Canvas.shade(c, 0.78f);
                cv.set(i, j, c);
            }
        }
        cv.rect(x + 26, rTop - 4, 140, 7, Canvas.shade(roofColor, 0.5f));
        cv.rect(x + 26, rTop - 4, 140, 2, Canvas.shade(roofColor, 0.75f));
        cv.rect(x + 1, rBot - 4, 190, 5, Canvas.shade(roofColor, 0.45f));
        // lucarne
        if (kind != 2) {
            int lx = x + 96, ly = y + 56;
            cv.rect(lx - 16, ly, 32, 26, 0xeadfc4); cv.rect(lx - 16, ly, 3, 26, 0x6e4b2e); cv.rect(lx + 13, ly, 3, 26, 0x6e4b2e);
            cv.rect(lx - 9, ly + 6, 18, 16, 0x7fb4d8); cv.rect(lx - 1, ly + 6, 2, 16, 0x5a3a22);
            cv.tri(lx - 22, ly + 2, lx, ly - 18, lx + 22, ly + 2, Canvas.shade(roofColor, 0.85f));
            cv.tri(lx, ly - 18, lx + 22, ly + 2, lx, ly + 2, Canvas.shade(roofColor, 0.7f));
        }
        // cheminée
        int cx = x + (kind == 2 ? 132 : 140), cy = y + (kind == 2 ? 6 : 14);
        cv.rect(cx, cy, 20, 34, 0x8a8478); cv.rect(cx, cy, 6, 34, 0xa8a094);
        for (int r = 0; r < 4; r++) cv.rect(cx, cy + 6 + r * 8, 20, 1, 0x6a6458);
        cv.rect(cx - 3, cy - 3, 26, 6, 0x6a6458);
        if (kind == 2) { cv.rect(cx + 4, cy - 2, 12, 3, 0xff8a30); }
        cv.outline(x, y, 192, 224, OUT);
    }

    private void signIcon(int cx, int cy, int kind) {
        switch (kind) {
            case 0: cv.ellipse(cx, cy + 3, 6, 6, 0xe03a3a); cv.rect(cx - 2, cy - 7, 4, 5, 0xd0e0f0); cv.rect(cx - 2, cy - 9, 4, 2, 0x8a5a30); cv.set(cx - 3, cy + 1, 0xffb0b0); break;
            case 1: cv.rect(cx - 6, cy - 5, 10, 12, 0xe8c060); cv.rect(cx - 6, cy - 7, 10, 3, 0xffffff); cv.rect(cx + 4, cy - 2, 3, 6, 0xe8c060); cv.rect(cx - 5, cy - 4, 2, 10, 0xf4dc84); break;
            case 2: cv.rect(cx - 8, cy, 16, 4, 0x40404a); cv.rect(cx - 4, cy + 4, 8, 4, 0x40404a); cv.tri(cx - 11, cy, cx - 8, cy, cx - 8, cy + 3, 0x40404a); cv.thick(cx - 5, cy - 2, cx + 5, cy - 9, 1, 0xa0a8b8); break;
            default: cv.rect(cx - 8, cy - 4, 16, 11, 0x9a6436); cv.rect(cx - 8, cy - 6, 16, 4, 0xb07a48); cv.rect(cx - 8, cy - 1, 16, 2, 0xf0c850); cv.rect(cx - 1, cy - 2, 3, 4, 0xf0c850); break;
        }
    }

    // ================================================================ personnages (couches à teinter)

    private void paintCharacters() {
        for (int d = 0; d < 3; d++)
            for (int f = 0; f < 3; f++) {
                body[d][f] = charLayer(d, f, 0);
                top[d][f] = charLayer(d, f, 1);
                bottom[d][f] = charLayer(d, f, 2);
            }
        for (int s = 0; s < 4; s++) for (int d = 0; d < 3; d++) hair[s][d] = hairLayer(s, d);
        for (int c = 0; c < 3; c++) for (int d = 0; d < 3; d++) weapon[c][d] = weaponLayer(c, d);
    }

    /** layer : 0 peau, 1 haut, 2 bas. Images 32x40, pieds en bas. */
    private TextureRegion charLayer(int dir, int frame, int layer) {
        int[] a = spr(32, 40); int x = a[0], y = a[1];
        int step = frame == 0 ? -1 : frame == 2 ? 1 : 0;
        int skin = 0xf4f4f4, skinS = 0xd2d2d2, cloth = 0xe8e8e8, clothS = 0xb4b4b4, pants = 0xd0d0d0, shoe = 0x4a4a4a;
        if (dir == 2) { // profil (vers la droite)
            if (layer == 2) {
                cv.rect(x + 12, y + 27, 8, 4, pants);
                cv.rect(x + 13 + step * 2, y + 30, 4, 6, pants); cv.rect(x + 13 + step * 2, y + 35, 6, 3, shoe);
                cv.rect(x + 14 - step * 2, y + 30, 4, 6, Canvas.shade(pants, 0.85f)); cv.rect(x + 14 - step * 2, y + 35, 6, 3, shoe);
            } else if (layer == 1) {
                for (int j = 17; j < 29; j++) for (int i = 11; i < 21; i++) cv.set(x + i, y + j, i > 17 ? clothS : cloth);
                cv.rect(x + 11, y + 26, 10, 2, 0x5a5a5a);
                cv.rect(x + 14 - step * 2, y + 18, 5, 9, clothS); cv.rect(x + 14 - step * 2, y + 18, 2, 9, cloth);
            } else {
                cv.ellipse(x + 16, y + 10, 7.5f, 7.5f, skin);
                for (int j = 3; j < 18; j++) for (int i = 8; i < 13; i++) if (Canvas.alpha(cv.get(x + i, y + j)) > 0) cv.set(x + i, y + j, skinS);
                cv.rect(x + 22, y + 10, 2, 2, skin);
                cv.rect(x + 19, y + 9, 2, 3, 0x202020); cv.set(x + 19, y + 9, 0xffffff);
                cv.rect(x + 14, y + 16, 4, 2, skinS);
                cv.ellipse(x + 16.5f - step * 2, y + 28, 2.2f, 2.2f, skin);
            }
        } else {
            boolean down = dir == 0;
            if (layer == 2) {
                cv.rect(x + 10, y + 26, 12, 5, pants);
                cv.rect(x + 11, y + 30 + Math.min(0, step), 4, 5 - Math.min(0, step), pants); cv.rect(x + 10, y + 34 + (step < 0 ? 0 : 1), 5, 3, shoe);
                cv.rect(x + 17, y + 30 + Math.min(0, -step), 4, 5 - Math.min(0, -step), Canvas.shade(pants, 0.9f)); cv.rect(x + 17, y + 34 + (step > 0 ? 0 : 1), 5, 3, shoe);
            } else if (layer == 1) {
                for (int j = 17; j < 29; j++) for (int i = 10; i < 22; i++) cv.set(x + i, y + j, i > 18 ? clothS : cloth);
                if (down) { cv.tri(x + 13, y + 17, x + 19, y + 17, x + 16, y + 21, clothS); }
                cv.rect(x + 10, y + 26, 12, 2, 0x5a5a5a);
                if (down) cv.rect(x + 15, y + 26, 2, 2, 0xa0a0a0);
                cv.rect(x + 7, y + 18 + step, 4, 9, cloth); cv.rect(x + 21, y + 18 - step, 4, 9, clothS);
            } else {
                cv.ellipse(x + 16, y + 10, 8, 7.5f, skin);
                for (int j = 3; j < 18; j++) for (int i = 19; i < 25; i++) if (Canvas.alpha(cv.get(x + i, y + j)) > 0) cv.set(x + i, y + j, skinS);
                cv.rect(x + 14, y + 16, 4, 2, skinS);
                if (down) {
                    cv.rect(x + 12, y + 10, 2, 3, 0x202020); cv.rect(x + 18, y + 10, 2, 3, 0x202020);
                    cv.set(x + 12, y + 10, 0xffffff); cv.set(x + 18, y + 10, 0xffffff);
                    cv.rect(x + 15, y + 14, 2, 1, 0xa08080);
                    cv.set(x + 10, y + 13, 0xe0b0b0); cv.set(x + 21, y + 13, 0xe0b0b0);
                }
                cv.ellipse(x + 9, y + 28 + step, 2.2f, 2.2f, skin);
                cv.ellipse(x + 23, y + 28 - step, 2.2f, 2.2f, skinS);
            }
        }
        cv.outline(x, y, 32, 40, OUT);
        return reg(x, y, 32, 40);
    }

    private TextureRegion hairLayer(int style, int dir) {
        int[] a = spr(32, 40); int x = a[0], y = a[1];
        int h = 0xd8d8d8, hs = 0xa8a8a8, hl = 0xf8f8f8;
        boolean side = dir == 2, back = dir == 1;
        // calotte
        for (int j = 1; j < 12; j++)
            for (int i = 7; i < 26; i++) {
                float dx = (i + 0.5f - 16) / 8.6f, dy = (j + 0.5f - 9) / 8f;
                if (dx * dx + dy * dy > 1) continue;
                boolean fringeZone = !back && j > 6 && (side ? i > 17 : (i > 9 && i < 23));
                if (fringeZone && (side || ((i / 3) % 2 == 0 ? j > 7 : j > 8))) continue;
                cv.set(x + i, y + j, j < 4 ? hl : (i > 19 ? hs : h));
            }
        if (back || side) for (int j = 8; j < 16; j++) for (int i = side ? 8 : 8; i < (side ? 15 : 24); i++) cv.set(x + i, y + j, i > 20 ? hs : h);
        switch (style) {
            case 1: // pics
                for (int s = 0; s < 5; s++) cv.tri(x + 7 + s * 4, y + 4, x + 10 + s * 4, y - 0 + (s % 2), x + 12 + s * 4, y + 5, s % 2 == 0 ? hl : h);
                break;
            case 2: // longs
                if (back) cv.rect(x + 8, y + 8, 16, 16, h);
                else if (side) { cv.rect(x + 8, y + 8, 7, 15, h); cv.rect(x + 8, y + 8, 2, 15, hs); }
                else { cv.rect(x + 7, y + 7, 4, 15, h); cv.rect(x + 21, y + 7, 4, 15, hs); }
                break;
            case 3: // chignon
                if (side) cv.ball(x + 8, y + 5, 4.5f, h); else cv.ball(x + 16, y + 1 + (back ? 3 : 0), 4.5f, h);
                break;
            default: break;
        }
        cv.outline(x, y, 32, 40, OUT);
        return reg(x, y, 32, 40);
    }

    private TextureRegion weaponLayer(int cls, int dir) {
        int[] a = spr(32, 40); int x = a[0], y = a[1];
        int m = 0xf0f0f0, ms = 0xb0b0b0, wood = 0x8a8a8a;
        int hx = dir == 2 ? 17 : 24, hy = 28;
        if (cls == 0) { // épée
            if (dir == 2) { cv.thick(x + hx, y + hy - 2, x + hx + 9, y + hy - 13, 1.2f, m); cv.line(x + hx + 1, y + hy - 3, x + hx + 9, y + hy - 12, ms); cv.thick(x + hx - 2, y + hy, x + hx + 2, y + hy - 4, 1, wood); }
            else { cv.rect(x + hx - 1, y + 10, 3, 16, m); cv.rect(x + hx + 1, y + 10, 1, 16, ms); cv.tri(x + hx - 1, y + 10, x + hx + 2, y + 10, x + hx + 0.5f, y + 6, m); cv.rect(x + hx - 3, y + 26, 7, 2, ms); cv.rect(x + hx - 0, y + 28, 2, 4, wood); }
        } else if (cls == 1) { // arc
            int bx = dir == 2 ? x + hx + 3 : x + hx + 1;
            for (int j = -12; j <= 12; j++) { int off = (int) (4 * Math.cos(j / 12f * Math.PI / 2)); cv.set(bx + off, y + hy - 6 + j, wood); cv.set(bx + off + 1, y + hy - 6 + j, wood); }
            cv.line(bx, y + hy - 18, bx, y + hy + 6, m);
        } else { // bâton
            int sx = x + hx;
            cv.rect(sx, y + 4, 2, 32, wood);
            cv.ball(sx + 1, y + 4, 3.5f, m);
            cv.set(sx, y + 2, 0xffffff);
        }
        cv.outline(x, y, 32, 40, OUT);
        return reg(x, y, 32, 40);
    }

    // ================================================================ monstres

    private void paintMonsters() {
        for (int f = 0; f < 2; f++) {
            int[] a = spr(36, 32); int x = a[0], y = a[1];
            float rx = f == 0 ? 13 : 15, ry = f == 0 ? 12 : 10;
            for (int j = 0; j < 32; j++) for (int i = 0; i < 36; i++) {
                float dx = (i + 0.5f - 18) / rx, dy = (j + 0.5f - (30 - ry)) / ry;
                if (dy > 0.85f) continue;
                float d = dx * dx + dy * dy;
                if (d > 1) continue;
                float l = -dx * 0.5f - dy * 0.6f + (float) Math.sqrt(Math.max(0, 1 - d)) * 0.6f;
                cv.set(x + i, y + j, Canvas.mix(0x2f8a3a, 0x8ae070, Math.max(0, Math.min(1, l))));
            }
            float ey = 30 - ry - 1;
            cv.ellipse(x + 13, y + ey, 3, 3.4f, 0xffffff); cv.ellipse(x + 23, y + ey, 3, 3.4f, 0xffffff);
            cv.ellipse(x + 13.5f, y + ey + 1, 1.5f, 2f, 0x102010); cv.ellipse(x + 23.5f, y + ey + 1, 1.5f, 2f, 0x102010);
            cv.rect(x + 16, y + (int) ey + 5, 4, 1, 0x1a4a1a);
            cv.ellipseA(x + 11, y + 30 - ry * 1.6f, 3, 2, 0xffffff, 0.8f);
            cv.outline(x, y, 36, 32, 0x163a1a);
            mobs[0][f] = reg(x, y, 36, 32);
        }
        for (int f = 0; f < 2; f++) {
            int[] a = spr(44, 34); int x = a[0], y = a[1];
            int g = 0x8a8c96, gd = 0x5e606a, gl = 0xb4b6c0;
            int lo = f == 0 ? 2 : -2;
            cv.rect(x + 9 + lo, y + 22, 4, 10, gd); cv.rect(x + 28 - lo, y + 22, 4, 10, gd);
            cv.thick(x + 6, y + 18, x + 1, y + 10, 2.2f, g);
            cv.ellipse(x + 20, y + 18, 14, 7, g);
            cv.ellipse(x + 20, y + 21, 11, 3.5f, gl);
            cv.rect(x + 12 - lo, y + 22, 4, 10, g); cv.rect(x + 25 + lo, y + 22, 4, 10, g);
            cv.ball(x + 34, y + 12, 7, g);
            cv.ellipse(x + 40, y + 15, 4, 3, gl); cv.set(x + 43, y + 14, 0x202020);
            cv.tri(x + 29, y + 7, x + 31, y - 0 + 1, x + 34, y + 6, gd); cv.tri(x + 34, y + 6, x + 37, y + 1, x + 38, y + 7, g);
            cv.rect(x + 35, y + 10, 2, 2, 0xffd040);
            cv.outline(x, y, 44, 34, OUT);
            mobs[1][f] = reg(x, y, 44, 34);
        }
        for (int f = 0; f < 2; f++) {
            int[] a = spr(34, 42); int x = a[0], y = a[1];
            int b = 0xe8e2d0, bd = 0xa8a290;
            int st = f == 0 ? 1 : -1;
            cv.rect(x + 13 + st, y + 28, 2, 10, b); cv.rect(x + 19 - st, y + 28, 2, 10, b);
            cv.rect(x + 11 + st, y + 37, 5, 2, bd); cv.rect(x + 18 - st, y + 37, 5, 2, bd);
            cv.rect(x + 16, y + 16, 2, 13, b);
            for (int r = 0; r < 4; r++) cv.rect(x + 11, y + 17 + r * 3, 12, 1, r % 2 == 0 ? b : bd);
            cv.rect(x + 12, y + 27, 10, 2, bd);
            cv.thick(x + 10, y + 17, x + 7, y + 27, 1, b); cv.thick(x + 24, y + 17, x + 27, y + 26, 1, b);
            cv.thick(x + 27, y + 26, x + 31, y + 8, 1, 0xa08060); cv.rect(x + 25, y + 24, 5, 2, 0x6a4a2a);
            cv.ball(x + 17, y + 9, 8, b);
            cv.rect(x + 13, y + 15, 8, 3, b);
            cv.ellipse(x + 14, y + 9, 2.2f, 2.6f, 0x101010); cv.ellipse(x + 20, y + 9, 2.2f, 2.6f, 0x101010);
            cv.set(x + 14, y + 9, 0xff4040); cv.set(x + 20, y + 9, 0xff4040);
            for (int k = 0; k < 3; k++) cv.set(x + 15 + k * 2, y + 16, 0x404040);
            cv.outline(x, y, 34, 42, OUT);
            mobs[2][f] = reg(x, y, 34, 42);
        }
        for (int f = 0; f < 2; f++) {
            int[] a = spr(76, 84); int x = a[0], y = a[1];
            int s = 0x8a7a64;
            int o = f == 0 ? 0 : 2;
            cv.ball(x + 26, y + 70, 10, Canvas.shade(s, 0.85f)); cv.ball(x + 50, y + 70, 10, Canvas.shade(s, 0.85f));
            cv.ball(x + 38, y + 46 + o, 22, s);
            cv.ball(x + 12, y + 44 - o, 10, s); cv.ball(x + 64, y + 44 + o, 10, s);
            cv.ball(x + 9, y + 60 - o, 8, Canvas.shade(s, 0.9f)); cv.ball(x + 67, y + 60 + o, 8, Canvas.shade(s, 0.9f));
            cv.ball(x + 38, y + 18 + o, 13, Canvas.mix(s, 0xffffff, 0.08f));
            cv.rect(x + 31, y + 16 + o, 4, 3, 0x6cf0b0); cv.rect(x + 41, y + 16 + o, 4, 3, 0x6cf0b0);
            cv.rect(x + 34, y + 42 + o, 8, 8, 0x55d0a0); cv.rect(x + 36, y + 44 + o, 4, 4, 0xb8ffe0);
            for (int k = 0; k < 14; k++) cv.ball(x + 20 + cv.rnd.nextInt(36), y + 28 + cv.rnd.nextInt(6), 2.6f, 0x6f9c36);
            cv.outline(x, y, 76, 84, OUT);
            mobs[3][f] = reg(x, y, 76, 84);
        }
    }

    // ================================================================ icônes d'objets

    private int[] icon() { return spr(32, 32); }

    private void flask(int x, int y, int liquid, boolean big) {
        float r = big ? 11 : 8.5f;
        cv.ellipse(x + 16, y + 31 - r, r + 1, r + 1, 0xd0e4f0);
        cv.ellipse(x + 16, y + 32 - r, r - 1, r - 1.5f, liquid);
        cv.ellipse(x + 16, y + 33 - r * 0.6f, r - 2, r * 0.5f, Canvas.shade(liquid, 0.75f));
        cv.rect(x + 13, y + 29 - (int) r * 2, 6, 7, 0xd0e4f0);
        cv.rect(x + 12, y + 27 - (int) r * 2, 8, 4, 0x9a6436);
        cv.ellipseA(x + 12, y + 28 - r, 2, 3, 0xffffff, 0.8f);
        if (big) cv.rect(x + 10, y + 19, 12, 4, 0xf0e0b0);
    }

    private void paintIcons() {
        int[] a;
        a = icon(); flask(a[0], a[1], 0xe03a3a, false); cv.outline(a[0], a[1], 32, 32, OUT); itemIcons[Items.SMALL_HP] = reg(a[0], a[1], 32, 32);
        a = icon(); flask(a[0], a[1], 0xe03a3a, true); cv.outline(a[0], a[1], 32, 32, OUT); itemIcons[Items.BIG_HP] = reg(a[0], a[1], 32, 32);
        a = icon(); flask(a[0], a[1], 0x3a6ae0, false); cv.outline(a[0], a[1], 32, 32, OUT); itemIcons[Items.SMALL_MP] = reg(a[0], a[1], 32, 32);
        a = icon(); flask(a[0], a[1], 0x3a6ae0, true); cv.outline(a[0], a[1], 32, 32, OUT); itemIcons[Items.BIG_MP] = reg(a[0], a[1], 32, 32);
        a = icon(); { int x = a[0], y = a[1];
            cv.ball(x + 16, y + 18, 11, 0xd4943e); cv.ellipse(x + 16, y + 24, 12, 5, 0xa86a2a);
            for (int k = 0; k < 3; k++) cv.thick(x + 9 + k * 6, y + 14, x + 12 + k * 6, y + 20, 0.6f, 0xf0c87a);
            cv.outline(x, y, 32, 32, OUT); itemIcons[Items.BREAD] = reg(x, y, 32, 32); }
        a = icon(); { int x = a[0], y = a[1];
            cv.ellipse(x + 16, y + 20, 13, 8, 0x8a5a32); cv.ellipse(x + 16, y + 16, 12, 4, 0xb85a2a);
            for (int k = 0; k < 4; k++) cv.ball(x + 9 + k * 5, y + 16, 1.8f, k % 2 == 0 ? 0xf0a040 : 0x6aa83a);
            for (int k = 0; k < 2; k++) for (int j = 0; j < 8; j++) cv.blend(x + 12 + k * 7 + (j % 3 == 0 ? 1 : 0), y + 9 - j, 0xffffff, 0.6f);
            cv.outline(x, y, 32, 32, OUT); itemIcons[Items.STEW] = reg(x, y, 32, 32); }
        a = icon(); { int x = a[0], y = a[1];
            cv.ellipse(x + 16, y + 20, 14, 8, 0xc8945a); cv.ellipse(x + 16, y + 18, 12, 6, 0x7a2a6a);
            for (int k = 0; k < 4; k++) { cv.rect(x + 7 + k * 5, y + 13, 2, 11, 0xe0b070); }
            cv.rect(x + 5, y + 17, 22, 2, 0xe0b070);
            cv.outline(x, y, 32, 32, OUT); itemIcons[Items.PIE] = reg(x, y, 32, 32); }
        // armes (gris, teintées selon le palier)
        a = icon(); { int x = a[0], y = a[1];
            cv.thick(x + 8, y + 24, x + 25, y + 7, 2, 0xf4f4f4); cv.line(x + 9, y + 24, x + 25, y + 8, 0xb8b8b8);
            cv.thick(x + 5, y + 21, x + 11, y + 27, 1.4f, 0x909090); cv.thick(x + 4, y + 28, x + 8, y + 24, 1.4f, 0x707070);
            cv.outline(x, y, 32, 32, OUT); weaponIcon[0] = reg(x, y, 32, 32); }
        a = icon(); { int x = a[0], y = a[1];
            for (int j = -13; j <= 13; j++) { int off = (int) (8 * Math.cos(j / 13f * Math.PI / 2)); cv.rect(x + 9 + off, y + 16 + j, 3, 1, 0xd0d0d0); }
            cv.line(x + 10, y + 3, x + 10, y + 29, 0xffffff);
            cv.thick(x + 6, y + 16, x + 26, y + 16, 0.6f, 0x909090); cv.tri(x + 26, y + 13, x + 30, y + 16, x + 26, y + 19, 0xf4f4f4);
            cv.outline(x, y, 32, 32, OUT); weaponIcon[1] = reg(x, y, 32, 32); }
        a = icon(); { int x = a[0], y = a[1];
            cv.thick(x + 8, y + 28, x + 22, y + 9, 1.4f, 0xa0a0a0);
            cv.ball(x + 23, y + 7, 5, 0xf4f4f4); cv.set(x + 21, y + 5, 0xffffff);
            cv.outline(x, y, 32, 32, OUT); weaponIcon[2] = reg(x, y, 32, 32); }
        for (int c = 0; c < 3; c++) {
            a = icon(); int x = a[0], y = a[1];
            if (c == 0) { cv.rect(x + 8, y + 8, 16, 18, 0xe8e8e8); cv.ball(x + 8, y + 10, 5, 0xd0d0d0); cv.ball(x + 24, y + 10, 5, 0xd0d0d0); cv.rect(x + 15, y + 9, 2, 16, 0xb0b0b0); cv.rect(x + 9, y + 20, 14, 2, 0xa0a0a0); }
            else if (c == 1) { cv.rect(x + 9, y + 7, 14, 20, 0xd8d8d8); cv.rect(x + 5, y + 8, 5, 10, 0xc8c8c8); cv.rect(x + 22, y + 8, 5, 10, 0xc8c8c8); for (int k = 0; k < 4; k++) cv.set(x + 16, y + 11 + k * 4, 0x707070); cv.tri(x + 12, y + 7, x + 20, y + 7, x + 16, y + 12, 0x909090); }
            else { cv.tri(x + 16, y + 5, x + 4, y + 29, x + 28, y + 29, 0xe0e0e0); cv.tri(x + 16, y + 5, x + 16, y + 29, x + 28, y + 29, 0xc0c0c0); cv.rect(x + 9, y + 18, 14, 2, 0x909090); cv.ball(x + 16, y + 9, 3, 0xf4f4f4); }
            cv.outline(x, y, 32, 32, OUT);
            armorIcon[c] = reg(x, y, 32, 32);
        }
        a = spr(24, 24); { int x = a[0], y = a[1];
            cv.ball(x + 12, y + 12, 10, 0xf0c040); cv.ellipse(x + 12, y + 12, 6, 6, 0xd8a028); cv.rect(x + 11, y + 8, 2, 8, 0xfff0a0);
            cv.outline(x, y, 24, 24, OUT); coin = reg(x, y, 24, 24); }
        a = icon(); { int x = a[0], y = a[1];
            cv.ball(x + 16, y + 19, 11, 0xa8743e); cv.rect(x + 11, y + 5, 10, 6, 0x8a5a30); cv.rect(x + 9, y + 10, 14, 3, 0x6a4224); cv.ball(x + 16, y + 20, 3, 0xf0c850);
            cv.outline(x, y, 32, 32, OUT); bag = reg(x, y, 32, 32); }
        a = icon(); { int x = a[0], y = a[1];
            cv.ellipse(x + 16, y + 14, 13, 9, 0xffffff); cv.tri(x + 8, y + 20, x + 14, y + 22, x + 6, y + 28, 0xffffff);
            for (int k = 0; k < 3; k++) cv.ball(x + 10 + k * 6, y + 14, 1.6f, 0x6a6a8a);
            cv.outline(x, y, 32, 32, OUT); chatIcon = reg(x, y, 32, 32); }
        a = icon(); { int x = a[0], y = a[1];
            for (int k = 0; k < 8; k++) { double an = k * Math.PI / 4; cv.thick(x + 16, y + 16, x + 16 + (float) Math.cos(an) * 13, y + 16 + (float) Math.sin(an) * 13, k % 2 == 0 ? 1.6f : 0.8f, 0xfff0a0); }
            cv.ball(x + 16, y + 16, 6, 0xffd040);
            cv.outline(x, y, 32, 32, OUT); skillIcon = reg(x, y, 32, 32); }
    }

    /** Couleur d'une case pour la mini-carte. */
    public static int miniColor(byte g, byte o) {
        if (o == WorldMap.TREE || o == WorldMap.PINE || o == WorldMap.BUSH) return 0x2f6a2a;
        if (o == WorldMap.ROCK || o == WorldMap.RUIN_WALL || o == WorldMap.PILLAR) return 0x5d6068;
        if (o == WorldMap.FENCE) return 0x8a5e36;
        switch (g) {
            case WorldMap.WATER: return 0x3f7fc8;
            case WorldMap.SAND: return 0xe3cf8f;
            case WorldMap.DIRT: case WorldMap.PATH: return 0xb8955f;
            case WorldMap.COBBLE: return 0xbcb19a;
            case WorldMap.STONE_FLOOR: case WorldMap.MOSS_STONE: return 0x8b8f98;
            case WorldMap.FIELD: return 0x7a5434;
            case WorldMap.DARK_GRASS: return 0x4a7a2a;
            default: return 0x6fa03a;
        }
    }

    @Override public void dispose() { texture.dispose(); shapes.dispose(); }
}
