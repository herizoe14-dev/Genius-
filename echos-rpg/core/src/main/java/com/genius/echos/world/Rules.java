package com.genius.echos.world;

/** Règles du jeu : classes, monstres et progression. Partagées serveur/client. */
public final class Rules {
    private Rules() {}

    // ---------------------------------------------------------------- classes
    public static final int WARRIOR = 0, ARCHER = 1, MAGE = 2;
    public static final String[] CLASS_NAMES = {"Guerrier", "Archère", "Mage"};
    public static final String[] CLASS_DESC = {
            "Robuste, corps à corps",
            "Tire de loin, rapide",
            "Feu de zone, fragile"};

    public static final float[] CLASS_RANGE = {1.7f, 6.5f, 5.5f};
    public static final float[] CLASS_COOLDOWN = {0.50f, 0.65f, 1.0f};
    public static final float[] CLASS_SPEED = {4.3f, 4.8f, 4.1f};
    public static final float[] CLASS_SPLASH = {0f, 0f, 1.6f};

    public static int maxHp(int cls, int level) {
        switch (cls) {
            case WARRIOR: return 120 + (level - 1) * 18;
            case ARCHER: return 90 + (level - 1) * 13;
            default: return 75 + (level - 1) * 11;
        }
    }

    public static int attack(int cls, int level) {
        switch (cls) {
            case WARRIOR: return 10 + (int) ((level - 1) * 3.0f);
            case ARCHER: return 8 + (int) ((level - 1) * 2.6f);
            default: return 13 + (int) ((level - 1) * 3.5f);
        }
    }

    public static int maxMp(int cls, int level) {
        switch (cls) {
            case WARRIOR: return 40 + (level - 1) * 4;
            case ARCHER: return 60 + (level - 1) * 6;
            default: return 90 + (level - 1) * 9;
        }
    }

    // Compétence spéciale (consomme des PM)
    public static final String[] SKILL_NAMES = {"Tourbillon", "Volée", "Météore"};
    public static final int[] SKILL_COST = {20, 25, 30};
    public static final float[] SKILL_COOLDOWN = {4f, 5f, 6f};
    public static final float[] SKILL_MULT = {1.6f, 1.25f, 2.2f};
    public static final float[] SKILL_RADIUS = {2.3f, 7f, 2.8f};

    public static int xpToNext(int level) {
        return (int) (20 * Math.pow(level, 1.5));
    }

    public static final int MAX_LEVEL = 50;

    // ---------------------------------------------------------------- monstres
    public static final int SLIME = 0, WOLF = 1, SKELETON = 2, GOLEM = 3;
    public static final String[] MOB_NAMES = {"Gelée", "Loup", "Squelette", "Golem ancien"};
    public static final int[] MOB_HP = {30, 75, 150, 1100};
    public static final int[] MOB_ATK = {4, 9, 16, 32};
    public static final int[] MOB_XP = {6, 16, 36, 350};
    public static final int[] MOB_GOLD_MIN = {1, 3, 6, 80};
    public static final int[] MOB_GOLD_MAX = {3, 7, 14, 150};
    public static final float[] MOB_SPEED = {1.6f, 3.3f, 2.6f, 1.9f};
    public static final float[] MOB_AGGRO = {4f, 6.5f, 7f, 8f};
    public static final float[] MOB_RADIUS = {0.35f, 0.38f, 0.38f, 0.8f};
    public static final float[] MOB_ATTACK_RANGE = {0.9f, 1.0f, 1.0f, 1.7f};
    public static final float[] MOB_ATTACK_CD = {1.2f, 1.0f, 1.1f, 1.6f};

    public static final float PLAYER_RADIUS = 0.3f;
}
