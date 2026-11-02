package com.genius.echos.net;

/**
 * Protocole texte : une ligne par message, champs séparés par des tabulations.
 *
 * Client -> Serveur
 *   HELLO name cls level xp gold version look inv bank weapon armor
 *   POS x y dir moving       (dir : 0 bas, 1 haut, 2 gauche, 3 droite)
 *   ATK | SKILL              attaque / compétence spéciale
 *   USE slot                 utiliser / équiper l'objet de la case
 *   QUSE hp|mp               boire la meilleure potion du type
 *   UNEQ w|a                 retirer l'arme / l'armure
 *   BUY itemId | SELL slot   boutiques (près de la porte)
 *   DEP slot | WDR slot      dépôt (banque)
 *   CHAT text
 *
 * Serveur -> Client
 *   WELCOME id seed x y
 *   YOU hp maxHp mp maxMp level xp xpNext gold buffT atk def skillCd
 *   INV inv bank weapon armor
 *   S players monsters
 *   FX kind x1 y1 x2 y2 | TXT x y color text | TP x y
 *   CHAT name text | KICK reason
 */
public final class Protocol {
    private Protocol() {}

    public static final int VERSION = 2;
    public static final int TCP_PORT = 47500;
    public static final int UDP_PORT = 47501;
    public static final String DISCOVER_ASK = "ECHOS?";
    public static final String DISCOVER_REPLY = "ECHOS!";

    public static final int FX_SLASH = 0, FX_ARROW = 1, FX_FIREBALL = 2, FX_BITE = 3, FX_HEAL = 4, FX_LEVELUP = 5, FX_WHIRL = 6, FX_METEOR = 7, FX_MANA = 8;
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
