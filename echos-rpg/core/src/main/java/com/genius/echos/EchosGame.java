package com.genius.echos;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.genius.echos.gfx.Art;
import com.genius.echos.screens.MenuScreen;
import com.genius.echos.ui.Ui;

/** Échos d'Aube — RPG en vue de dessus, jouable hors ligne et en multijoueur sur réseau local. */
public class EchosGame extends Game {
    public final Platform platform;
    public Art art;
    public Ui ui;
    public Save save;

    public EchosGame(Platform platform) {
        this.platform = platform == null ? Platform.NONE : platform;
    }

    @Override
    public void create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        art = new Art();
        ui = new Ui(art);
        save = new Save();
        setScreen(new MenuScreen(this, null));
    }

    @Override
    public void resize(int width, int height) {
        ui.resize(width, height);
        super.resize(width, height);
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        ui.dispose();
        art.dispose();
    }
}
