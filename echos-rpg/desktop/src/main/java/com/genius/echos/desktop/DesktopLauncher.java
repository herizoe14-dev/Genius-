package com.genius.echos.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.genius.echos.EchosGame;
import com.genius.echos.Platform;

/** Version PC (pour tester ou jouer avec des téléphones sur le même réseau). */
public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Échos d'Aube");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new EchosGame(Platform.NONE), config);
    }
}
