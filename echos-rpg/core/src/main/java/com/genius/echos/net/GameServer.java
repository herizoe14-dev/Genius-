package com.genius.echos.net;

import com.genius.echos.world.Rules;
import com.genius.echos.world.WorldMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.genius.echos.net.Protocol.f;
import static com.genius.echos.net.Protocol.join;

/**
 * Serveur de jeu qui fait autorité sur les monstres, les combats et la progression.
 * Il tourne dans l'application du joueur qui « héberge » (ou en mode solo, en local).
 * Tout l'état du jeu est manipulé uniquement par le fil de simulation (20 Hz) :
 * les fils réseau se contentent de déposer des commandes dans une file.
 */
public class GameServer implements Connection.Listener {
    public static final int TICK_RATE = 20;
    private static final float DT = 1f / TICK_RATE;
    private static final float VIEW_RANGE = 22f;
    private static final int MAX_PLAYERS = 8;

    public final WorldMap map;
    private final String hostName;
    private final boolean lan;
    private final Random rnd = new Random();
    private ServerSocket serverSocket;
    private LanDiscovery.Responder responder;
    private volatile boolean running;
    private int port;

    private final ConcurrentLinkedQueue<Object[]> commands = new ConcurrentLinkedQueue<>();
    private final List<Player> players = new ArrayList<>();
    private final List<Monster> monsters = new ArrayList<>();
    private final List<float[]> respawns = new ArrayList<>(); // {timer, type}
    private volatile int playerCount;
    private int nextId = 1;
    private long tick;

    static final class Player {
        int id; Connection conn; String name = "?"; int cls;
        float x, y; int facing = 1; boolean moving;
        int hp, level = 1, xp, gold, potions;
        float atkCd, potCd, sinceHit = 99f;
        boolean joined, dirty = true;
        int maxHp() { return Rules.maxHp(cls, level); }
    }

    static final class Monster {
        int id, type; float x, y, homeX, homeY; int hp;
        Player target; boolean returning; float atkCd, wanderT; float wx, wy; int facing = 1;
    }

    public GameServer(long seed, String hostName, boolean lan) {
        this.map = new WorldMap(seed);
        this.hostName = Protocol.clean(hostName, 20);
        this.lan = lan;
    }

    public int getPort() { return port; }
    public int getPlayerCount() { return playerCount; }

    /** Démarre le serveur. port = 0 pour un port libre quelconque. */
    public void start(int requestedPort) throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        // En solo, on n'écoute que sur la boucle locale : personne d'autre ne peut entrer.
        serverSocket.bind(lan ? new InetSocketAddress(requestedPort) : new InetSocketAddress("127.0.0.1", requestedPort));
        port = serverSocket.getLocalPort();
        running = true;
        spawnInitialMonsters();

        Thread accept = new Thread(() -> {
            while (running) {
                try {
                    Socket s = serverSocket.accept();
                    Connection c = new Connection(s, this);
                    commands.add(new Object[]{c, "#OPEN"}); // enregistré avant tout message reçu
                    c.start();
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        }, "echos-accept");
        accept.setDaemon(true);
        accept.start();

        Thread loop = new Thread(this::loop, "echos-server");
        loop.setDaemon(true);
        loop.start();

        if (lan) responder = new LanDiscovery.Responder(Protocol.UDP_PORT,
                () -> join(hostName, port, playerCount));
    }

    public void stop() {
        running = false;
        if (responder != null) responder.stop();
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        for (Player p : new ArrayList<>(players)) p.conn.close();
    }

    @Override public void onLine(Connection c, String line) { commands.add(new Object[]{c, line}); }
    @Override public void onClosed(Connection c) { commands.add(new Object[]{c, "#CLOSE"}); }

    private void loop() {
        long next = System.nanoTime();
        while (running) {
            try {
                step();
            } catch (Exception e) {
                e.printStackTrace();
            }
            next += 1_000_000_000L / TICK_RATE;
            long sleep = (next - System.nanoTime()) / 1_000_000L;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            } else if (sleep < -1000) {
                next = System.nanoTime();
            }
        }
    }

