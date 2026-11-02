package com.genius.echos.net;

import com.genius.echos.world.Items;
import com.genius.echos.world.Rules;
import com.genius.echos.world.WorldMap;
import org.junit.Test;

import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

public class MultiplayerTest {

    private static String hello(String name, int cls) {
        return Protocol.join("HELLO", name, cls, 1, 0, 0, Protocol.VERSION, 5, "", "", 0, 0);
    }

    private static void pump(GameClient c, ClientState s) {
        String l;
        while ((l = c.inbox.poll()) != null) s.process(l);
        s.update(0.05f);
    }

    private static void waitFor(String what, BooleanSupplier cond, Runnable pump) throws InterruptedException {
        long end = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < end) {
            pump.run();
            if (cond.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("Délai dépassé : " + what);
    }

    @Test
    public void mapIsDeterministicAndVillageIsWalkable() {
        WorldMap a = new WorldMap(1234), b = new WorldMap(1234);
        for (int y = 0; y < WorldMap.SIZE; y++)
            for (int x = 0; x < WorldMap.SIZE; x++) assertEquals(a.get(x, y), b.get(x, y));
        assertEquals(7, a.buildings.size()); // fontaine, 4 boutiques, 2 étals
        for (int y = 0; y < WorldMap.SIZE; y++)
            for (int x = 0; x < WorldMap.SIZE; x++) assertEquals(a.obj(x, y), b.obj(x, y));
        // chaque porte de boutique est accessible et reliée à la fontaine
        for (WorldMap.Building bd : a.buildings) if (bd.isShop())
            assertTrue("porte " + bd.type, reachable(a, bd.doorX(), bd.doorY(), WorldMap.FOUNTAIN_X, WorldMap.FOUNTAIN_Y - 2.5f));
        // les 4 portes du village mènent dehors
        assertTrue(reachable(a, WorldMap.FOUNTAIN_X, WorldMap.FOUNTAIN_Y - 2.5f, WorldMap.VILLAGE_X, WorldMap.VILLAGE_Y + 18));
        assertTrue(reachable(a, WorldMap.FOUNTAIN_X, WorldMap.FOUNTAIN_Y - 2.5f, WorldMap.VILLAGE_X + 18, WorldMap.VILLAGE_Y));
    }

    /** Parcours en largeur sur les cases libres. */
    static boolean reachable(WorldMap m, float sx, float sy, float tx, float ty) {
        int n = WorldMap.SIZE;
        boolean[] seen = new boolean[n * n];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        int ax = (int) sx, ay = (int) sy, bx = (int) tx, by = (int) ty;
        q.add(new int[]{ax, ay});
        seen[ay * n + ax] = true;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[0] == bx && c[1] == by) return true;
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] k : d) {
                int x = c[0] + k[0], y = c[1] + k[1];
                if (x < 0 || y < 0 || x >= n || y >= n || seen[y * n + x] || m.solid(x, y)) continue;
                seen[y * n + x] = true;
                q.add(new int[]{x, y});
            }
        }
        return false;
    }

    /** Chemin (centres de cases) par parcours en largeur. */
    static java.util.List<float[]> path(WorldMap m, float sx, float sy, float tx, float ty) {
        int n = WorldMap.SIZE;
        int[] prev = new int[n * n];
        java.util.Arrays.fill(prev, -2);
        int start = (int) sy * n + (int) sx, goal = (int) ty * n + (int) tx;
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        q.add(start); prev[start] = -1;
        while (!q.isEmpty()) {
            int c = q.poll();
            if (c == goal) break;
            int cx = c % n, cy = c / n;
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] k : d) {
                int x = cx + k[0], y = cy + k[1];
                if (x < 0 || y < 0 || x >= n || y >= n || prev[y * n + x] != -2 || m.solid(x, y)) continue;
                prev[y * n + x] = c;
                q.add(y * n + x);
            }
        }
        java.util.LinkedList<float[]> out = new java.util.LinkedList<>();
        out.add(new float[]{tx, ty});
        for (int c = prev[goal] == -2 ? -1 : prev[goal]; c >= 0 && c != start; c = prev[c]) out.addFirst(new float[]{c % n + 0.5f, c / n + 0.5f});
        return out;
    }

    /** Fait marcher un client vers une cible en contournant les obstacles (petits pas, comme un joueur). */
    private static void walkTo(GameClient c, ClientState s, float tx, float ty, Runnable pump) throws InterruptedException {
        pump.run();
        for (float[] wp : path(s.map, s.myX, s.myY, tx, ty)) {
            long end = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < end) {
                pump.run();
                float dx = wp[0] - s.myX, dy = wp[1] - s.myY;
                if (dx * dx + dy * dy < 0.01f) break;
                float len = (float) Math.sqrt(dx * dx + dy * dy), step = Math.min(len, 0.35f);
                s.myX += dx / len * step; s.myY += dy / len * step;
                c.send("POS", Protocol.f(s.myX), Protocol.f(s.myY), 0, 1);
                Thread.sleep(20);
            }
        }
        Thread.sleep(150);
        pump.run();
        float dx = tx - s.myX, dy = ty - s.myY;
        assertTrue("arrivé en " + tx + "," + ty + " (pos " + s.myX + "," + s.myY + ")", dx * dx + dy * dy < 0.1f);
    }

    @Test
    public void shopsBankEquipmentAndSafeZone() throws Exception {
        GameServer server = new GameServer(42L, "Hôte", false);
        server.start(0);
        GameClient c = new GameClient("127.0.0.1", server.getPort(), hello("Marchande", Rules.ARCHER));
        ClientState s = new ClientState();
        Runnable pump = () -> pump(c, s);
        try {
            waitFor("bienvenue", () -> s.welcomed && s.invReceived && s.statsReceived, pump);
            // kit de départ
            assertEquals(Items.weaponId(Rules.ARCHER, 1), s.weapon);
            assertEquals(3, s.inv.count(Items.SMALL_HP));
            assertTrue(WorldMap.inSafeZone(s.myX, s.myY, 0));

            // acheter loin de la boutique : refusé
            c.send("BUY", Items.SMALL_HP);
            Thread.sleep(300); pump.run();
            assertEquals(3, s.inv.count(Items.SMALL_HP));

            // on se donne de l'or puis on va à l'apothicaire
            server.runOnServer(() -> { for (Object o : serverPlayers(server)) ((GameServer.Player) o).gold = 500; });
            WorldMap.Building apo = s.map.building(WorldMap.B_APOTHECARY);
            walkTo(c, s, apo.doorX(), apo.doorY(), pump);
            c.send("BUY", Items.SMALL_HP);
            waitFor("achat potion", () -> s.inv.count(Items.SMALL_HP) == 4 && s.gold == 488, pump);
            c.send("BUY", Items.weaponId(Rules.ARCHER, 2)); // pas vendu ici
            Thread.sleep(300); pump.run();
            assertEquals(488, s.gold);

            // forge : armure de palier 1 puis équipement
            WorldMap.Building forge = s.map.building(WorldMap.B_FORGE);
            walkTo(c, s, forge.doorX(), forge.doorY(), pump);
            int armorId = Items.armorId(Rules.ARCHER, 1);
            c.send("BUY", armorId);
            waitFor("achat armure", () -> s.inv.count(armorId) == 1, pump);
            c.send("BUY", Items.armorId(Rules.ARCHER, 3)); // niveau 10 requis
            Thread.sleep(300); pump.run();
            assertEquals(0, s.inv.count(Items.armorId(Rules.ARCHER, 3)));
            int defBefore = s.def;
            c.send("USE", s.inv.find(armorId));
            waitFor("armure équipée", () -> s.armor == armorId && s.def > defBefore, pump);

            // auberge : nourriture -> bonus
            WorldMap.Building inn = s.map.building(WorldMap.B_INN);
            walkTo(c, s, inn.doorX(), inn.doorY(), pump);
            c.send("USE", s.inv.find(Items.BREAD));
            waitFor("repas", () -> s.buffT > 0, pump);

            // dépôt
            WorldMap.Building bank = s.map.building(WorldMap.B_BANK);
            walkTo(c, s, bank.doorX(), bank.doorY(), pump);
            int slot = s.inv.find(Items.SMALL_MP);
            c.send("DEP", slot);
            waitFor("dépôt", () -> s.bank.count(Items.SMALL_MP) == 2 && s.inv.count(Items.SMALL_MP) == 0, pump);
            c.send("WDR", s.bank.find(Items.SMALL_MP));
            waitFor("retrait", () -> s.inv.count(Items.SMALL_MP) == 2, pump);

            // compétence : consomme des PM
            int mpBefore = s.mp;
            final float px = s.myX, py = s.myY;
            server.runOnServer(() -> server.spawnAt(Rules.WOLF, px + 3f, py - 3f));
            c.send("SKILL");
            waitFor("compétence lancée", () -> s.mp < mpBefore, pump);

            // zone sûre : un loup lancé vers un joueur dans le village n'y entre jamais
            final int[] wolfId = new int[1];
            server.runOnServer(() -> wolfId[0] = server.spawnAt(Rules.WOLF, WorldMap.VILLAGE_X, WorldMap.VILLAGE_Y - 17.5f).id);
            walkTo(c, s, WorldMap.VILLAGE_X, WorldMap.VILLAGE_Y - 13.5f, pump);
            long end = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < end) {
                pump.run();
                final float[][] pos = new float[1][];
                server.runOnServer(() -> pos[0] = server.monsterPos(wolfId[0]));
                Thread.sleep(60);
                if (pos[0] != null) assertFalse("monstre dans le village", WorldMap.inSafeZone(pos[0][0], pos[0][1], 0));
            }
        } finally {
            c.close();
            server.stop();
        }
    }

    private static java.util.List<?> serverPlayers(GameServer s) {
        try {
            java.lang.reflect.Field f = GameServer.class.getDeclaredField("players");
            f.setAccessible(true);
            return (java.util.List<?>) f.get(s);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    public void twoPlayersOnLan() throws Exception {
        GameServer server = new GameServer(42L, "Hôte", true);
        server.start(0);
        GameClient c1 = new GameClient("127.0.0.1", server.getPort(), hello("Alice", Rules.WARRIOR));
        GameClient c2 = new GameClient("127.0.0.1", server.getPort(), hello("Bob", Rules.MAGE));
        ClientState s1 = new ClientState(), s2 = new ClientState();
        Runnable pumpAll = () -> { pump(c1, s1); pump(c2, s2); };
        try {
            waitFor("bienvenue", () -> s1.welcomed && s2.welcomed, pumpAll);
            assertEquals(42L, s1.map.seed);
            waitFor("les deux joueurs se voient", () -> s1.players.size() == 2 && s2.players.size() == 2, pumpAll);
            assertEquals("Bob", s1.players.get(s2.myId).name);
            assertEquals(Rules.maxHp(Rules.WARRIOR, 1), s1.maxHp);
            assertEquals(5, s2.players.get(s1.myId).look);

            // Déplacement d'Alice visible par Bob
            float nx = s1.map.canStand(s1.myX + 1f, s1.myY, Rules.PLAYER_RADIUS) ? s1.myX + 1f : s1.myX - 1f; // case libre (pas un lampadaire)
            c1.send("POS", Protocol.f(nx), Protocol.f(s1.myY), 1, 1);
            waitFor("déplacement répliqué", () -> Math.abs(s2.players.get(s1.myId).tx - nx) < 0.05f, pumpAll);

            // Téléportation refusée
            c1.send("POS", "5", "5", 1, 1);
            waitFor("téléportation refusée", () -> Math.abs(s1.myX - nx) < 0.05f && Math.abs(s2.players.get(s1.myId).tx - nx) < 0.05f, pumpAll);

            // Chat
            c2.send("CHAT", "Salut Alice, on y va ?");
            waitFor("chat reçu", () -> s1.chat.stream().anyMatch(l -> l.name.equals("Bob") && l.text.equals("Salut Alice, on y va ?")), pumpAll);

            // Combat : on place une gelée à côté d'Alice, elle frappe jusqu'à la vaincre.
            // (hors du village, sinon les monstres n'attaquent pas)
            float ox = WorldMap.VILLAGE_X, oy = WorldMap.VILLAGE_Y - 17.5f;
            walkTo(c1, s1, ox, oy, pumpAll);
            walkTo(c2, s2, ox + 1, oy, pumpAll);
            final float ax = s1.myX, ay = s1.myY;
            server.runOnServer(() -> server.spawnAt(Rules.SLIME, ax + 1f, ay));
            waitFor("gelée visible", () -> s1.mobs.values().stream().anyMatch(m -> m.type == Rules.SLIME && Math.abs(m.tx - (ax + 1f)) < 1.5f), pumpAll);
            long end = System.currentTimeMillis() + 6000;
            while (s1.xp == 0 && s1.level == 1 && System.currentTimeMillis() < end) {
                c1.send("ATK");
                Thread.sleep(120);
                pumpAll.run();
            }
            assertTrue("Alice a gagné de l'XP", s1.xp > 0 || s1.level > 1);
            assertTrue("Alice a gagné de l'or", s1.gold > 0);
            // Bob, tout proche, partage la récompense
            waitFor("XP partagée avec Bob", () -> s2.xp > 0, pumpAll);

            // Déconnexion de Bob
            c2.close();
            waitFor("Bob parti", () -> s1.players.size() == 1, pumpAll);
        } finally {
            c1.close(); c2.close();
            server.stop();
        }
    }

    @Test
    public void soloConnectsReliably() throws Exception {
        // Régression : le HELLO ne doit jamais être perdu, même envoyé immédiatement.
        for (int i = 0; i < 25; i++) {
            GameServer server = new GameServer(3L, "Solo", false);
            server.start(0);
            GameClient c = new GameClient("127.0.0.1", server.getPort(), hello("Solo" + i, i % 3));
            ClientState s = new ClientState();
            try {
                waitFor("bienvenue n°" + i, () -> s.welcomed && s.statsReceived, () -> pump(c, s));
            } finally {
                c.close();
                server.stop();
            }
        }
    }

    @Test
    public void lanDiscoveryFindsHost() throws Exception {
        GameServer server = new GameServer(7L, "PartieTest", true);
        server.start(0);
        LanDiscovery.Scanner scanner = new LanDiscovery.Scanner(Protocol.UDP_PORT);
        try {
            long end = System.currentTimeMillis() + 5000;
            boolean found = false;
            while (!found && System.currentTimeMillis() < end) {
                for (LanDiscovery.Host h : scanner.hosts())
                    if (h.name.equals("PartieTest") && h.port == server.getPort()) found = true;
                Thread.sleep(100);
            }
            assertTrue("partie trouvée par diffusion UDP", found);
        } finally {
            scanner.stop();
            server.stop();
        }
    }

    @Test
    public void soloServerRefusesLanConnections() throws Exception {
        GameServer server = new GameServer(9L, "Solo", false);
        server.start(0);
        try {
            String lan = null;
            for (String ip : LanDiscovery.localAddresses()) lan = ip;
            if (lan == null) return; // pas d'interface réseau : rien à vérifier
            GameClient c = new GameClient(lan, server.getPort(), hello("Intrus", 0));
            long end = System.currentTimeMillis() + 6000;
            while (c.isConnecting() && System.currentTimeMillis() < end) Thread.sleep(20);
            assertTrue(c.isClosed());
        } finally {
            server.stop();
        }
    }
}
