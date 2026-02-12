package com.velocitygate.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VelocityCache.
 *
 * @since 1.0.0
 */
@DisplayName("VelocityCache Tests")
class VelocityCacheTests {

    private VelocityCache velocityCache;

    @BeforeEach
    void setUp() {
        velocityCache = new VelocityCache();
        velocityCache.clear();
    }

    @Test
    @DisplayName("Records a single request")
    void testRecordSingleRequest() {
        int count = velocityCache.recordRequest("192.168.1.1");
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Counts multiple requests from the same IP within the sliding window")
    void testRecordMultipleRequests() {
        for (int i = 0; i < 10; i++) {
            velocityCache.recordRequest("192.168.1.1");
        }
        assertEquals(10, velocityCache.getRequestCount("192.168.1.1"));
    }

    @Test
    @DisplayName("Tracks requests from multiple IPs independently")
    void testMultipleIPs() {
        velocityCache.recordRequest("192.168.1.1");
        velocityCache.recordRequest("192.168.1.1");
        velocityCache.recordRequest("192.168.1.2");

        assertEquals(2, velocityCache.getRequestCount("192.168.1.1"));
        assertEquals(1, velocityCache.getRequestCount("192.168.1.2"));
    }

    @Test
    @DisplayName("Handles null IP addresses gracefully")
    void testNullIP() {
        int count = velocityCache.recordRequest(null);
        assertEquals(0, count);
    }

    @Test
    @DisplayName("Handles blank IP addresses gracefully")
    void testBlankIP() {
        int count = velocityCache.recordRequest("   ");
        assertEquals(0, count);
    }

    @Test
    @DisplayName("Returns zero for untracked IP addresses")
    void testUntrackedIP() {
        assertEquals(0, velocityCache.getRequestCount("192.168.1.99"));
    }

    @Test
    @DisplayName("Clears all cached data")
    void testClear() {
        velocityCache.recordRequest("192.168.1.1");
        velocityCache.recordRequest("192.168.1.2");
        
        velocityCache.clear();
        
        assertEquals(0, velocityCache.getTrackedIpCount());
        assertEquals(0, velocityCache.getRequestCount("192.168.1.1"));
    }

    @Test
    @DisplayName("Tracks the correct number of distinct IP addresses")
    void testTrackedIpCount() {
        velocityCache.recordRequest("192.168.1.1");
        velocityCache.recordRequest("192.168.1.2");
        velocityCache.recordRequest("192.168.1.3");
        
        assertEquals(3, velocityCache.getTrackedIpCount());
    }

    @Test
    @DisplayName("Maintains thread-safety with concurrent requests")
    void testThreadSafety() throws InterruptedException {
        int threads = 10;
        int requestsPerThread = 100;
        
        Thread[] threadArray = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final String ip = "192.168.1.1";
            threadArray[i] = new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    velocityCache.recordRequest(ip);
                }
            });
            threadArray[i].start();
        }
        
        for (Thread t : threadArray) {
            t.join();
        }
        
        assertEquals(threads * requestsPerThread, velocityCache.getRequestCount("192.168.1.1"));
    }
}
