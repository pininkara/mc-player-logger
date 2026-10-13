package dev.presencelogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.UUID;

// No test framework dependency: exercises the real file writer and configuration loader.
public final class LogWriterVerification {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("presence-log-verification-");
        try {
            Path logs = root.resolve("logs");
            UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
            var writer = new CsvLogWriter(logs, ZoneId.of("Asia/Shanghai"));
            writer.append(Instant.parse("2026-10-12T15:59:59.123Z"), "LOGIN", "玩家,\"A\"", uuid);
            // A new instance simulates restarting the server; it must preserve the file and header.
            writer = new CsvLogWriter(logs, ZoneId.of("Asia/Shanghai"));
            writer.append(Instant.parse("2026-10-12T15:59:59.999Z"), "LOGOUT", "Steve", uuid);
            writer.append(Instant.parse("2026-10-12T16:00:00Z"), "LOGIN", "Alex", uuid);
            String first = Files.readString(logs.resolve("2026-10-12.csv"), StandardCharsets.UTF_8);
            require(first.lines().count() == 3, "append and single header");
            require(first.contains("2026-10-12T23:59:59.123+08:00,\"LOGIN\",\"玩家,\"\"A\"\"\"," + uuid),
                    "timestamp, UUID, UTF-8 and CSV escaping");
            require(Files.readString(logs.resolve("2026-10-13.csv")).contains("2026-10-13T00:00:00.000+08:00"),
                    "rollover uses configured timezone");
            Files.writeString(logs.resolve("2026-10-14.csv"), "");
            writer.append(Instant.parse("2026-10-13T16:00:00Z"), "LOGOUT", "Alex", uuid);
            require(Files.readString(logs.resolve("2026-10-14.csv")).startsWith("timestamp,event,player,uuid\n"),
                    "empty file gets header");
            Path config = root.resolve("config/settings.properties");
            var settings = LoggerSettings.load(config);
            require(settings.timeZone().equals(ZoneId.of("Asia/Shanghai")), "default timezone");
            require(settings.resolveDirectory(root).equals(root.resolve("logs/player-presence").toAbsolutePath()),
                    "relative directory");
            Files.writeString(config, "logDirectory=custom/logs\ntimeZone=UTC\n");
            settings = LoggerSettings.load(config);
            require(settings.timeZone().equals(ZoneId.of("UTC")), "custom timezone");
            require(settings.resolveDirectory(root).equals(root.resolve("custom/logs").toAbsolutePath()),
                    "custom directory");
            Files.writeString(config, "timeZone=invalid/timezone\n");
            try {
                LoggerSettings.load(config);
                throw new AssertionError("invalid timezone accepted");
            } catch (java.time.DateTimeException expected) {
                // Invalid settings are rejected and the mod can fall back to defaults.
            }
            Path blocker = root.resolve("not-a-directory");
            Files.writeString(blocker, "blocker");
            try {
                new CsvLogWriter(blocker, ZoneId.of("UTC")).append(Instant.now(), "LOGIN", "Steve", uuid);
                throw new AssertionError("unwritable log directory accepted");
            } catch (java.io.IOException expected) {
                // Errors propagate to the mod's catch/log handler.
            }
            System.out.println("PASS: append, restart, rollover, escaping, UTF-8, settings, I/O failure");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
