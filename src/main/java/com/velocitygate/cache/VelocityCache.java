package com.velocitygate.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, high-performance cache for tracking request velocity per IP address.
 * <p>
 * Uses a sliding window approach with automatic expiration of old entries to maintain
 * memory efficiency while tracking request patterns for rate-limiting and anomaly detection.
 * <p>
 * This class implements a 1-second sliding window for request counting and performs periodic
 * cleanup of expired entries every 5 seconds to prevent unbounded memory growth.
 *
 * @since 1.0.0
 */
public class VelocityCache {

    private static final long WINDOW_DURATION_MS = 1000L;
    private static final int CLEANUP_INTERVAL_MS = 5000;
    private static final long CLEANUP_THRESHOLD_MS = 60000L;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> requestTimestamps;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile long lastCleanupTime = System.currentTimeMillis();

    /**
     * Constructs a new VelocityCache instance and initializes the background cleanup thread.
     */
    public VelocityCache() {
        this.requestTimestamps = new ConcurrentHashMap<>();
        startCleanupTask();
    }

    /**
     * Records a request timestamp for the given IP address.
     * <p>
     * This method updates the sliding window for the IP and returns the total count
     * of requests within the 1-second window, including the current request.
     *
     * @param ip the client IP address
     * @return the current request count within the 1-second sliding window, or 0 if IP is null/blank
     */
    public int recordRequest(String ip) {
        if (ip == null || ip.isBlank()) {
            return 0;
        }

        long currentTimeMs = System.currentTimeMillis();
        lock.readLock().lock();
        try {
            CopyOnWriteArrayList<Long> timestamps =
                    requestTimestamps.computeIfAbsent(ip, k -> new CopyOnWriteArrayList<>());

            timestamps.add(currentTimeMs);
            long windowStart = currentTimeMs - WINDOW_DURATION_MS;
            timestamps.removeIf(timestamp -> timestamp < windowStart);

            return timestamps.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retrieves the current request count for the given IP address within the 1-second window.
     *
     * @param ip the client IP address
     * @return the request count within the 1-second sliding window, or 0 if IP is not found
     */
    public int getRequestCount(String ip) {
        if (ip == null || ip.isBlank()) {
            return 0;
        }

        lock.readLock().lock();
        try {
            CopyOnWriteArrayList<Long> timestamps = requestTimestamps.get(ip);
            if (timestamps == null) {
                return 0;
            }

            long currentTimeMs = System.currentTimeMillis();
            long windowStart = currentTimeMs - WINDOW_DURATION_MS;

            return (int) timestamps.stream().filter(t -> t >= windowStart).count();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Performs periodic cleanup of expired entries to prevent memory leaks.
     * <p>
     * This method removes IP entries with no request timestamps within the cleanup threshold
     * (60 seconds), ensuring that long-idle IPs are evicted from memory.
     */
    private void cleanup() {
        lock.writeLock().lock();
        try {
            long currentTimeMs = System.currentTimeMillis();
            long expirationThreshold = currentTimeMs - CLEANUP_THRESHOLD_MS;

            requestTimestamps.forEach(
                    (ip, timestamps) -> {
                        timestamps.removeIf(timestamp -> timestamp < expirationThreshold);
                        if (timestamps.isEmpty()) {
                            requestTimestamps.remove(ip);
                        }
                    });
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Initializes and starts the background cleanup daemon thread.
     * <p>
     * The cleanup thread runs every 5 seconds to remove expired entries from the cache.
     */
    private void startCleanupTask() {
        Thread cleanupThread =
                new Thread(
                        () -> {
                            while (true) {
                                try {
                                    long now = System.currentTimeMillis();
                                    if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
                                        cleanup();
                                        lastCleanupTime = now;
                                    }
                                    Thread.sleep(CLEANUP_INTERVAL_MS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        },
                        "VelocityCache-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /**
     * Clears all cached request data for all IP addresses.
     * <p>
     * This method is provided for testing and administrative purposes.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            requestTimestamps.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retrieves the total number of IP addresses currently tracked in the cache.
     *
     * @return the count of distinct IP addresses with active request timestamps
     */
    public int getTrackedIpCount() {
        lock.readLock().lock();
        try {
            return requestTimestamps.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
