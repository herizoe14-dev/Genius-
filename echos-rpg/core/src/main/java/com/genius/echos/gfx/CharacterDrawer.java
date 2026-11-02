package com.genius.echos.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Assemble un personnage à partir des couches teintées :
 * peau, bas, haut (selon classe + armure), cheveux (coiffure + couleur), arme (selon palier).
 */
public final class CharacterDrawer {
    private CharacterDrawer() {}

    public static final int DIR_DOWN = 0, DIR_UP = 1, DIR_LEFT = 2, DIR_RIGHT = 3;
    public static final int LOOKS = 4 * 4 * 6;

    public static int skin(int look) { return Math.floorMod(look, 4); }
    public static int hairStyle(int look) { return Math.floorMod(look / 4, 4); }
    public static int hairColor(int look) { return Math.floorMod(look / 16, 6); }
    public static int look(int skin, int style, int color) { return Math.floorMod(skin, 4) + Math.floorMod(style, 4) * 4 + Math.floorMod(color, 6) * 16; }

    public static int topColor(int cls, int armorTier) {
        int c = Art.CLASS_COLOR[Math.max(0, Math.min(2, cls))];
        switch (armorTier) {
            case 0: return 0xd8d0c0;
            case 1: return Canvas.mix(c, 0x8a6a4a, 0.45f);
            case 2: return c;
            case 3: return Canvas.mix(c, 0xe0e4ec, 0.3f);
            case 4: return Canvas.mix(c, 0x60d0ff, 0.35f);
            default: return Canvas.mix(c, 0xffb040, 0.4f);
        }
    }

    private static void tint(Batch b, int rgb) {
        b.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
    }

    private static void layer(Batch b, TextureRegion r, float x, float y, float w, float h, boolean flip) {
        if (flip) b.draw(r, x + w, y, -w, h);
        else b.draw(r, x, y, w, h);
    }

    /**
     * Dessine le personnage, (x, y) = coin bas-gauche, taille w x h.
     * dir : 0 bas, 1 haut, 2 gauche, 3 droite. frame : 0..2 (1 = immobile).
     */
    public static void draw(Batch b, Art art, int look, int cls, int armorTier, int weaponTier,
                            int dir, int frame, float x, float y, float w, float h) {
        Color saved = b.getColor().cpy();
        int d = dir == DIR_DOWN ? 0 : dir == DIR_UP ? 1 : 2;
        boolean flip = dir == DIR_LEFT;
        frame = Math.max(0, Math.min(2, frame));
        int pants = armorTier >= 3 ? Canvas.mix(0x4a3e34, Art.CLASS_COLOR[Math.max(0, Math.min(2, cls))], 0.25f) : 0x5a4a3a;
        boolean drawWeapon = weaponTier > 0;

        if (d == 1 && drawWeapon) { tint(b, Art.TIER_TINT[Math.min(5, weaponTier)]); layer(b, art.weapon[cls][1], x, y, w, h, flip); }
        tint(b, pants); layer(b, art.bottom[d][frame], x, y, w, h, flip);
        tint(b, topColor(cls, armorTier)); layer(b, art.top[d][frame], x, y, w, h, flip);
        tint(b, Art.SKIN[skin(look)]); layer(b, art.body[d][frame], x, y, w, h, flip);
        tint(b, Art.HAIR[hairColor(look)]); layer(b, art.hair[hairStyle(look)][d], x, y, w, h, flip);
        if (d != 1 && drawWeapon) { tint(b, Art.TIER_TINT[Math.min(5, weaponTier)]); layer(b, art.weapon[cls][d], x, y, w, h, flip); }
        b.setColor(saved);
    }
}