    // ================================================================ simulation

    void step() {
        Object[] cmd;
        while ((cmd = commands.poll()) != null) {
            if (cmd[1] instanceof Runnable) ((Runnable) cmd[1]).run();
            else handle((Connection) cmd[0], (String) cmd[1]);
        }

        tick++;
        for (Player p : players) updatePlayer(p);
        for (Monster m : monsters) updateMonster(m);
        updateRespawns();

        if (tick % 2 == 0) broadcastSnapshots();
        for (Player p : players) {
            if (p.joined && (p.dirty || tick % TICK_RATE == 0)) sendYou(p);
        }
    }

    private Player playerOf(Connection c) {
        return c.attachment instanceof Player ? (Player) c.attachment : null;
    }

    private void handle(Connection c, String line) {
        if (line.equals("#OPEN")) {
            if (players.size() >= MAX_PLAYERS) {
                c.send(join("KICK", "La partie est pleine"));
                c.close();
                return;
            }
            Player p = new Player();
            p.id = nextId++;
            p.conn = c;
            c.attachment = p;
            players.add(p);
            playerCount = players.size();
            return;
        }
        Player p = playerOf(c);
        if (p == null) return;
        if (line.equals("#CLOSE")) {
            players.remove(p);
            playerCount = players.size();
            for (Monster m : monsters) if (m.target == p) m.target = null;
            if (p.joined) systemChat(p.name + " a quitté la partie.");
            return;
        }

        String[] a = line.split("\t", -1);
        switch (a[0]) {
            case "HELLO": {
                if (p.joined || a.length < 8) return;
                if (Protocol.i(a[7], 0) != Protocol.VERSION) {
                    c.send(join("KICK", "Version différente de celle de l'hôte"));
                    c.close();
                    return;
                }
                p.name = Protocol.clean(a[1], 16);
                if (p.name.isEmpty()) p.name = "Voyageur" + p.id;
                p.cls = clamp(Protocol.i(a[2], 0), 0, 2);
                p.level = clamp(Protocol.i(a[3], 1), 1, Rules.MAX_LEVEL);
                p.xp = Math.max(0, Protocol.i(a[4], 0));
                p.gold = Math.max(0, Protocol.i(a[5], 0));
                p.potions = clamp(Protocol.i(a[6], 3), 0, 99);
                p.hp = p.maxHp();
                float[] s = spawnPoint();
                p.x = s[0]; p.y = s[1];
                p.joined = true;
                c.send(join("WELCOME", p.id, map.seed, f(p.x), f(p.y)));
                sendYou(p);
                systemChat(p.name + " (" + Rules.CLASS_NAMES[p.cls] + " niv. " + p.level + ") a rejoint la partie !");
                break;
            }
            case "POS": {
                if (!p.joined || a.length < 5) return;
                float x = Protocol.fl(a[1], p.x), y = Protocol.fl(a[2], p.y);
                float dx = x - p.x, dy = y - p.y;
                // Garde-fou : refuse les téléportations et les positions dans un mur.
                if (dx * dx + dy * dy > 9f || !map.canStand(x, y, Rules.PLAYER_RADIUS * 0.9f)) {
                    c.send(join("TP", f(p.x), f(p.y)));
                    return;
                }
                p.x = x; p.y = y;
                p.facing = Protocol.i(a[3], 1) < 0 ? -1 : 1;
                p.moving = a[4].equals("1");
                break;
            }
            case "ATK": if (p.joined) playerAttack(p); break;
            case "POT": if (p.joined) drinkPotion(p); break;
            case "BUY": if (p.joined) buyPotion(p); break;
            case "CHAT": {
                if (!p.joined || a.length < 2) return;
                String text = Protocol.cleanText(a[1], 120);
                if (!text.isEmpty()) broadcast(join("CHAT", p.name, text));
                break;
            }
            default: break;
        }
    }

