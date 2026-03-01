package io.openpulsechecker.setup;

public class SetupLockedException extends IllegalStateException {
    public SetupLockedException(String message) {
        super(message);
    }
}
