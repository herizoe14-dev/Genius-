package com.genius.echos.net;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Client réseau : se connecte (sur un fil séparé) et met les messages reçus en file. */
public class GameClient implements Connection.Listener {
    public final ConcurrentLinkedQueue<String> inbox = new ConcurrentLinkedQueue<>();
    private volatile Connection conn;
    private volatile boolean connecting = true;
    private volatile boolean closed;
    private volatile String error;

    public GameClient(final String host, final int port, final String helloLine) {
        Thread t = new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 5000);
                Connection c = new Connection(s, this);
                c.send(helloLine);
                conn = c;
                c.start();
            } catch (Exception e) {
                error = "Connexion impossible à " + host + " (" + e.getClass().getSimpleName() + ")";
                closed = true;
            } finally {
                connecting = false;
            }
        }, "echos-connect");
        t.setDaemon(true);
        t.start();
    }

    public boolean isConnecting() { return connecting; }
    public boolean isClosed() { return closed; }
    public String getError() { return error; }

    public void send(Object... parts) {
        Connection c = conn;
        if (c != null) c.send(Protocol.join(parts));
    }

    public void close() {
        closed = true;
        Connection c = conn;
        if (c != null) c.close();
    }

    @Override public void onLine(Connection c, String line) {
        if (line.startsWith("KICK\t")) error = line.substring(5);
        inbox.add(line);
    }

    @Override public void onClosed(Connection c) {
        if (error == null) error = "Connexion perdue avec l'hôte.";
        closed = true;
    }
}
