package com.genius.echos.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.genius.echos.gfx.Art;

/**
 * Interface en coordonnées virtuelles : 720 unités de haut, largeur selon l'écran.
 * Les polices sont générées à la taille réelle de l'écran pour rester nettes.
 */
public final class Ui implements Disposable {
    public static final float H = 720f;
    public final SpriteBatch batch = new SpriteBatch();
    public final OrthographicCamera cam = new OrthographicCamera();
    public final Art art;
    public BitmapFont small, font, big;
    public float w = 1280f;
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 tmp = new Vector3();
    private int fontPx = -1;

    public Ui(Art art) {
        this.art = art;
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        w = H * width / (float) height;
        cam.setToOrtho(false, w, H);
        cam.update();
        if (height != fontPx) buildFonts(height);
    }

    private void buildFonts(int screenH) {
        if (font != null) { small.dispose(); font.dispose(); big.dispose(); }
        fontPx = screenH;
        float k = screenH / H;
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans.ttf"));
        FreeTypeFontGenerator genB = new FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans-Bold.ttf"));
        small = make(gen, 20, k);
        font = make(genB, 27, k);
        big = make(genB, 64, k);
        gen.dispose();
        genB.dispose();
    }

    private BitmapFont make(FreeTypeFontGenerator g, int size, float k) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.max(8, Math.round(size * k));
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "œŒ’…•★⚔✎‹›●✚";
        p.borderWidth = Math.max(1f, 1.6f * k);
        p.borderColor = new Color(0, 0, 0, 0.85f);
        p.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        p.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        BitmapFont f = g.generateFont(p);
        f.getData().setScale(1f / k);
        f.setUseIntegerPositions(false);
        return f;
    }

    /** Convertit des coordonnées écran (pixels, origine en haut) en coordonnées UI. */
    public float uiX(int sx, int sy) { tmp.set(sx, sy, 0); cam.unproject(tmp); return tmp.x; }
    public float uiY(int sx, int sy) { tmp.set(sx, sy, 0); cam.unproject(tmp); return tmp.y; }

    public void begin() { cam.update(); batch.setProjectionMatrix(cam.combined); batch.begin(); }
    public void end() { batch.end(); }

    public void rect(float x, float y, float w, float h, Color c) {
        batch.setColor(c);
        batch.draw(art.white, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        batch.setColor(r, g, b, a);
        batch.draw(art.white, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    public void panel(float x, float y, float w, float h, float alpha) {
        rect(x - 2, y - 2, w + 4, h + 4, 0f, 0f, 0f, alpha * 0.9f);
        rect(x, y, w, h, 0.12f, 0.10f, 0.16f, alpha);
        rect(x, y + h - 3, w, 3, 0.95f, 0.75f, 0.35f, alpha);
    }

    public void bar(float x, float y, float w, float h, float frac, float r, float g, float b) {
        rect(x - 2, y - 2, w + 4, h + 4, 0, 0, 0, 0.8f);
        rect(x, y, w, h, 0.25f, 0.25f, 0.28f, 1f);
        rect(x, y, w * Math.max(0, Math.min(1, frac)), h, r, g, b, 1f);
        rect(x, y + h - h / 3f, w * Math.max(0, Math.min(1, frac)), h / 3f, 1, 1, 1, 0.18f);
    }

    public void circle(float cx, float cy, float r, float red, float g, float b, float a) {
        batch.setColor(red, g, b, a);
        batch.draw(art.circle, cx - r, cy - r, r * 2, r * 2);
        batch.setColor(Color.WHITE);
    }

    public void ringShape(float cx, float cy, float r, float red, float g, float b, float a) {
        batch.setColor(red, g, b, a);
        batch.draw(art.ring, cx - r, cy - r, r * 2, r * 2);
        batch.setColor(Color.WHITE);
    }

    public void sprite(TextureRegion reg, float x, float y, float w, float h) {
        batch.draw(reg, x, y, w, h);
    }

    public void text(BitmapFont f, String s, float x, float y, Color c, int align) {
        f.setColor(c);
        f.draw(batch, s, x, y, 0, align, false);
    }

    public void textCentered(BitmapFont f, String s, float cx, float cy, Color c) {
        layout.setText(f, s);
        f.setColor(c);
        f.draw(batch, s, cx - layout.width / 2f, cy + layout.height / 2f);
    }

    public float textWidth(BitmapFont f, String s) { layout.setText(f, s); return layout.width; }

    /** Bouton rectangulaire ; renvoie vrai si (tx,ty) est dedans. */
    public boolean button(String label, float x, float y, float w, float h, boolean highlight) {
        rect(x - 3, y - 3, w + 6, h + 6, 0, 0, 0, 0.75f);
        if (highlight) rect(x, y, w, h, 0.85f, 0.58f, 0.22f, 1f);
        else rect(x, y, w, h, 0.26f, 0.22f, 0.36f, 1f);
        rect(x, y + h - h * 0.35f, w, h * 0.35f, 1, 1, 1, 0.08f);
        textCentered(font, label, x + w / 2f, y + h / 2f, Color.WHITE);
        return false;
    }

    public static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    public void wrapped(BitmapFont f, String s, float x, float y, float width, Color c) {
        f.setColor(c);
        f.draw(batch, s, x, y, width, Align.left, true);
    }

    @Override public void dispose() {
        batch.dispose();
        if (font != null) { small.dispose(); font.dispose(); big.dispose(); }
    }
}
