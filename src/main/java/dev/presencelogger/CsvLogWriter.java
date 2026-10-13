package dev.presencelogger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class CsvLogWriter {
    private static final String HEADER = "timestamp,event,player,uuid,info\n";
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss.SSS");
    private final Path file;
    private final ZoneId timeZone;

    private CsvLogWriter(Path file, ZoneId timeZone) {
        this.file = file;
        this.timeZone = timeZone;
    }

    public static CsvLogWriter start(Path directory, ZoneId timeZone, Instant startedAt) throws IOException {
        Files.createDirectories(directory);
        String prefix = FILE_TIMESTAMP.format(startedAt.atZone(timeZone));
        // CREATE_NEW preserves old files even when two starts have the same timestamp.
        for (int suffix = 0; ; suffix++) {
            Path file = directory.resolve(prefix + (suffix == 0 ? "" : "_" + suffix) + ".csv");
            BufferedWriter output;
            try {
                output = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException collision) {
                continue;
            }
            try (output) {
                output.write(HEADER);
            }
            return new CsvLogWriter(file, timeZone);
        }
    }

    public Path file() {
        return file;
    }

    public synchronized void append(Instant timestamp, String action, String name, UUID uuid, String info)
            throws IOException {
        // The same run keeps the same file across midnight. Flush each record by closing.
        try (BufferedWriter output = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            output.write(TIMESTAMP.format(timestamp.atZone(timeZone)) + "," + escape(action) + ","
                    + escape(name) + "," + uuid + "," + escape(info) + "\n");
        }
    }

    private static String escape(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
