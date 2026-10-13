package dev.presencelogger;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerSessions {
    private final Map<UUID, Instant> loginTimes = new HashMap<>();

    public synchronized void login(UUID player, Instant timestamp) {
        loginTimes.put(player, timestamp);
    }

    public synchronized String logoutInfo(UUID player, Instant timestamp) {
        Instant login = loginTimes.remove(player);
        if (login == null) {
            return "unknown";
        }
        long milliseconds = Duration.between(login, timestamp).toMillis();
        return BigDecimal.valueOf(milliseconds, 3).toPlainString() + "s";
    }

    public synchronized void clear() {
        loginTimes.clear();
    }
}
