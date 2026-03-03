package io.openpulsechecker.service;

import io.openpulsechecker.domain.HttpMethod;

public interface HttpCheckClient {
    HttpCheckOutcome execute(String targetUrl, int timeoutMs, HttpMethod httpMethod, String expectedResponseKeyword);
}
