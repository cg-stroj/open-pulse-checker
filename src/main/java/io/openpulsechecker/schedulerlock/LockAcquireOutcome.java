package io.openpulsechecker.schedulerlock;

public enum LockAcquireOutcome {
    ACQUIRED,
    STOLEN,
    CONTENDED
}
