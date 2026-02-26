package io.openpulsechecker.service;

public record HttpCheckOutcome(boolean up, Integer statusCode, Long latencyMs, String error) {
}
