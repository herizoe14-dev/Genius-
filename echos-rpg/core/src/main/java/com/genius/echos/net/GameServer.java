package com.genius.echos.net;

import com.genius.echos.world.Inventory;
import com.genius.echos.world.Items;
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
 * Serveur de jeu qui fait autorité sur les monstres, les combats, l'inventaire et la progression.
 * Il tourne dans l'application du joueur qui « héberge » (ou en mode solo, en local).
 * Tout l'état est manipulé par le seul fil de simulation (20 Hz) ;
 * les fils réseau déposent leurs commandes dans une file.
 */
public class GameServer implements Connection.Listener {
    public static final int TICK_RATE = 20;
    private static final float DT = 1f / TICK_RATE;
    private static final float VIEW_RANGE = 22f;
    private static final int MAX_PLAYERS = 8;
    public static final float SHOP_RANGE = 2.6f;
    public static final int INV_SIZE = 24, BANK_SIZE = 48;

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
        int id; Connection conn; String name = "?"; int cls, look;
        float x, y; int dir; boolean moving;
        int hp, mp, level = 1, xp, gold;
        int weapon, armor;
        final Inventory inv = new Inventory(INV_SIZE), bank = new Inventory(BANK_SIZE);
        float atkCd, useCd, skillCd, sinceHit = 99f;
        float buffT; int buffHp, buffMp;
        boolean joined, dirty = true, invDirty = true;
        int maxHp() { return Rules.maxHp(cls, level) + Items.armorHp(armor); }
        int maxMp() { return Rules.maxMp(cls, level); }
        int atk() { return Rules.attack(cls, level) + Items.weaponAtk(weapon); }
        int def() { return Items.armorDef(armor); }
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

