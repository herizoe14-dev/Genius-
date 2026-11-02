package com.genius.echos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.genius.echos.world.Items;

/** Sauvegarde locale : une progression (niveau, or, sac, dépôt, équipement) par classe. */
public final class Save {
    private final Preferences prefs = Gdx.app.getPreferences("echos-daube-save");

    public Save() {
        // Migration des sauvegardes de la version 1 (simple compteur de potions)
        for (int c = 0; c < 3; c++) {
            if (prefs.contains("lvl" + c) && !prefs.contains("inv" + c)) {
                int pot = prefs.getInteger("pot" + c, 3);
                prefs.putString("inv" + c, pot > 0 ? "0:" + Items.SMALL_HP + ":" + pot : "");
                prefs.putInteger("wpn" + c, Items.weaponId(c, 1));
            }
        }
        prefs.flush();
    }

    public String name() { return prefs.getString("name", "Voyageur"); }
    public void setName(String n) { prefs.putString("name", n); prefs.flush(); }

    public int cls() { return prefs.getInteger("cls", 0); }
    public void setCls(int c) { prefs.putInteger("cls", c); prefs.flush(); }

    public int look() { return prefs.getInteger("look", 16 + 4); }
    public void setLook(int l) { prefs.putInteger("look", l); prefs.flush(); }

    public int level(int c) { return prefs.getInteger("lvl" + c, 1); }
    public int xp(int c) { return prefs.getInteger("xp" + c, 0); }
    public int gold(int c) { return prefs.getInteger("gold" + c, 0); }
    public String inv(int c) { return prefs.getString("inv" + c, ""); }
    public String bank(int c) { return prefs.getString("bank" + c, ""); }
    public int weapon(int c) { return prefs.getInteger("wpn" + c, 0); }
    public int armor(int c) { return prefs.getInteger("arm" + c, 0); }

    public String lastIp() { return prefs.getString("lastIp", "192.168.1."); }
    public void setLastIp(String ip) { prefs.putString("lastIp", ip); prefs.flush(); }

    public long worldSeed() {
        long s = prefs.getLong("seed", 0);
        if (s == 0) { s = 1 + (long) (Math.random() * 1_000_000_000L); prefs.putLong("seed", s); prefs.flush(); }
        return s;
    }

    public void store(int c, int level, int xp, int gold) {
        prefs.putInteger("lvl" + c, level);
        prefs.putInteger("xp" + c, xp);
        prefs.putInteger("gold" + c, gold);
    }

    public void storeItems(int c, String inv, String bank, int weapon, int armor) {
        prefs.putString("inv" + c, inv);
        prefs.putString("bank" + c, bank);
        prefs.putInteger("wpn" + c, weapon);
        prefs.putInteger("arm" + c, armor);
    }

    public void flush() { prefs.flush(); }
}
