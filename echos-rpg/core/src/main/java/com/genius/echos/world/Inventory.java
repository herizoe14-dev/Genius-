package com.genius.echos.world;

/** Inventaire à cases (objets empilables jusqu'à 99). */
public final class Inventory {
    public static final int MAX_STACK = 99;
    public final int[] ids, counts;

    public Inventory(int size) {
        ids = new int[size];
        counts = new int[size];
    }

    public int size() { return ids.length; }

    /** Ajoute n objets ; renvoie ce qui n'a pas pu être rangé. */
    public int add(int id, int n) {
        Items.Def d = Items.get(id);
        if (d == null || n <= 0) return n;
        if (d.stackable()) {
            for (int i = 0; i < ids.length && n > 0; i++)
                if (ids[i] == id && counts[i] < MAX_STACK) {
                    int k = Math.min(n, MAX_STACK - counts[i]);
                    counts[i] += k; n -= k;
                }
        }
        for (int i = 0; i < ids.length && n > 0; i++)
            if (ids[i] == 0) {
                int k = d.stackable() ? Math.min(n, MAX_STACK) : 1;
                ids[i] = id; counts[i] = k; n -= k;
            }
        return n;
    }

    /** Peut-on ranger n objets de ce type ? */
    public boolean canAdd(int id, int n) {
        Items.Def d = Items.get(id);
        if (d == null) return false;
        int room = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == 0) room += d.stackable() ? MAX_STACK : 1;
            else if (d.stackable() && ids[i] == id) room += MAX_STACK - counts[i];
        }
        return room >= n;
    }

    public void take(int slot, int n) {
        counts[slot] -= n;
        if (counts[slot] <= 0) { counts[slot] = 0; ids[slot] = 0; }
    }

    public int count(int id) {
        int n = 0;
        for (int i = 0; i < ids.length; i++) if (ids[i] == id) n += counts[i];
        return n;
    }

    public int find(int id) {
        for (int i = 0; i < ids.length; i++) if (ids[i] == id) return i;
        return -1;
    }

    public boolean valid(int slot) { return slot >= 0 && slot < ids.length && ids[slot] != 0; }

    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == 0) continue;
            if (sb.length() > 0) sb.append('.');
            sb.append(i).append(':').append(ids[i]).append(':').append(counts[i]);
        }
        return sb.toString();
    }

    public void decode(String s) {
        java.util.Arrays.fill(ids, 0);
        java.util.Arrays.fill(counts, 0);
        if (s == null || s.isEmpty()) return;
        for (String e : s.split("\\.")) {
            String[] v = e.split(":");
            if (v.length != 3) continue;
            try {
                int slot = Integer.parseInt(v[0]), id = Integer.parseInt(v[1]), n = Integer.parseInt(v[2]);
                Items.Def d = Items.get(id);
                if (slot < 0 || slot >= ids.length || d == null || n <= 0 || ids[slot] != 0) continue;
                ids[slot] = id;
                counts[slot] = d.stackable() ? Math.min(n, MAX_STACK) : 1;
            } catch (NumberFormatException ignored) {}
        }
    }
}
