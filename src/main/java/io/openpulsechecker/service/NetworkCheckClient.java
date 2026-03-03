package io.openpulsechecker.service;

public interface NetworkCheckClient {
    HttpCheckOutcome executeTcp(String target, int timeoutMs);

    HttpCheckOutcome executePing(String targetUrl, int timeoutMs);
}
