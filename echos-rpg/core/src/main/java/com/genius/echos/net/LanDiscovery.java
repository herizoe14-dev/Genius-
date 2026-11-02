package com.genius.echos.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Découverte des parties sur le réseau local (même Wi-Fi ou partage de connexion)
 * par diffusion UDP. Aucune connexion Internet n'est nécessaire.
 */
public final class LanDiscovery {
    private LanDiscovery() {}

    public static final class Host {
        public final String address;
        public final String name;
        public final int port;
        public final int players;
        public final long seen;

        Host(String address, String name, int port, int players) {
            this.address = address; this.name = name; this.port = port; this.players = players;
            this.seen = System.currentTimeMillis();
        }
    }

    /** Côté hôte : répond aux requêtes de découverte. */
    public static final class Responder {
        private volatile boolean running = true;
        private DatagramSocket socket;

        public Responder(final int udpPort, final Supplier<String> info) {
            Thread t = new Thread(() -> {
                try {
                    socket = new DatagramSocket(null);
                    socket.setReuseAddress(true);
                    socket.setBroadcast(true);
                    socket.bind(new InetSocketAddress(udpPort));
                    socket.setSoTimeout(500);
                    byte[] buf = new byte[512];
                    while (running) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        try { socket.receive(p); } catch (SocketTimeoutException e) { continue; }
                        String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                        if (!msg.startsWith(Protocol.DISCOVER_ASK)) continue;
                        byte[] reply = (Protocol.DISCOVER_REPLY + "\t" + info.get()).getBytes(StandardCharsets.UTF_8);
                        socket.send(new DatagramPacket(reply, reply.length, p.getAddress(), p.getPort()));
                    }
                } catch (Exception ignored) {
                } finally {
                    if (socket != null) socket.close();
                }
            }, "echos-discovery-responder");
            t.setDaemon(true);
            t.start();
        }

        public void stop() {
            running = false;
            if (socket != null) socket.close();
        }
    }

    /** Côté joueur : cherche les parties en continu. */
    public static final class Scanner {
        private volatile boolean running = true;
        private final Map<String, Host> hosts = Collections.synchronizedMap(new LinkedHashMap<>());

        public Scanner(final int udpPort) {
            Thread t = new Thread(() -> {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setBroadcast(true);
                    socket.setSoTimeout(250);
                    byte[] ask = Protocol.DISCOVER_ASK.getBytes(StandardCharsets.UTF_8);
                    byte[] buf = new byte[512];
                    long lastSend = 0;
                    while (running) {
                        long now = System.currentTimeMillis();
                        if (now - lastSend > 1000) {
                            lastSend = now;
                            for (InetAddress target : broadcastTargets()) {
                                try { socket.send(new DatagramPacket(ask, ask.length, target, udpPort)); } catch (Exception ignored) {}
                            }
                            synchronized (hosts) { hosts.values().removeIf(h -> now - h.seen > 4000); }
                        }
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        try { socket.receive(p); } catch (SocketTimeoutException e) { continue; }
                        String[] parts = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).split("\t");
                        if (parts.length < 4 || !parts[0].equals(Protocol.DISCOVER_REPLY)) continue;
                        String addr = p.getAddress().getHostAddress();
                        hosts.put(addr, new Host(addr, parts[1], Protocol.i(parts[2], Protocol.TCP_PORT), Protocol.i(parts[3], 0)));
                    }
                } catch (Exception ignored) {
                }
            }, "echos-discovery-scanner");
            t.setDaemon(true);
            t.start();
        }

        public List<Host> hosts() {
            synchronized (hosts) { return new ArrayList<>(hosts.values()); }
        }

        public void stop() { running = false; }
    }

    static List<InetAddress> broadcastTargets() {
        List<InetAddress> out = new ArrayList<>();
        try { out.add(InetAddress.getByName("255.255.255.255")); } catch (Exception ignored) {}
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e != null && e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b != null && !out.contains(b)) out.add(b);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Adresses IPv4 locales (pour afficher l'IP de l'hôte). */
    public static List<String> localAddresses() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e != null && e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.getAddress().length == 4) out.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) {}
        return out;
    }
}
