package com.genius.echos.net;

import com.genius.echos.world.Rules;
import com.genius.echos.world.WorldMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * État du monde vu par un client, reconstruit à partir des messages du serveur.
 * Indépendant de libGDX (testable sur une JVM simple).
 */
public class ClientState {
    public static final class Avatar {
        public int id, cls, hp, maxHp, level, facing = 1;
        public String name = "";
        public float x, y, tx, ty;
        public boolean moving;
        public float anim;
    }

    public static final class Mob {
        public int id, type, hp, maxHp, facing = 1;
        public float x, y, tx, ty, anim, hurt;
    }

    public static final class Fx {
        public int kind; public float x1, y1, x2, y2, t, life;
    }

    public static final class FloatText {
        public float x, y, t; public int color; public String text;
    }

    public static final class ChatLine {
        public final String name, text; public float age;
        ChatLine(String name, String text) { this.name = name; this.text = text; }
    }

    public int myId = -1;
    public WorldMap map;
    public boolean welcomed;

    // Le joueur local (position décidée localement, statistiques par le serveur)
    public float myX, myY;
    public int myFacing = 1;
    public boolean myMoving;
    public int hp = 1, maxHp = 1, level = 1, xp, xpNext = 20, gold, potions;
    public boolean statsReceived;

    public final Map<Integer, Avatar> players = new HashMap<>();
    public final Map<Integer, Mob> mobs = new HashMap<>();
    public final List<Fx> fxs = new ArrayList<>();
    public final List<FloatText> texts = new ArrayList<>();
    public final List<ChatLine> chat = new ArrayList<>();
    public String kickReason;

    public void process(String line) {
        String[] a = line.split("\t", -1);
        switch (a[0]) {
            case "WELCOME":
                myId = Protocol.i(a[1], -1);
                long seed = Long.parseLong(a[2]);
                if (map == null || map.seed != seed) map = new WorldMap(seed);
                myX = Protocol.fl(a[3], WorldMap.VILLAGE_X);
                myY = Protocol.fl(a[4], WorldMap.VILLAGE_Y);
                welcomed = true;
                break;
            case "YOU":
                hp = Protocol.i(a[1], hp); maxHp = Protocol.i(a[2], maxHp);
                level = Protocol.i(a[3], level); xp = Protocol.i(a[4], xp);
                xpNext = Protocol.i(a[5], xpNext); gold = Protocol.i(a[6], gold);
                potions = Protocol.i(a[7], potions);
                statsReceived = true;
                break;
            case "S":
                readSnapshot(a.length > 1 ? a[1] : "", a.length > 2 ? a[2] : "");
                break;
            case "TP":
                myX = Protocol.fl(a[1], myX);
                myY = Protocol.fl(a[2], myY);
                break;
            case "FX": {
                Fx fx = new Fx();
                fx.kind = Protocol.i(a[1], 0);
                fx.x1 = Protocol.fl(a[2], 0); fx.y1 = Protocol.fl(a[3], 0);
                fx.x2 = Protocol.fl(a[4], 0); fx.y2 = Protocol.fl(a[5], 0);
                fx.life = fx.kind == Protocol.FX_LEVELUP ? 1.2f : fx.kind == Protocol.FX_HEAL ? 0.6f : 0.25f;
                fxs.add(fx);
                if (fx.kind != Protocol.FX_HEAL && fx.kind != Protocol.FX_LEVELUP) {
                    for (Mob m : mobs.values())
                        if (Math.abs(m.tx - fx.x2) < 0.3f && Math.abs(m.ty - fx.y2) < 0.3f) m.hurt = 0.2f;
                }
                break;
            }
            case "TXT": {
                FloatText t = new FloatText();
                t.x = Protocol.fl(a[1], 0); t.y = Protocol.fl(a[2], 0);
                t.color = Protocol.i(a[3], 0); t.text = a.length > 4 ? a[4] : "";
                texts.add(t);
                break;
            }
            case "CHAT":
                addChat(a.length > 1 ? a[1] : "", a.length > 2 ? a[2] : "");
                break;
            case "KICK":
                kickReason = a.length > 1 ? a[1] : "Expulsé";
                break;
            default: break;
        }
    }

