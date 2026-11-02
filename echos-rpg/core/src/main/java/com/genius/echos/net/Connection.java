package com.genius.echos.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Connexion TCP ligne par ligne avec un fil d'écriture dédié
 * (aucune écriture réseau n'a donc lieu sur le fil de rendu).
 */
public class Connection {
    public interface Listener {
        void onLine(Connection c, String line);
        void onClosed(Connection c);
    }

    private final Socket socket;
    private final LinkedBlockingQueue<String> out = new LinkedBlockingQueue<>();
    private volatile boolean open = true;
    private final Listener listener;
    public Object attachment;

    public Connection(Socket socket, Listener listener) throws IOException {
        this.socket = socket;
        this.listener = listener;
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        Thread reader = new Thread(() -> {
            try {
                String line;
                while (open && (line = in.readLine()) != null) {
                    if (line.length() > 8192) continue;
                    listener.onLine(this, line);
                }
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }, "echos-read");
        reader.setDaemon(true);

        Thread writer = new Thread(() -> {
            try {
                while (open) {
                    String s = out.poll(500, TimeUnit.MILLISECONDS);
                    if (s == null) continue;
                    w.write(s); w.write('\n');
                    String more;
                    while ((more = out.poll()) != null) { w.write(more); w.write('\n'); }
                    w.flush();
                }
            } catch (Exception ignored) {
            } finally {
                close();
            }
        }, "echos-write");
        writer.setDaemon(true);

        this.reader = reader;
        this.writer = writer;
    }

    private final Thread reader, writer;

    /** Démarre la lecture/écriture. À appeler une fois la connexion enregistrée. */
    public void start() {
        reader.start();
        writer.start();
    }

    public void send(String line) {
        if (open) out.offer(line);
    }

    public int pending() { return out.size(); }

    public boolean isOpen() { return open; }

    public String remoteAddress() {
        return socket.getInetAddress() == null ? "?" : socket.getInetAddress().getHostAddress();
    }

    public void close() {
        boolean wasOpen;
        synchronized (this) { wasOpen = open; open = false; }
        if (!wasOpen) return;
        try { socket.close(); } catch (IOException ignored) {}
        listener.onClosed(this);
    }
}