        if (lan) responder = new LanDiscovery.Responder(Protocol.UDP_PORT, () -> join(hostName, port, playerCount));
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
            try { step(); } catch (Exception e) { e.printStackTrace(); }
            next += 1_000_000_000L / TICK_RATE;
            long sleep = (next - System.nanoTime()) / 1_000_000L;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            } else if (sleep < -1000) next = System.nanoTime();
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
            if (!p.joined) continue;
            if (p.invDirty) sendInv(p);
            if (p.dirty || tick % TICK_RATE == 0) sendYou(p);
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
        if (!a[0].equals("HELLO") && !p.joined) return;
        switch (a[0]) {
            case "HELLO": hello(p, a); break;
            case "POS": {
                if (a.length < 5) return;
                float x = Protocol.fl(a[1], p.x), y = Protocol.fl(a[2], p.y);
                float dx = x - p.x, dy = y - p.y;
                // Garde-fou : refuse les téléportations et les positions dans un mur.
                if (dx * dx + dy * dy > 9f || !map.canStand(x, y, Rules.PLAYER_RADIUS * 0.9f)) {
                    c.send(join("TP", f(p.x), f(p.y)));
                    return;
                }
                p.x = x; p.y = y;
                p.dir = clamp(Protocol.i(a[3], 0), 0, 3);
                p.moving = a[4].equals("1");
                break;
            }
            case "ATK": playerAttack(p); break;
            case "SKILL": playerSkill(p); break;
            case "USE": if (a.length > 1) useSlot(p, Protocol.i(a[1], -1)); break;
            case "QUSE": if (a.length > 1) quickUse(p, a[1].equals("mp") ? Items.T_MP : Items.T_HP); break;
            case "UNEQ": if (a.length > 1) unequip(p, a[1].equals("a")); break;
            case "BUY": if (a.length > 1) buy(p, Protocol.i(a[1], -1)); break;
            case "SELL": if (a.length > 1) sell(p, Protocol.i(a[1], -1)); break;
            case "DEP": if (a.length > 1) bankMove(p, Protocol.i(a[1], -1), true); break;
            case "WDR": if (a.length > 1) bankMove(p, Protocol.i(a[1], -1), false); break;
            case "CHAT": {
                if (a.length < 2) return;
                String text = Protocol.cleanText(a[1], 120);
                if (!text.isEmpty()) broadcast(join("CHAT", p.name, text));
                break;
            }
            default: break;
        }
    }

    private void hello(Player p, String[] a) {
        if (p.joined || a.length < 7) return;
        if (Protocol.i(a[6], 0) != Protocol.VERSION) {
            p.conn.send(join("KICK", "Version différente de celle de l'hôte (mets le jeu à jour)"));
            p.conn.close();
            return;
        }
        p.name = Protocol.clean(a[1], 16);
        if (p.name.isEmpty()) p.name = "Voyageur" + p.id;
        p.cls = clamp(Protocol.i(a[2], 0), 0, 2);
        p.level = clamp(Protocol.i(a[3], 1), 1, Rules.MAX_LEVEL);
        p.xp = Math.max(0, Protocol.i(a[4], 0));
        p.gold = Math.max(0, Protocol.i(a[5], 0));
        p.look = a.length > 7 ? Math.floorMod(Protocol.i(a[7], 0), 96) : 0;
        if (a.length > 8) p.inv.decode(a[8]);
        if (a.length > 9) p.bank.decode(a[9]);
        p.weapon = validEquip(p, a.length > 10 ? Protocol.i(a[10], 0) : 0, Items.T_WEAPON);
        p.armor = validEquip(p, a.length > 11 ? Protocol.i(a[11], 0) : 0, Items.T_ARMOR);
        boolean fresh = p.weapon == 0 && p.armor == 0 && p.level == 1 && p.xp == 0 && p.inv.encode().isEmpty() && p.bank.encode().isEmpty();
        if (fresh) { // équipement de départ
            p.weapon = Items.weaponId(p.cls, 1);
            p.inv.add(Items.SMALL_HP, 3);
            p.inv.add(Items.SMALL_MP, 2);
            p.inv.add(Items.BREAD, 2);
        }
        p.hp = p.maxHp();
        p.mp = p.maxMp();
        float[] s = spawnPoint();
        p.x = s[0]; p.y = s[1];
        p.joined = true;
        p.conn.send(join("WELCOME", p.id, map.seed, f(p.x), f(p.y)));
        sendInv(p);
        sendYou(p);
        systemChat(p.name + " (" + Rules.CLASS_NAMES[p.cls] + " niv. " + p.level + ") a rejoint la partie !");
    }

    private static int validEquip(Player p, int id, int type) {
        Items.Def d = Items.get(id);
        return d != null && d.type == type && d.cls == p.cls ? id : 0;
    }

    private float[] spawnPoint() {
        for (int i = 0; i < 50; i++) {
            float x = WorldMap.FOUNTAIN_X + (rnd.nextFloat() - 0.5f) * 4f;
            float y = WorldMap.FOUNTAIN_Y - 2.2f - rnd.nextFloat() * 1.5f;
            if (map.canStand(x, y, Rules.PLAYER_RADIUS)) return new float[]{x, y};
        }
        return new float[]{WorldMap.FOUNTAIN_X, WorldMap.FOUNTAIN_Y - 2.5f};
    }

    private boolean nearFountain(Player p) {
        float dx = p.x - WorldMap.FOUNTAIN_X, dy = p.y - WorldMap.FOUNTAIN_Y;
        return dx * dx + dy * dy < 3.6f * 3.6f;
    }

    private void updatePlayer(Player p) {
        if (!p.joined) return;
        p.atkCd -= DT; p.useCd -= DT; p.skillCd -= DT;
        p.sinceHit += DT;
        if (p.buffT > 0) { p.buffT -= DT; if (p.buffT <= 0) { p.buffT = 0; p.dirty = true; } }
        if (tick % TICK_RATE != 0) return;
        int maxHp = p.maxHp(), maxMp = p.maxMp();
        boolean fountain = nearFountain(p);
        int hpGain = 0, mpGain = Math.max(1, (int) (maxMp * 0.02f));
        if (fountain) { hpGain = Math.max(1, (int) (maxHp * 0.15f)); mpGain = Math.max(1, (int) (maxMp * 0.15f)); }
        else if (p.sinceHit > 6f) hpGain = Math.max(1, (int) (maxHp * 0.02f));
        if (p.buffT > 0) { hpGain += p.buffHp; mpGain += p.buffMp; }
        int oldHp = p.hp, oldMp = p.mp;
        p.hp = Math.min(maxHp, p.hp + hpGain);
        p.mp = Math.min(maxMp, p.mp + mpGain);
        if (p.hp != oldHp || p.mp != oldMp) p.dirty = true;
        if (fountain && (p.hp != oldHp || p.mp != oldMp)) broadcast(join("FX", Protocol.FX_HEAL, f(p.x), f(p.y), f(p.x), f(p.y)));
    }

    // ================================================================ combat

    private Monster nearestMonster(Player p, float range) {
        Monster best = null;
        float bestD = Float.MAX_VALUE;
        for (Monster m : monsters) {
            if (m.hp <= 0) continue;
            float d = dist(p.x, p.y, m.x, m.y) - Rules.MOB_RADIUS[m.type];
            if (d < range && d < bestD) { bestD = d; best = m; }
        }
        return best;
    }

    private void faceToward(Player p, float tx, float ty) {
        float dx = tx - p.x, dy = ty - p.y;
        if (Math.abs(dx) > Math.abs(dy)) p.dir = dx < 0 ? 2 : 3; else p.dir = dy < 0 ? 0 : 1;
    }

    private void playerAttack(Player p) {
        if (p.atkCd > 0) return;
        Monster best = nearestMonster(p, Rules.CLASS_RANGE[p.cls]);
        p.atkCd = Rules.CLASS_COOLDOWN[p.cls];
        int fx = p.cls == Rules.WARRIOR ? Protocol.FX_SLASH : p.cls == Rules.ARCHER ? Protocol.FX_ARROW : Protocol.FX_FIREBALL;
        if (best == null) {
            float ox = p.dir == 2 ? -1.2f : p.dir == 3 ? 1.2f : 0, oy = p.dir == 0 ? -1.2f : p.dir == 1 ? 1.2f : 0;
            broadcast(join("FX", fx, f(p.x), f(p.y), f(p.x + ox), f(p.y + oy)));
            return;
        }
        faceToward(p, best.x, best.y);
        broadcast(join("FX", fx, f(p.x), f(p.y), f(best.x), f(best.y)));
        if (Rules.CLASS_SPLASH[p.cls] > 0) {
            for (Monster m : new ArrayList<>(monsters))
                if (m.hp > 0 && dist(m.x, m.y, best.x, best.y) <= Rules.CLASS_SPLASH[p.cls]) hitMonster(p, m, p.atk());
        } else hitMonster(p, best, p.atk());
    }

    private void playerSkill(Player p) {
        int cost = Rules.SKILL_COST[p.cls];
        if (p.skillCd > 0) return;
        if (p.mp < cost) { p.conn.send(join("TXT", f(p.x), f(p.y + 1.2f), Protocol.TXT_XP, "Pas assez de PM")); return; }
        int dmg = Math.round(p.atk() * Rules.SKILL_MULT[p.cls]);
        float r = Rules.SKILL_RADIUS[p.cls];
        List<Monster> hit = new ArrayList<>();
        if (p.cls == Rules.WARRIOR) {
            for (Monster m : monsters) if (m.hp > 0 && dist(m.x, m.y, p.x, p.y) - Rules.MOB_RADIUS[m.type] <= r) hit.add(m);
            broadcast(join("FX", Protocol.FX_WHIRL, f(p.x), f(p.y), f(p.x), f(p.y)));
        } else if (p.cls == Rules.ARCHER) {
            List<Monster> cands = new ArrayList<>();
            for (Monster m : monsters) if (m.hp > 0 && dist(m.x, m.y, p.x, p.y) <= r) cands.add(m);
            cands.sort((m1, m2) -> Float.compare(dist(m1.x, m1.y, p.x, p.y), dist(m2.x, m2.y, p.x, p.y)));
            for (int i = 0; i < Math.min(4, cands.size()); i++) {
                hit.add(cands.get(i));
                broadcast(join("FX", Protocol.FX_ARROW, f(p.x), f(p.y), f(cands.get(i).x), f(cands.get(i).y)));
            }
        } else {
            Monster t = nearestMonster(p, Rules.CLASS_RANGE[p.cls] + 1f);
            float cx = t != null ? t.x : p.x, cy = t != null ? t.y : p.y;
            for (Monster m : monsters) if (m.hp > 0 && dist(m.x, m.y, cx, cy) <= r) hit.add(m);
            broadcast(join("FX", Protocol.FX_METEOR, f(p.x), f(p.y), f(cx), f(cy)));
        }
        if (hit.isEmpty() && p.cls != Rules.WARRIOR) {
            p.conn.send(join("TXT", f(p.x), f(p.y + 1.2f), Protocol.TXT_XP, "Aucune cible"));
            return;
        }
        p.mp -= cost;
        p.skillCd = Rules.SKILL_COOLDOWN[p.cls];
        p.dirty = true;
        for (Monster m : hit) if (monsters.contains(m) && m.hp > 0) hitMonster(p, m, dmg);
    }

    private void hitMonster(Player p, Monster m, int base) {
        boolean crit = rnd.nextFloat() < 0.12f;
        int dmg = Math.max(1, Math.round(base * (0.85f + rnd.nextFloat() * 0.3f) * (crit ? 1.8f : 1f)));
        m.hp -= dmg;
        broadcast(join("TXT", f(m.x), f(m.y + 0.6f), crit ? Protocol.TXT_CRIT : Protocol.TXT_DMG_MOB, crit ? dmg + "!" : dmg));
        if (m.target == null && !m.returning && !WorldMap.inSafeZone(p.x, p.y, 0)) m.target = p;
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
        // butin pour celui qui achève
        float r = rnd.nextFloat();
        int drop = 0, n = 1;
        if (m.type == Rules.GOLEM) {
            giveItem(killer, Items.weaponId(killer.cls, 3), 1, m);
            drop = Items.BIG_HP; n = 3;
            systemChat(killer.name + " et ses alliés ont vaincu le Golem ancien !");
        } else if (r < 0.10f) drop = Items.SMALL_HP;
        else if (r < 0.17f) drop = Items.SMALL_MP;
        else if (m.type == Rules.SKELETON && r < 0.22f) drop = Items.BIG_HP;
        if (drop != 0) giveItem(killer, drop, n, m);
    }

    private void giveItem(Player p, int id, int n, Monster at) {
        int left = p.inv.add(id, n);
        p.invDirty = true;
        Items.Def d = Items.get(id);
        if (left < n) broadcast(join("TXT", f(at.x), f(at.y + 1.6f), Protocol.TXT_HEAL, "+" + (n - left) + " " + d.name));
        if (left > 0) p.conn.send(join("CHAT", "", "Sac plein : " + d.name + " perdu(e)."));
    }

    private void gainXp(Player p, int xp) {
        if (p.level >= Rules.MAX_LEVEL) return;
        p.xp += xp;
        while (p.level < Rules.MAX_LEVEL && p.xp >= Rules.xpToNext(p.level)) {
            p.xp -= Rules.xpToNext(p.level);
            p.level++;
            p.hp = p.maxHp();
            p.mp = p.maxMp();
            broadcast(join("FX", Protocol.FX_LEVELUP, f(p.x), f(p.y), f(p.x), f(p.y)));
            systemChat(p.name + " atteint le niveau " + p.level + " !");
        }
        if (p.level >= Rules.MAX_LEVEL) p.xp = 0;
    }

    private void damagePlayer(Monster m, Player p) {
        int raw = Math.round(Rules.MOB_ATK[m.type] * (0.8f + rnd.nextFloat() * 0.4f));
        int dmg = Math.max(1, raw - p.def());
        p.hp -= dmg;
        p.sinceHit = 0;
        p.dirty = true;
        broadcast(join("FX", Protocol.FX_BITE, f(m.x), f(m.y), f(p.x), f(p.y)));
        broadcast(join("TXT", f(p.x), f(p.y + 0.7f), Protocol.TXT_DMG_PLAYER, dmg));
        if (p.hp <= 0) {
            int lost = p.gold / 10;
            p.gold -= lost;
            p.hp = p.maxHp();
            p.mp = p.maxMp();
            float[] s = spawnPoint();
            p.x = s[0]; p.y = s[1];
            p.conn.send(join("TP", f(p.x), f(p.y)));
            systemChat(p.name + " a été vaincu par " + Rules.MOB_NAMES[m.type] + (lost > 0 ? " et perd " + lost + " or." : "."));
            for (Monster o : monsters) if (o.target == p) { o.target = null; o.returning = true; }
        }
    }

    // ================================================================ objets & boutiques

    private void useSlot(Player p, int slot) {
        if (!p.inv.valid(slot)) return;
        Items.Def d = Items.get(p.inv.ids[slot]);
        switch (d.type) {
            case Items.T_HP: case Items.T_MP: drink(p, slot, d); break;
            case Items.T_FOOD:
                p.buffT = d.duration; p.buffHp = d.value; p.buffMp = d.value2;
                p.inv.take(slot, 1);
                p.invDirty = true; p.dirty = true;
                p.conn.send(join("TXT", f(p.x), f(p.y + 1f), Protocol.TXT_HEAL, d.name + " !"));
                break;
            default: equip(p, slot, d); break;
        }
    }

    private void drink(Player p, int slot, Items.Def d) {
        if (p.useCd > 0) return;
        boolean hp = d.type == Items.T_HP;
        int max = hp ? p.maxHp() : p.maxMp(), cur = hp ? p.hp : p.mp;
        if (cur >= max) return;
        int gain = Math.min(max - cur, d.value);
        if (hp) p.hp += gain; else p.mp += gain;
        p.useCd = 0.8f;
        p.inv.take(slot, 1);
        p.invDirty = true; p.dirty = true;
        broadcast(join("FX", hp ? Protocol.FX_HEAL : Protocol.FX_MANA, f(p.x), f(p.y), f(p.x), f(p.y)));
        broadcast(join("TXT", f(p.x), f(p.y + 0.8f), hp ? Protocol.TXT_HEAL : Protocol.TXT_XP, "+" + gain));
    }

    private void quickUse(Player p, int type) {
        int best = -1, bestVal = -1;
        for (int i = 0; i < p.inv.size(); i++) {
            Items.Def d = Items.get(p.inv.ids[i]);
            if (d != null && d.type == type && d.value > bestVal) { best = i; bestVal = d.value; }
        }
        if (best >= 0) drink(p, best, Items.get(p.inv.ids[best]));
    }

    private void equip(Player p, int slot, Items.Def d) {
        if (d.cls != p.cls) { p.conn.send(join("CHAT", "", "Cet objet n'est pas fait pour ta classe.")); return; }
        if (p.level < d.level) { p.conn.send(join("CHAT", "", "Niveau " + d.level + " requis pour " + d.name + ".")); return; }
        int old = d.type == Items.T_WEAPON ? p.weapon : p.armor;
        p.inv.take(slot, 1);
        if (old != 0) p.inv.add(old, 1);
        if (d.type == Items.T_WEAPON) p.weapon = d.id; else p.armor = d.id;
        p.hp = Math.min(p.hp, p.maxHp());
        p.invDirty = true; p.dirty = true;
    }

    private void unequip(Player p, boolean armor) {
        int id = armor ? p.armor : p.weapon;
        if (id == 0 || !p.inv.canAdd(id, 1)) return;
        p.inv.add(id, 1);
        if (armor) p.armor = 0; else p.weapon = 0;
        p.hp = Math.min(p.hp, p.maxHp());
        p.invDirty = true; p.dirty = true;
    }

    private WorldMap.Building shopNear(Player p) { return map.shopAt(p.x, p.y, SHOP_RANGE); }

    private void buy(Player p, int itemId) {
        WorldMap.Building b = shopNear(p);
        Items.Def d = Items.get(itemId);
        if (b == null || d == null || !Items.sells(b.type, p.cls, itemId)) return;
        if (p.level < d.level) { p.conn.send(join("CHAT", "", "Niveau " + d.level + " requis.")); return; }
        if (p.gold < d.price) { p.conn.send(join("CHAT", "", "Pas assez d'or (" + d.price + " or).")); return; }
        if (!p.inv.canAdd(itemId, 1)) { p.conn.send(join("CHAT", "", "Ton sac est plein.")); return; }
        p.gold -= d.price;
        p.inv.add(itemId, 1);
        p.invDirty = true; p.dirty = true;
        p.conn.send(join("TXT", f(p.x), f(p.y + 0.9f), Protocol.TXT_GOLD, "-" + d.price + " or"));
    }

    private void sell(Player p, int slot) {
        WorldMap.Building b = shopNear(p);
        if (b == null || b.type == WorldMap.B_BANK || !p.inv.valid(slot)) return;
        Items.Def d = Items.get(p.inv.ids[slot]);
        p.inv.take(slot, 1);
        p.gold += d.sellPrice();
        p.invDirty = true; p.dirty = true;
        p.conn.send(join("TXT", f(p.x), f(p.y + 0.9f), Protocol.TXT_GOLD, "+" + d.sellPrice() + " or"));
    }

    private void bankMove(Player p, int slot, boolean deposit) {
        WorldMap.Building b = shopNear(p);
        if (b == null || b.type != WorldMap.B_BANK) return;
        Inventory from = deposit ? p.inv : p.bank, to = deposit ? p.bank : p.inv;
        if (!from.valid(slot)) return;
        int id = from.ids[slot], n = from.counts[slot];
        int left = to.add(id, n);
        from.take(slot, n - left);
        if (left > 0) p.conn.send(join("CHAT", "", deposit ? "Le dépôt est plein." : "Ton sac est plein."));
        p.invDirty = true;
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
        if (WorldMap.inSafeZone(x, y, 3f)) return false;
        float d = WorldMap.distToVillage(x, y);
        if (dist(x, y, WorldMap.RUINS_X, WorldMap.RUINS_Y) < 9f) return false;
        switch (type) {
            case Rules.SLIME: return d < 28f;
            case Rules.WOLF: return d >= 28f && d < 44f;
            default: return d >= 44f;
        }
    }

    private boolean mobCanStand(float x, float y, float r) {
        return map.canStand(x, y, r) && !WorldMap.inSafeZone(x, y, r + 0.2f);
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
                if (!inZone(type, x, y) || !mobCanStand(x, y, Rules.MOB_RADIUS[type])) continue;
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

    /** Pour les tests : position d'un monstre (ou null s'il n'existe plus). */
    float[] monsterPos(int id) {
        for (Monster m : monsters) if (m.id == id) return new float[]{m.x, m.y};
        return null;
    }

    private void updateMonster(Monster m) {
        m.atkCd -= DT;
        float speed = Rules.MOB_SPEED[m.type];
        float leash = m.type == Rules.GOLEM ? 10f : 15f;

        if (m.target != null && (!players.contains(m.target) || dist(m.x, m.y, m.homeX, m.homeY) > leash
                || WorldMap.inSafeZone(m.target.x, m.target.y, 0))) {
            m.target = null;
            m.returning = true;
        }
        if (m.target == null && !m.returning) {
            Player best = null;
            float bestD = Rules.MOB_AGGRO[m.type];
            for (Player p : players) {
                if (!p.joined || WorldMap.inSafeZone(p.x, p.y, 0)) continue;
                float d = dist(p.x, p.y, m.x, m.y);
                if (d < bestD) { bestD = d; best = p; }
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
        if (mobCanStand(m.x + mx, m.y + my, r)) { m.x += mx; m.y += my; }
        else if (mobCanStand(m.x + mx, m.y, r)) m.x += mx;
        else if (mobCanStand(m.x, m.y + my, r)) m.y += my;
        else if (m.target == null) m.wanderT = 0; // bloqué : change de direction
        else { m.target = null; m.returning = true; } // la cible est hors d'atteinte (ex. village)
    }

    // ================================================================ envoi

    private void broadcastSnapshots() {
        StringBuilder ps = new StringBuilder();
        for (Player p : players) {
            if (!p.joined) continue;
            if (ps.length() > 0) ps.append(';');
            ps.append(p.id).append(',').append(p.name).append(',').append(p.cls).append(',')
                    .append(f(p.x)).append(',').append(f(p.y)).append(',').append(p.hp).append(',')
                    .append(p.maxHp()).append(',').append(p.level).append(',').append(p.dir).append(',')
                    .append(p.moving ? 1 : 0).append(',').append(p.look).append(',')
                    .append(Items.tier(p.weapon)).append(',').append(Items.tier(p.armor));
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
        p.conn.send(join("YOU", p.hp, p.maxHp(), p.mp, p.maxMp(), p.level, p.xp, Rules.xpToNext(p.level), p.gold,
                f(Math.max(0, p.buffT)), p.atk(), p.def(), f(Math.max(0, p.skillCd))));
    }

    private void sendInv(Player p) {
        p.invDirty = false;
        p.conn.send(join("INV", p.inv.encode(), p.bank.encode(), p.weapon, p.armor));
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