    public void addChat(String name, String text) {
        chat.add(new ChatLine(name, text));
        while (chat.size() > 30) chat.remove(0);
    }

    private void readSnapshot(String ps, String ms) {
        Set<Integer> seen = new HashSet<>();
        if (!ps.isEmpty()) for (String e : ps.split(";")) {
            String[] v = e.split(",");
            if (v.length < 10) continue;
            int id = Protocol.i(v[0], -1);
            seen.add(id);
            Avatar av = players.get(id);
            boolean fresh = av == null;
            if (fresh) { av = new Avatar(); av.id = id; players.put(id, av); }
            av.name = v[1]; av.cls = Protocol.i(v[2], 0);
            av.tx = Protocol.fl(v[3], av.tx); av.ty = Protocol.fl(v[4], av.ty);
            av.hp = Protocol.i(v[5], 0); av.maxHp = Protocol.i(v[6], 1);
            av.level = Protocol.i(v[7], 1); av.facing = Protocol.i(v[8], 1);
            av.moving = v[9].equals("1");
            if (fresh) { av.x = av.tx; av.y = av.ty; }
        }
        players.keySet().retainAll(seen);

        Set<Integer> seenM = new HashSet<>();
        if (!ms.isEmpty()) for (String e : ms.split(";")) {
            String[] v = e.split(",");
            if (v.length < 7) continue;
            int id = Protocol.i(v[0], -1);
            seenM.add(id);
            Mob m = mobs.get(id);
            boolean fresh = m == null;
            if (fresh) { m = new Mob(); m.id = id; mobs.put(id, m); }
            m.type = Protocol.i(v[1], 0);
            m.tx = Protocol.fl(v[2], m.tx); m.ty = Protocol.fl(v[3], m.ty);
            m.hp = Protocol.i(v[4], 0); m.maxHp = Protocol.i(v[5], 1);
            m.facing = Protocol.i(v[6], 1);
            if (fresh) { m.x = m.tx; m.y = m.ty; }
        }
        mobs.keySet().retainAll(seenM);
    }

    /** Avance les animations et l'interpolation. */
    public void update(float dt) {
        float k = Math.min(1f, dt * 12f);
        for (Avatar av : players.values()) {
            if (av.id == myId) { av.x = myX; av.y = myY; av.tx = myX; av.ty = myY; }
            else {
                float dx = av.tx - av.x, dy = av.ty - av.y;
                if (dx * dx + dy * dy > 16f) { av.x = av.tx; av.y = av.ty; }
                else { av.x += dx * k; av.y += dy * k; }
            }
            av.anim += dt;
        }
        for (Mob m : mobs.values()) {
            m.x += (m.tx - m.x) * k;
            m.y += (m.ty - m.y) * k;
            m.anim += dt;
            m.hurt = Math.max(0, m.hurt - dt);
        }
        for (Iterator<Fx> it = fxs.iterator(); it.hasNext(); ) {
            Fx fx = it.next();
            fx.t += dt;
            if (fx.t >= fx.life) it.remove();
        }
        for (Iterator<FloatText> it = texts.iterator(); it.hasNext(); ) {
            FloatText t = it.next();
            t.t += dt;
            if (t.t > 1.0f) it.remove();
        }
        for (ChatLine c : chat) c.age += dt;
    }

    /** Déplacement local avec collisions (glissement le long des murs). */
    public void moveMe(float dx, float dy, float dt, int cls) {
        if (map == null) return;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        myMoving = len > 0.15f;
        if (!myMoving) return;
        if (len > 1f) { dx /= len; dy /= len; }
        float speed = Rules.CLASS_SPEED[cls];
        float mx = dx * speed * dt, my = dy * speed * dt;
        float r = Rules.PLAYER_RADIUS;
        if (Math.abs(mx) > 0.0001f) myFacing = mx < 0 ? -1 : 1;
        if (map.canStand(myX + mx, myY, r)) myX += mx;
        if (map.canStand(myX, myY + my, r)) myY += my;
    }
}
