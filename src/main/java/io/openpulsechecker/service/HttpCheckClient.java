package io.openpulsechecker.service;

public interface HttpCheckClient {
    HttpCheckOutcome execute(String targetUrl, int timeoutMs);
}
