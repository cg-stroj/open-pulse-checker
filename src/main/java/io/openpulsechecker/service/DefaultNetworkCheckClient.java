package io.openpulsechecker.service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class DefaultNetworkCheckClient implements NetworkCheckClient {

    @Override
    public HttpCheckOutcome executeTcp(String target, int timeoutMs) {
        long start = System.nanoTime();
        int safeTimeout = Math.max(timeoutMs, 1);
        try {
            HostPort hostPort = parseHostPort(target);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(hostPort.host(), hostPort.port()), safeTimeout);
            }
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            return new HttpCheckOutcome(true, null, latencyMs, null);
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            return new HttpCheckOutcome(false, null, latencyMs, sanitizeError(ex));
        }
    }

    @Override
    public HttpCheckOutcome executePing(String targetUrl, int timeoutMs) {
        long start = System.nanoTime();
        int safeTimeout = Math.max(timeoutMs, 1);
        try {
            URI uri = URI.create(targetUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Target URL host is required");
            }

            InetAddress address = InetAddress.getByName(host);
            boolean reachable = address.isReachable(safeTimeout);
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            if (reachable) {
                return new HttpCheckOutcome(true, null, latencyMs, null);
            }
            return new HttpCheckOutcome(false, null, latencyMs, "Host unreachable");
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            return new HttpCheckOutcome(false, null, latencyMs, sanitizeError(ex));
        }
    }

    private HostPort parseHostPort(String target) {
        String[] parts = target.split(":");
        if (parts.length != 2 || parts[0].isBlank()) {
            throw new IllegalArgumentException("TCP target must be host:port");
        }
        int port = Integer.parseInt(parts[1]);
        return new HostPort(parts[0], port);
    }

    private String sanitizeError(Exception ex) {
        String msg = ex.getClass().getSimpleName();
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            msg += ": " + ex.getMessage();
        }
        return msg.length() > 1024 ? msg.substring(0, 1024) : msg;
    }

    private record HostPort(String host, int port) {
    }
}