    private float[] spawnPoint() {
        for (int i = 0; i < 50; i++) {
            float x = WorldMap.VILLAGE_X + 0.5f + (rnd.nextFloat() - 0.5f) * 4f;
            float y = WorldMap.VILLAGE_Y - 2.5f + (rnd.nextFloat() - 0.5f) * 2f;
            if (map.canStand(x, y, Rules.PLAYER_RADIUS)) return new float[]{x, y};
        }
        return new float[]{WorldMap.VILLAGE_X + 0.5f, WorldMap.VILLAGE_Y - 2.5f};
    }

    private void updatePlayer(Player p) {
        if (!p.joined) return;
        p.atkCd -= DT;
        p.potCd -= DT;
        p.sinceHit += DT;
        int max = p.maxHp();
        if (p.hp < max && tick % TICK_RATE == 0) {
            int heal = 0;
            if (nearFountain(p)) heal = Math.max(1, (int) (max * 0.15f));
            else if (p.sinceHit > 6f) heal = Math.max(1, (int) (max * 0.02f));
            if (heal > 0) {
                heal = Math.min(heal, max - p.hp);
                p.hp += heal;
                p.dirty = true;
                if (nearFountain(p)) broadcast(join("FX", Protocol.FX_HEAL, f(p.x), f(p.y), f(p.x), f(p.y)));
            }
        }
    }

    private boolean nearFountain(Player p) {
        float dx = p.x - (WorldMap.VILLAGE_X + 0.5f), dy = p.y - (WorldMap.VILLAGE_Y + 0.5f);
        return dx * dx + dy * dy < 3.2f * 3.2f;
    }

    private void playerAttack(Player p) {
        if (p.atkCd > 0) return;
        float range = Rules.CLASS_RANGE[p.cls];
        Monster best = null;
        float bestD = Float.MAX_VALUE;
        for (Monster m : monsters) {
            if (m.hp <= 0) continue;
            float d = dist(p.x, p.y, m.x, m.y) - Rules.MOB_RADIUS[m.type];
            if (d < range && d < bestD) { bestD = d; best = m; }
        }
        p.atkCd = Rules.CLASS_COOLDOWN[p.cls];
        int fx = p.cls == Rules.WARRIOR ? Protocol.FX_SLASH : p.cls == Rules.ARCHER ? Protocol.FX_ARROW : Protocol.FX_FIREBALL;
        if (best == null) {
            broadcast(join("FX", fx, f(p.x), f(p.y), f(p.x + p.facing * 1.2f), f(p.y)));
            return;
        }
        p.facing = best.x < p.x ? -1 : 1;
        broadcast(join("FX", fx, f(p.x), f(p.y), f(best.x), f(best.y)));
        int base = Rules.attack(p.cls, p.level);
        if (Rules.CLASS_SPLASH[p.cls] > 0) {
            float r = Rules.CLASS_SPLASH[p.cls];
            for (Monster m : new ArrayList<>(monsters))
                if (m.hp > 0 && dist(m.x, m.y, best.x, best.y) <= r) hitMonster(p, m, base);
        } else {
            hitMonster(p, best, base);
        }
    }

    private void hitMonster(Player p, Monster m, int base) {
        boolean crit = rnd.nextFloat() < 0.12f;
        int dmg = Math.max(1, Math.round(base * (0.85f + rnd.nextFloat() * 0.3f) * (crit ? 1.8f : 1f)));
        m.hp -= dmg;
        broadcast(join("TXT", f(m.x), f(m.y + 0.6f), crit ? Protocol.TXT_CRIT : Protocol.TXT_DMG_MOB, crit ? dmg + "!" : dmg));
        if (m.target == null && !m.returning) m.target = p;
        if (m.hp <= 0) killMonster(p, m);
    }

