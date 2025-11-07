package com.rfn.fileencryptor.util;

public class ProgressTracker {

    /**
     * Callback interface for progress updates
     */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * Called when progress is updated
         * @param percentage Progress percentage (0-100)
         * @param processed Bytes processed
         * @param total Total bytes
         * @param eta Estimated time remaining in seconds
         */
        void onProgress(double percentage, long processed, long total, long eta);
    }

    /**
     * Calculate progress percentage
     */
    public static double calculatePercentage(long processed, long total) {
        if (total == 0) return 0;
        return (processed * 100.0) / total;
    }

    /**
     * Estimate time remaining
     */
    public static long estimateTimeRemaining(long processed, long total, long elapsedMs) {
        if (processed == 0) return -1;
        long totalEstimatedMs = (elapsedMs * total) / processed;
        return (totalEstimatedMs - elapsedMs) / 1000; // Convert to seconds
    }
}
