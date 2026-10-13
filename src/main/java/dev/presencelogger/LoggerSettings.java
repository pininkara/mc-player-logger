package dev.presencelogger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.util.Properties;

public record LoggerSettings(Path logDirectory, ZoneId timeZone) {
    public static LoggerSettings defaults() {
        return new LoggerSettings(Path.of("logs", "player-presence"), ZoneId.of("Asia/Shanghai"));
    }

    public static LoggerSettings load(Path configFile) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } else {
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            properties.setProperty("logDirectory", "logs/player-presence");
            properties.setProperty("timeZone", "Asia/Shanghai");
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                properties.store(writer, "Player Presence Logger: restart the server after changing settings.");
            }
        }
        String directory = properties.getProperty("logDirectory", "logs/player-presence").trim();
        if (directory.isEmpty()) {
            throw new IllegalArgumentException("logDirectory cannot be empty");
        }
        return new LoggerSettings(Path.of(directory),
                ZoneId.of(properties.getProperty("timeZone", "Asia/Shanghai").trim()));
    }

    public Path resolveDirectory(Path gameDirectory) {
        return (logDirectory.isAbsolute() ? logDirectory : gameDirectory.resolve(logDirectory))
                .toAbsolutePath().normalize();
    }
}
