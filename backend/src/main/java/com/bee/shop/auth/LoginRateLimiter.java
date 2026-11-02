package com.bee.shop.auth;

import com.bee.shop.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Batas percobaan login per IP (sliding window, in-memory). Melengkapi lockout per akun
 * untuk menahan password spraying ke banyak username. Untuk multi-instance, pindahkan ke Redis.
 */
@Component
public class LoginRateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);

    private final int maxAttempts;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public LoginRateLimiter(AppProperties props) {
        this.maxAttempts = props.admin().loginAttemptsPerIp();
    }

    LoginRateLimiter(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean tryAcquire(String ip) {
        String key = ip == null ? "unknown" : ip;
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);
        Deque<Instant> q = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
            if (q.size() >= maxAttempts) return false;
            q.addLast(now);
        }
        if (attempts.size() > 10_000) attempts.entrySet().removeIf(e -> e.getValue().isEmpty());
        return true;
    }

    void reset() {
        attempts.clear();
    }
}