    private void killMonster(Player killer, Monster m) {
        monsters.remove(m);
        respawns.add(new float[]{m.type == Rules.GOLEM ? 120f : 8f + rnd.nextFloat() * 10f, m.type});
        int xp = Rules.MOB_XP[m.type];
        int gold = Rules.MOB_GOLD_MIN[m.type] + rnd.nextInt(Rules.MOB_GOLD_MAX[m.type] - Rules.MOB_GOLD_MIN[m.type] + 1);
        // Récompense coopérative : tous les joueurs proches gagnent l'expérience et l'or.
        for (Player p : players) {
            if (!p.joined || (p != killer && dist(p.x, p.y, m.x, m.y) > 12f)) continue;
            p.gold += gold;
            gainXp(p, xp);
            p.dirty = true;
        }
        broadcast(join("TXT", f(m.x), f(m.y + 1.1f), Protocol.TXT_GOLD, "+" + gold + " or"));
        if (rnd.nextFloat() < (m.type == Rules.GOLEM ? 1f : 0.14f)) {
            int n = m.type == Rules.GOLEM ? 3 : 1;
            killer.potions = Math.min(99, killer.potions + n);
            killer.dirty = true;
            broadcast(join("TXT", f(m.x), f(m.y + 1.6f), Protocol.TXT_HEAL, "+" + n + " potion"));
        }
        if (m.type == Rules.GOLEM) systemChat(killer.name + " et ses alliés ont vaincu le Golem ancien !");
    }

    private void gainXp(Player p, int xp) {
        if (p.level >= Rules.MAX_LEVEL) return;
        p.xp += xp;
        while (p.level < Rules.MAX_LEVEL && p.xp >= Rules.xpToNext(p.level)) {
            p.xp -= Rules.xpToNext(p.level);
            p.level++;
            p.hp = p.maxHp();
            broadcast(join("FX", Protocol.FX_LEVELUP, f(p.x), f(p.y), f(p.x), f(p.y)));
            systemChat(p.name + " atteint le niveau " + p.level + " !");
        }
        if (p.level >= Rules.MAX_LEVEL) p.xp = 0;
    }

    private void drinkPotion(Player p) {
        if (p.potCd > 0 || p.potions <= 0 || p.hp >= p.maxHp()) return;
        p.potCd = 1f;
        p.potions--;
        int heal = Math.min(p.maxHp() - p.hp, Math.max(1, p.maxHp() / 2));
        p.hp += heal;
        p.dirty = true;
        broadcast(join("FX", Protocol.FX_HEAL, f(p.x), f(p.y), f(p.x), f(p.y)));
        broadcast(join("TXT", f(p.x), f(p.y + 0.8f), Protocol.TXT_HEAL, "+" + heal));
    }

    private void buyPotion(Player p) {
        if (!nearFountain(p)) {
            p.conn.send(join("CHAT", "", "Approche-toi de la fontaine du village pour acheter."));
            return;
        }
        if (p.gold < Rules.POTION_PRICE) {
            p.conn.send(join("CHAT", "", "Pas assez d'or (" + Rules.POTION_PRICE + " or la potion)."));
            return;
        }
        if (p.potions >= 99) return;
        p.gold -= Rules.POTION_PRICE;
        p.potions++;
        p.dirty = true;
        p.conn.send(join("TXT", f(p.x), f(p.y + 0.8f), Protocol.TXT_GOLD, "-" + Rules.POTION_PRICE + " or"));
    }

    private void damagePlayer(Monster m, Player p) {
        int dmg = Math.max(1, Math.round(Rules.MOB_ATK[m.type] * (0.8f + rnd.nextFloat() * 0.4f)));
        p.hp -= dmg;
        p.sinceHit = 0;
        p.dirty = true;
        broadcast(join("FX", Protocol.FX_BITE, f(m.x), f(m.y), f(p.x), f(p.y)));
        broadcast(join("TXT", f(p.x), f(p.y + 0.7f), Protocol.TXT_DMG_PLAYER, dmg));
        if (p.hp <= 0) {
            int lost = p.gold / 10;
            p.gold -= lost;
            p.hp = p.maxHp();
            float[] s = spawnPoint();
            p.x = s[0]; p.y = s[1];
            p.conn.send(join("TP", f(p.x), f(p.y)));
            systemChat(p.name + " a été vaincu par " + Rules.MOB_NAMES[m.type] + (lost > 0 ? " et perd " + lost + " or." : "."));
            for (Monster o : monsters) if (o.target == p) { o.target = null; o.returning = true; }
        }
    }

