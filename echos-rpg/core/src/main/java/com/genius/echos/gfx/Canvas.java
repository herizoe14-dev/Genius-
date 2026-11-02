package com.genius.echos.gfx;

import com.badlogic.gdx.graphics.Pixmap;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Petite toile de dessin en mémoire (ARGB) : bien plus rapide que de dessiner pixel par pixel
 * dans une Pixmap, et permet mélange, ombrage et contours « dessin animé ».
 */
final class Canvas {
    final int w, h;
    final int[] px;
    final Random rnd = new Random(1234);
    // allocation en étagères
    private int shelfX, shelfY, shelfH;

    Canvas(int w, int h) {
        this.w = w; this.h = h;
        px = new int[w * h];
    }

    /** Réserve une zone libre de taille (rw, rh) ; renvoie {x, y}. */
    int[] alloc(int rw, int rh) {
        if (shelfX + rw + 2 > w) { shelfX = 0; shelfY += shelfH + 2; shelfH = 0; }
        int[] r = {shelfX + 1, shelfY + 1};
        shelfX += rw + 2;
        shelfH = Math.max(shelfH, rh);
        if (shelfY + rh + 2 > h) throw new IllegalStateException("Atlas plein");
        return r;
    }

    // ---------------------------------------------------------------- couleurs
    static int rgb(int c) { return 0xff000000 | c; }
    static int alpha(int argb) { return argb >>> 24; }

    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f));
        int g = Math.min(255, (int) (((c >> 8) & 255) * f));
        int b = Math.min(255, (int) ((c & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- pixels
    void set(int x, int y, int rgb) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        px[y * w + x] = 0xff000000 | rgb;
    }

    void setA(int x, int y, int rgb, float a) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        px[y * w + x] = ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xffffff);
    }

    int get(int x, int y) { return (x < 0 || y < 0 || x >= w || y >= h) ? 0 : px[y * w + x]; }

    /** Mélange une couleur par-dessus (opacité a). */
    void blend(int x, int y, int rgb, float a) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0) return;
        int i = y * w + x, d = px[i];
        int da = d >>> 24;
        if (da == 0) { setA(x, y, rgb, a); return; }
        float outA = a + da / 255f * (1 - a);
        int c = mix(d & 0xffffff, rgb, a / outA);
        px[i] = ((int) (outA * 255) << 24) | c;
    }

    void rect(int x, int y, int rw, int rh, int rgb) {
        for (int j = y; j < y + rh; j++) for (int i = x; i < x + rw; i++) set(i, j, rgb);
    }

    void rectA(int x, int y, int rw, int rh, int rgb, float a) {
        for (int j = y; j < y + rh; j++) for (int i = x; i < x + rw; i++) blend(i, j, rgb, a);
    }

    void ellipse(float cx, float cy, float rx, float ry, int rgb) {
        for (int j = (int) (cy - ry - 1); j <= cy + ry + 1; j++)
            for (int i = (int) (cx - rx - 1); i <= cx + rx + 1; i++) {
                float dx = (i + 0.5f - cx) / rx, dy = (j + 0.5f - cy) / ry;
                float d = dx * dx + dy * dy;
                if (d <= 1f) set(i, j, rgb);
            }
    }

    void ellipseA(float cx, float cy, float rx, float ry, int rgb, float a) {
        for (int j = (int) (cy - ry - 1); j <= cy + ry + 1; j++)
            for (int i = (int) (cx - rx - 1); i <= cx + rx + 1; i++) {
                float dx = (i + 0.5f - cx) / rx, dy = (j + 0.5f - cy) / ry;
                if (dx * dx + dy * dy <= 1f) blend(i, j, rgb, a);
            }
    }

    /** Disque ombré, éclairé depuis le haut-gauche (effet volume). */
    void ball(float cx, float cy, float r, int base) {
        int dark = shade(base, 0.62f), light = mix(base, 0xffffff, 0.35f);
        for (int j = (int) (cy - r - 1); j <= cy + r + 1; j++)
            for (int i = (int) (cx - r - 1); i <= cx + r + 1; i++) {
                float dx = (i + 0.5f - cx) / r, dy = (j + 0.5f - cy) / r;
                float d = dx * dx + dy * dy;
                if (d > 1f) continue;
                float nz = (float) Math.sqrt(1 - d);
                float l = (-dx * 0.55f - dy * 0.65f + nz * 0.55f);
                int c = l > 0.5f ? mix(base, light, (l - 0.5f) * 1.6f) : mix(dark, base, Math.max(0, l + 0.2f) / 0.7f);
                set(i, j, c);
            }
    }

    void tri(float x0, float y0, float x1, float y1, float x2, float y2, int rgb) {
        int minX = (int) Math.floor(Math.min(x0, Math.min(x1, x2))), maxX = (int) Math.ceil(Math.max(x0, Math.max(x1, x2)));
        int minY = (int) Math.floor(Math.min(y0, Math.min(y1, y2))), maxY = (int) Math.ceil(Math.max(y0, Math.max(y1, y2)));
        for (int j = minY; j <= maxY; j++)
            for (int i = minX; i <= maxX; i++) {
                float px = i + 0.5f, py = j + 0.5f;
                float d1 = (px - x1) * (y0 - y1) - (x0 - x1) * (py - y1);
                float d2 = (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2);
                float d3 = (px - x0) * (y2 - y0) - (x2 - x0) * (py - y0);
                boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
                if (!(neg && pos)) set(i, j, rgb);
            }
    }

    void line(float x0, float y0, float x1, float y1, int rgb) {
        int n = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        for (int k = 0; k <= n; k++) {
            float t = k / (float) n;
            set((int) (x0 + (x1 - x0) * t), (int) (y0 + (y1 - y0) * t), rgb);
        }
    }

    void thick(float x0, float y0, float x1, float y1, float r, int rgb) {
        int n = (int) (Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2) + 1;
        for (int k = 0; k <= n; k++) {
            float t = k / (float) n;
            ellipse(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r, r, rgb);
        }
    }

    /** Contour sombre autour des pixels opaques d'une zone (style dessin animé). */
    void outline(int x, int y, int rw, int rh, int rgb) {
        boolean[] mark = new boolean[rw * rh];
        for (int j = 0; j < rh; j++)
            for (int i = 0; i < rw; i++) {
                if (alpha(get(x + i, y + j)) > 100) continue;
                boolean near = false;
                for (int k = 0; k < 4 && !near; k++) {
                    int ni = i + (k == 0 ? 1 : k == 1 ? -1 : 0), nj = j + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (ni < 0 || nj < 0 || ni >= rw || nj >= rh) continue;
                    near = alpha(get(x + ni, y + nj)) > 160;
                }
                mark[j * rw + i] = near;
            }
        for (int j = 0; j < rh; j++)
            for (int i = 0; i < rw; i++) if (mark[j * rw + i]) set(x + i, y + j, rgb);
    }

    /** Assombrit/éclaircit la zone selon une fonction verticale (ombre douce). */
    void grade(int x, int y, int rw, int rh, float top, float bottom) {
        for (int j = 0; j < rh; j++) {
            float f = top + (bottom - top) * j / (float) Math.max(1, rh - 1);
            for (int i = 0; i < rw; i++) {
                int c = get(x + i, y + j);
                if (alpha(c) == 0) continue;
                px[(y + j) * w + x + i] = (c & 0xff000000) | shade(c & 0xffffff, f);
            }
        }
    }

    // ---------------------------------------------------------------- bruit raccordable
    /** Bruit de valeur qui se raccorde sur une période p (pour des tuiles sans couture). */
    float[] tileNoise(int period) {
        float[] g = new float[period * period];
        for (int i = 0; i < g.length; i++) g[i] = rnd.nextFloat();
        return g;
    }

    static float noiseAt(float[] g, int period, float u, float v) {
        float fx = u * period, fy = v * period;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
        float tx = fx - ix, ty = fy - iy;
        tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
        int x0 = Math.floorMod(ix, period), y0 = Math.floorMod(iy, period);
        int x1 = (x0 + 1) % period, y1 = (y0 + 1) % period;
        float a = g[y0 * period + x0], b = g[y0 * period + x1], c = g[y1 * period + x0], d = g[y1 * period + x1];
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * ty;
    }

    /** Recopie les pixels du bord dans la marge d'1 px (évite les lignes entre les tuiles). */
    void extrude(int x, int y, int rw, int rh) {
        for (int i = 0; i < rw; i++) { px[(y - 1) * w + x + i] = px[y * w + x + i]; px[(y + rh) * w + x + i] = px[(y + rh - 1) * w + x + i]; }
        for (int j = -1; j <= rh; j++) { px[(y + j) * w + x - 1] = px[(y + j) * w + x]; px[(y + j) * w + x + rw] = px[(y + j) * w + x + rw - 1]; }
    }

    void copyTo(Pixmap pm) {
        ByteBuffer buf = pm.getPixels();
        buf.position(0);
        byte[] row = new byte[w * 4];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = px[j * w + i];
                row[i * 4] = (byte) (c >> 16);
                row[i * 4 + 1] = (byte) (c >> 8);
                row[i * 4 + 2] = (byte) c;
                row[i * 4 + 3] = (byte) (c >>> 24);
            }
            buf.put(row);
        }
        buf.position(0);
    }
}
