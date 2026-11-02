package com.genius.echos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Sauvegarde locale (une progression par classe). */
public final class Save {
    private final Preferences prefs = Gdx.app.getPreferences("echos-daube-save");

    public String name() { return prefs.getString("name", "Voyageur"); }
    public void setName(String n) { prefs.putString("name", n); prefs.flush(); }

    public int cls() { return prefs.getInteger("cls", 0); }
    public void setCls(int c) { prefs.putInteger("cls", c); prefs.flush(); }

    public int level(int c) { return prefs.getInteger("lvl" + c, 1); }
    public int xp(int c) { return prefs.getInteger("xp" + c, 0); }
    public int gold(int c) { return prefs.getInteger("gold" + c, 0); }
    public int potions(int c) { return prefs.getInteger("pot" + c, 3); }

    public String lastIp() { return prefs.getString("lastIp", "192.168.1."); }
    public void setLastIp(String ip) { prefs.putString("lastIp", ip); prefs.flush(); }

    public long worldSeed() {
        long s = prefs.getLong("seed", 0);
        if (s == 0) { s = 1 + (long) (Math.random() * 1_000_000_000L); prefs.putLong("seed", s); prefs.flush(); }
        return s;
    }

    public void store(int c, int level, int xp, int gold, int potions) {
        prefs.putInteger("lvl" + c, level);
        prefs.putInteger("xp" + c, xp);
        prefs.putInteger("gold" + c, gold);
        prefs.putInteger("pot" + c, potions);
    }

    public void flush() { prefs.flush(); }
}
