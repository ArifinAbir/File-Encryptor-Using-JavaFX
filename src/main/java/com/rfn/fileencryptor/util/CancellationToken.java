package com.rfn.fileencryptor.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple cancellation token for cooperative cancellation of long-running operations.
 */
public class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