    // ================================================================ monstres

    private void spawnInitialMonsters() {
        for (int i = 0; i < 26; i++) spawnMonster(Rules.SLIME);
        for (int i = 0; i < 26; i++) spawnMonster(Rules.WOLF);
        for (int i = 0; i < 24; i++) spawnMonster(Rules.SKELETON);
        spawnMonster(Rules.GOLEM);
    }

    private void updateRespawns() {
        List<Integer> due = new ArrayList<>();
        for (Iterator<float[]> it = respawns.iterator(); it.hasNext(); ) {
            float[] r = it.next();
            r[0] -= DT;
            if (r[0] <= 0) { due.add((int) r[1]); it.remove(); }
        }
        for (int type : due) spawnMonster(type);
    }

    private boolean inZone(int type, float x, float y) {
        float d = WorldMap.distToVillage(x, y);
        float dr = dist(x, y, WorldMap.RUINS_X, WorldMap.RUINS_Y);
        if (dr < 9f) return false;
        switch (type) {
            case Rules.SLIME: return d > 11f && d < 24f;
            case Rules.WOLF: return d >= 24f && d < 42f;
            default: return d >= 42f;
        }
    }

    void spawnMonster(int type) {
        float x = 0, y = 0;
        boolean ok = false;
        if (type == Rules.GOLEM) {
            x = WorldMap.RUINS_X; y = WorldMap.RUINS_Y; ok = true;
        } else {
            for (int i = 0; i < 400 && !ok; i++) {
                x = 2 + rnd.nextFloat() * (WorldMap.SIZE - 4);
                y = 2 + rnd.nextFloat() * (WorldMap.SIZE - 4);
                if (!inZone(type, x, y) || !map.canStand(x, y, Rules.MOB_RADIUS[type])) continue;
                ok = true;
                for (Player p : players) if (dist(p.x, p.y, x, y) < 9f) { ok = false; break; }
            }
        }
        if (!ok) { respawns.add(new float[]{5f, type}); return; }
        Monster m = new Monster();
        m.id = nextId++;
        m.type = type;
        m.x = m.homeX = m.wx = x;
        m.y = m.homeY = m.wy = y;
        m.hp = Rules.MOB_HP[type];
        m.wanderT = rnd.nextFloat() * 3f;
        monsters.add(m);
    }

    /** Pour les tests : place un monstre précis. Doit être appelé depuis le fil de simulation. */
    Monster spawnAt(int type, float x, float y) {
        Monster m = new Monster();
        m.id = nextId++; m.type = type;
        m.x = m.homeX = m.wx = x; m.y = m.homeY = m.wy = y;
        m.hp = Rules.MOB_HP[type];
        m.wanderT = 99f;
        monsters.add(m);
        return m;
    }

    /** Pour les tests : exécute une action sur le fil de simulation. */
    void runOnServer(Runnable r) { commands.add(new Object[]{null, r}); }

