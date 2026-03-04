package io.openpulsechecker.service;

public class MonitorDeletionBlockedException extends RuntimeException {

    public MonitorDeletionBlockedException(String message) {
        super(message);
    }
}
