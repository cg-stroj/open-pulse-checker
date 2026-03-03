package io.openpulsechecker.service;

import io.openpulsechecker.domain.HttpMethod;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class DefaultHttpCheckClient implements HttpCheckClient {

    private final HttpClient httpClient;

    public DefaultHttpCheckClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public HttpCheckOutcome execute(String targetUrl, int timeoutMs, HttpMethod httpMethod, String expectedResponseKeyword) {
        long start = System.nanoTime();
        try {
            HttpMethod resolvedMethod = httpMethod != null ? httpMethod : HttpMethod.GET;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .method(resolvedMethod.name(), HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = (System.nanoTime() - start) / 1_000_000;

            boolean up = response.statusCode() >= 200 && response.statusCode() < 400;
            String keyword = expectedResponseKeyword == null ? null : expectedResponseKeyword.trim();
            if (keyword != null && !keyword.isBlank()) {
                String responseBody = response.body() == null ? "" : response.body();
                if (!responseBody.contains(keyword)) {
                    return new HttpCheckOutcome(
                            false,
                            response.statusCode(),
                            latencyMs,
                            "Expected response keyword not found: " + keyword);
                }
            }

            return new HttpCheckOutcome(up, response.statusCode(), latencyMs, null);
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            return new HttpCheckOutcome(false, null, latencyMs, sanitizeError(ex));
        }
    }

    private String sanitizeError(Exception ex) {
        String msg = ex.getClass().getSimpleName();
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            msg += ": " + ex.getMessage();
        }
        return msg.length() > 1024 ? msg.substring(0, 1024) : msg;
    }
}
