package dev.presencelogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.UUID;

public final class LogWriterVerification {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("presence-log-verification-");
        try {
            ZoneId zone = ZoneId.of("Asia/Shanghai");
            Instant startedAt = Instant.parse("2026-10-12T15:59:59.123Z");
            Path logs = root.resolve("logs");
            UUID steve = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID alex = UUID.fromString("00000000-0000-0000-0000-000000000002");
            var writer = CsvLogWriter.start(logs, zone, startedAt);
            require(writer.file().getFileName().toString().equals("2026-10-12_23-59-59.123.csv"),
                    "filename uses server start date and time in configured timezone");
            require(Files.readString(writer.file()).equals("timestamp,event,player,uuid,info\n"),
                    "file exists with info header before the first player event");
            var sessions = new PlayerSessions();
            sessions.login(steve, startedAt);
            writer.append(startedAt, "LOGIN", "玩家,\"A\"", steve, "");
            sessions.login(alex, startedAt.plusSeconds(1));
            writer.append(startedAt.plusSeconds(2), "ADVANCEMENT", "玩家,\"A\"", steve,
                    "Stone Age [minecraft:story/mine_stone]");
            writer.append(startedAt.plusSeconds(3), "COMMAND", "玩家,\"A\"", steve,
                    "/say \"hello, world\"");
            String duration = sessions.logoutInfo(steve, startedAt.plusMillis(3723123));
            require(duration.equals("3723.123s"), "logout time minus login time");
            writer.append(startedAt.plusMillis(3723123), "LOGOUT", "玩家,\"A\"", steve, duration);
            require(sessions.logoutInfo(alex, startedAt.plusMillis(6001)).equals("5.001s"),
                    "players have independent sessions");
            require(sessions.logoutInfo(steve, startedAt.plusSeconds(4000)).equals("unknown"),
                    "logout removes the session");
            sessions.login(steve, startedAt.plusSeconds(4000));
            require(sessions.logoutInfo(steve, startedAt.plusMillis(4000500)).equals("0.500s"),
                    "reconnect measures only the new session");
            sessions.login(steve, startedAt);
            sessions.clear();
            require(sessions.logoutInfo(steve, startedAt).equals("unknown"), "server restart clears sessions");
            String first = Files.readString(writer.file(), StandardCharsets.UTF_8);
            require(first.lines().count() == 5, "all four event types append under one header");
            require(first.contains("\"玩家,\"\"A\"\"\""), "UTF-8 and escaped player name");
            require(first.contains("\"/say \"\"hello, world\"\"\""), "command info preserves commas and quotes");
            require(first.contains("\"Stone Age [minecraft:story/mine_stone]\""), "advancement info");
            require(first.contains("2026-10-13T"), "timestamps cross midnight in the same file");
            try (var files = Files.list(logs)) {
                require(files.count() == 1, "midnight does not rotate the file");
            }
            var restart = CsvLogWriter.start(logs, zone, startedAt.plusSeconds(5000));
            require(!restart.file().equals(writer.file()), "restart creates a new CSV");
            restart.append(startedAt.plusSeconds(5001), "LOGIN", "Alex", alex, "");
            require(Files.readString(writer.file()).equals(first), "restart preserves the previous run");
            var collision = CsvLogWriter.start(logs, zone, startedAt);
            require(collision.file().getFileName().toString().equals("2026-10-12_23-59-59.123_1.csv"),
                    "same start timestamp creates another file without overwriting");
            Path config = root.resolve("config/settings.properties");
            var settings = LoggerSettings.load(config);
            require(settings.timeZone().equals(zone), "default timezone");
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
                // The mod catches invalid settings and falls back to defaults.
            }
            Path blocker = root.resolve("not-a-directory");
            Files.writeString(blocker, "blocker");
            try {
                CsvLogWriter.start(blocker, zone, startedAt);
                throw new AssertionError("invalid directory accepted");
            } catch (java.io.IOException expected) {
                // The mod reports the error without interrupting server startup.
            }
            System.out.println("PASS: startup CSV, midnight, restart, collisions, four events, info escaping, online duration, reconnect, settings");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
