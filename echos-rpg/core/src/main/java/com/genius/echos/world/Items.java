package com.genius.echos.world;

import java.util.ArrayList;
import java.util.List;

/** Catalogue des objets et des boutiques. Partagé serveur/client. */
public final class Items {
    private Items() {}

    public static final int T_HP = 0, T_MP = 1, T_FOOD = 2, T_WEAPON = 3, T_ARMOR = 4;

    public static final class Def {
        public final int id, type, cls, tier, level, price;
        public final String name;
        /** Potion : soin. Nourriture : PV/s. Arme : attaque. Armure : défense. */
        public final int value;
        /** Nourriture : PM/s. Armure : PV bonus. */
        public final int value2;
        /** Nourriture : durée (s). */
        public final int duration;

        Def(int id, int type, int cls, int tier, int level, int price, String name, int value, int value2, int duration) {
            this.id = id; this.type = type; this.cls = cls; this.tier = tier; this.level = level;
            this.price = price; this.name = name; this.value = value; this.value2 = value2; this.duration = duration;
        }

        public boolean stackable() { return type == T_HP || type == T_MP || type == T_FOOD; }
        public int sellPrice() { return Math.max(1, price * 2 / 5); }

        public String describe() {
            switch (type) {
                case T_HP: return "Rend " + value + " PV";
                case T_MP: return "Rend " + value + " PM";
                case T_FOOD: return "+" + value + " PV/s" + (value2 > 0 ? ", +" + value2 + " PM/s" : "") + " pendant " + duration + " s";
                case T_WEAPON: return "+" + value + " attaque";
                default: return "+" + value + " défense, +" + value2 + " PV max";
            }
        }
    }

    private static final Def[] BY_ID = new Def[300];
    private static final List<Def> ALL = new ArrayList<>();

    private static void add(Def d) { BY_ID[d.id] = d; ALL.add(d); }

    public static final int SMALL_HP = 1, BIG_HP = 2, SMALL_MP = 3, BIG_MP = 4, BREAD = 10, STEW = 11, PIE = 12;

    static final String[][] WEAPON_NAMES = {
            {"Épée courte", "Épée d'acier", "Lame runique", "Épée du crépuscule", "Lame d'aube"},
            {"Arc court", "Arc long", "Arc composite", "Arc sylvestre", "Arc d'aube"},
            {"Bâton de chêne", "Bâton d'apprenti", "Sceptre runique", "Bâton astral", "Sceptre d'aube"}};
    static final String[][] ARMOR_NAMES = {
            {"Plastron de cuir", "Cotte de mailles", "Armure d'acier", "Armure runique", "Armure d'aube"},
            {"Tunique de cuir", "Veste cloutée", "Cuir renforcé", "Tenue sylvestre", "Tenue d'aube"},
            {"Robe de lin", "Robe d'apprenti", "Robe runique", "Robe astrale", "Robe d'aube"}};
    static final int[] TIER_LEVEL = {1, 5, 10, 18, 28};
    static final int[] WEAPON_ATK = {4, 9, 16, 26, 40};
    static final int[] WEAPON_PRICE = {30, 120, 350, 900, 2200};
    static final int[] ARMOR_DEF = {2, 5, 9, 14, 21};
    static final int[] ARMOR_HP = {10, 30, 60, 100, 160};
    static final int[] ARMOR_PRICE = {40, 150, 420, 1000, 2500};

    static {
        add(new Def(SMALL_HP, T_HP, -1, 1, 1, 12, "Petite potion de vie", 60, 0, 0));
        add(new Def(BIG_HP, T_HP, -1, 2, 10, 40, "Grande potion de vie", 220, 0, 0));
        add(new Def(SMALL_MP, T_MP, -1, 1, 1, 12, "Petite potion de mana", 40, 0, 0));
        add(new Def(BIG_MP, T_MP, -1, 2, 10, 40, "Grande potion de mana", 140, 0, 0));
        add(new Def(BREAD, T_FOOD, -1, 1, 1, 8, "Pain de campagne", 3, 0, 30));
        add(new Def(STEW, T_FOOD, -1, 2, 1, 25, "Ragoût de l'auberge", 6, 3, 45));
        add(new Def(PIE, T_FOOD, -1, 3, 12, 60, "Tarte aux baies", 11, 6, 60));
        for (int c = 0; c < 3; c++)
            for (int t = 0; t < 5; t++) {
                add(new Def(weaponId(c, t + 1), T_WEAPON, c, t + 1, TIER_LEVEL[t], WEAPON_PRICE[t], WEAPON_NAMES[c][t], WEAPON_ATK[t], 0, 0));
                add(new Def(armorId(c, t + 1), T_ARMOR, c, t + 1, TIER_LEVEL[t], ARMOR_PRICE[t], ARMOR_NAMES[c][t], ARMOR_DEF[t], ARMOR_HP[t], 0));
            }
    }

    public static int weaponId(int cls, int tier) { return 100 + cls * 10 + tier; }
    public static int armorId(int cls, int tier) { return 200 + cls * 10 + tier; }

    public static Def get(int id) { return id > 0 && id < BY_ID.length ? BY_ID[id] : null; }
    public static int tier(int id) { Def d = get(id); return d == null ? 0 : d.tier; }
    public static int weaponAtk(int id) { Def d = get(id); return d != null && d.type == T_WEAPON ? d.value : 0; }
    public static int armorDef(int id) { Def d = get(id); return d != null && d.type == T_ARMOR ? d.value : 0; }
    public static int armorHp(int id) { Def d = get(id); return d != null && d.type == T_ARMOR ? d.value2 : 0; }

    // ---------------------------------------------------------------- boutiques
    public static final int SHOP_APOTHECARY = 0, SHOP_INN = 1, SHOP_FORGE = 2, SHOP_BANK = 3;
    public static final String[] SHOP_NAMES = {"Apothicaire", "Auberge", "Forge", "Dépôt"};
    public static final String[] KEEPER_NAMES = {"Lila", "Bram", "Hugo", "Nora"};
    public static final String[] SHOP_GREETING = {
            "Potions de vie et de mana, fraîchement préparées !",
            "Un bon repas te requinque pendant tes aventures.",
            "Armes et armures adaptées à ta classe.",
            "Je garde tes objets en sécurité. Touche un objet pour le déplacer."};

    /** Objets vendus par une boutique (la forge dépend de la classe). */
    public static List<Def> catalog(int shop, int cls) {
        List<Def> out = new ArrayList<>();
        switch (shop) {
            case SHOP_APOTHECARY: out.add(get(SMALL_HP)); out.add(get(BIG_HP)); out.add(get(SMALL_MP)); out.add(get(BIG_MP)); break;
            case SHOP_INN: out.add(get(BREAD)); out.add(get(STEW)); out.add(get(PIE)); break;
            case SHOP_FORGE:
                for (int t = 1; t <= 5; t++) { out.add(get(weaponId(cls, t))); out.add(get(armorId(cls, t))); }
                break;
            default: break;
        }
        return out;
    }

    public static boolean sells(int shop, int cls, int itemId) {
        for (Def d : catalog(shop, cls)) if (d.id == itemId) return true;
        return false;
    }
}
