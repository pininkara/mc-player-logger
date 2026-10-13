package dev.presencelogger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class CsvLogWriter {
    private static final String HEADER = "timestamp,event,player,uuid\n";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");
    private final Path directory;
    private final ZoneId timeZone;

    public CsvLogWriter(Path directory, ZoneId timeZone) {
        this.directory = directory;
        this.timeZone = timeZone;
    }

    public Path directory() {
        return directory;
    }

    public synchronized void append(Instant timestamp, String action, String name, UUID uuid)
            throws IOException {
        var localTime = timestamp.atZone(timeZone);
        Files.createDirectories(directory);
        Path file = directory.resolve(localTime.toLocalDate() + ".csv");
        boolean writeHeader = !Files.exists(file) || Files.size(file) == 0;
        // Append and close for each event: records are not left in a long-lived Java buffer.
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (writeHeader) {
                writer.write(HEADER);
            }
            writer.write(TIMESTAMP.format(localTime) + "," + escape(action) + ","
                    + escape(name) + "," + uuid + "\n");
        }
    }

    private static String escape(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
