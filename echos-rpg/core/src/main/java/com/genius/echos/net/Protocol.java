package com.genius.echos.net;

/**
 * Protocole texte très simple : une ligne par message, champs séparés par des tabulations.
 *
 * Client -> Serveur
 *   HELLO name cls level xp gold potions version
 *   POS x y facing moving
 *   ATK                      attaque la cible la plus proche
 *   POT                      boit une potion
 *   BUY                      achète une potion (près de la fontaine)
 *   CHAT text
 *
 * Serveur -> Client
 *   WELCOME id seed x y
 *   YOU hp maxHp level xp xpNext gold potions
 *   S players monsters       (instantané ; voir GameServer)
 *   FX kind x1 y1 x2 y2
 *   TXT x y color text
 *   TP x y
 *   CHAT name text           (name vide = message système)
 *   KICK reason
 */
public final class Protocol {
    private Protocol() {}

    public static final int VERSION = 1;
    public static final int TCP_PORT = 47500;
    public static final int UDP_PORT = 47501;
    public static final String DISCOVER_ASK = "ECHOS?";
    public static final String DISCOVER_REPLY = "ECHOS!";

    public static final int FX_SLASH = 0, FX_ARROW = 1, FX_FIREBALL = 2, FX_BITE = 3, FX_HEAL = 4, FX_LEVELUP = 5;
    public static final int TXT_DMG_MOB = 0, TXT_DMG_PLAYER = 1, TXT_HEAL = 2, TXT_GOLD = 3, TXT_CRIT = 4, TXT_XP = 5;

    public static String join(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** Retire les caractères réservés au protocole. */
    public static String clean(String s, int maxLen) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < maxLen; i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || c == ',' || c == ';' || c < 32) c = ' ';
            sb.append(c);
        }
        return sb.toString().trim();
    }

    /** Pour le chat : seules les tabulations et retours à la ligne sont réservés. */
    public static String cleanText(String s, int maxLen) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < maxLen; i++) {
            char c = s.charAt(i);
            sb.append(c == '\t' || c == '\n' || c == '\r' || c < 32 ? ' ' : c);
        }
        return sb.toString().trim();
    }

    public static String f(float v) {
        return Float.toString(Math.round(v * 100f) / 100f);
    }

    public static int i(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    public static float fl(String s, float def) {
        try {
            float v = Float.parseFloat(s.trim());
            return Float.isNaN(v) || Float.isInfinite(v) ? def : v;
        } catch (Exception e) { return def; }
    }
}
