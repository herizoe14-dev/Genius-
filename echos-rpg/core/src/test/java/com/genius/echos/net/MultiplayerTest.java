package com.genius.echos.net;

import com.genius.echos.world.Rules;
import com.genius.echos.world.WorldMap;
import org.junit.Test;

import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

public class MultiplayerTest {

    private static String hello(String name, int cls) {
        return Protocol.join("HELLO", name, cls, 1, 0, 0, 3, Protocol.VERSION);
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
        assertEquals(WorldMap.FOUNTAIN, a.get((int) WorldMap.VILLAGE_X, (int) WorldMap.VILLAGE_Y));
        assertTrue(a.canStand(WorldMap.VILLAGE_X + 0.5f, WorldMap.VILLAGE_Y - 2.5f, Rules.PLAYER_RADIUS));
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

            // Déplacement d'Alice visible par Bob
            float nx = s1.myX + 1f;
            c1.send("POS", Protocol.f(nx), Protocol.f(s1.myY), 1, 1);
            waitFor("déplacement répliqué", () -> Math.abs(s2.players.get(s1.myId).tx - nx) < 0.05f, pumpAll);

            // Téléportation refusée
            c1.send("POS", "5", "5", 1, 1);
            waitFor("téléportation refusée", () -> Math.abs(s1.myX - nx) < 0.05f && Math.abs(s2.players.get(s1.myId).tx - nx) < 0.05f, pumpAll);

            // Chat
            c2.send("CHAT", "Salut Alice, on y va ?");
            waitFor("chat reçu", () -> s1.chat.stream().anyMatch(l -> l.name.equals("Bob") && l.text.equals("Salut Alice, on y va ?")), pumpAll);

            // Combat : on place une gelée à côté d'Alice, elle frappe jusqu'à la vaincre.
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