    private void updateMonster(Monster m) {
        m.atkCd -= DT;
        float speed = Rules.MOB_SPEED[m.type];
        float leash = m.type == Rules.GOLEM ? 10f : 15f;

        if (m.target != null && (!players.contains(m.target) || dist(m.x, m.y, m.homeX, m.homeY) > leash)) {
            m.target = null;
            m.returning = true;
        }
        if (m.target == null && !m.returning) {
            float aggro = Rules.MOB_AGGRO[m.type];
            Player best = null;
            float bestD = aggro;
            for (Player p : players) {
                if (!p.joined) continue;
                float d = dist(p.x, p.y, m.x, m.y);
                if (d < bestD && dist(p.x, p.y, WorldMap.VILLAGE_X, WorldMap.VILLAGE_Y) > 9f) { bestD = d; best = p; }
            }
            m.target = best;
        }

        if (m.target != null) {
            Player p = m.target;
            float d = dist(m.x, m.y, p.x, p.y);
            float reach = Rules.MOB_ATTACK_RANGE[m.type];
            if (d > reach * 0.85f) moveToward(m, p.x, p.y, speed);
            if (d <= reach && m.atkCd <= 0) {
                m.atkCd = Rules.MOB_ATTACK_CD[m.type];
                damagePlayer(m, p);
            }
        } else if (m.returning) {
            moveToward(m, m.homeX, m.homeY, speed * 1.3f);
            if (dist(m.x, m.y, m.homeX, m.homeY) < 0.5f) {
                m.returning = false;
                m.hp = Rules.MOB_HP[m.type];
            }
        } else {
            m.wanderT -= DT;
            if (m.wanderT <= 0) {
                m.wanderT = 2f + rnd.nextFloat() * 4f;
                m.wx = m.homeX + (rnd.nextFloat() - 0.5f) * 6f;
                m.wy = m.homeY + (rnd.nextFloat() - 0.5f) * 6f;
            }
            if (dist(m.x, m.y, m.wx, m.wy) > 0.2f) moveToward(m, m.wx, m.wy, speed * 0.4f);
        }
    }

    private void moveToward(Monster m, float tx, float ty, float speed) {
        float dx = tx - m.x, dy = ty - m.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float step = Math.min(len, speed * DT);
        float mx = dx / len * step, my = dy / len * step;
        float r = Rules.MOB_RADIUS[m.type];
        if (Math.abs(mx) > 0.001f) m.facing = mx < 0 ? -1 : 1;
        if (map.canStand(m.x + mx, m.y + my, r)) { m.x += mx; m.y += my; }
        else if (map.canStand(m.x + mx, m.y, r)) m.x += mx;
        else if (map.canStand(m.x, m.y + my, r)) m.y += my;
        else if (m.target == null) m.wanderT = 0; // bloqué : change de direction
    }

    // ================================================================ envoi

    private void broadcastSnapshots() {
        StringBuilder ps = new StringBuilder();
        for (Player p : players) {
            if (!p.joined) continue;
            if (ps.length() > 0) ps.append(';');
            ps.append(p.id).append(',').append(p.name).append(',').append(p.cls).append(',')
                    .append(f(p.x)).append(',').append(f(p.y)).append(',').append(p.hp).append(',')
                    .append(p.maxHp()).append(',').append(p.level).append(',').append(p.facing).append(',')
                    .append(p.moving ? 1 : 0);
        }
        String playersStr = ps.toString();
        for (Player p : players) {
            if (!p.joined || p.conn.pending() > 200) continue;
            StringBuilder ms = new StringBuilder();
            for (Monster m : monsters) {
                if (Math.abs(m.x - p.x) > VIEW_RANGE || Math.abs(m.y - p.y) > VIEW_RANGE) continue;
                if (ms.length() > 0) ms.append(';');
                ms.append(m.id).append(',').append(m.type).append(',').append(f(m.x)).append(',')
                        .append(f(m.y)).append(',').append(m.hp).append(',').append(Rules.MOB_HP[m.type])
                        .append(',').append(m.facing);
            }
            p.conn.send(join("S", playersStr, ms));
        }
    }

    private void sendYou(Player p) {
        p.dirty = false;
        p.conn.send(join("YOU", p.hp, p.maxHp(), p.level, p.xp, Rules.xpToNext(p.level), p.gold, p.potions));
    }

    private void broadcast(String line) {
        for (Player p : players) if (p.joined) p.conn.send(line);
    }

    private void systemChat(String text) { broadcast(join("CHAT", "", text)); }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
