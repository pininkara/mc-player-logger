package dev.presencelogger;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;

@Mod(value = PlayerPresenceLogger.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class PlayerPresenceLogger {
    public static final String MOD_ID = "player_presence_logger";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final LoggerSettings settings;
    private final Path logDirectory;
    private final PlayerSessions sessions = new PlayerSessions();
    private CsvLogWriter writer;

    public PlayerPresenceLogger() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("player-presence-logger.properties");
        LoggerSettings loaded;
        try {
            loaded = LoggerSettings.load(configFile);
        } catch (IOException | IllegalArgumentException | DateTimeException | SecurityException exception) {
            LOGGER.error("Unable to load {}. Using default settings for this launch.", configFile, exception);
            loaded = LoggerSettings.defaults();
        }
        settings = loaded;
        logDirectory = settings.resolveDirectory(FMLPaths.GAMEDIR.get());
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(this::onAdvancementEarned);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onCommand);
    }

    private void onServerStarting(ServerStartingEvent event) {
        sessions.clear();
        writer = null;
        try {
            writer = CsvLogWriter.start(logDirectory, settings.timeZone(), Instant.now());
            LOGGER.info("Player Presence Logger enabled: file={}, timeZone={}", writer.file(), settings.timeZone());
        } catch (IOException | SecurityException exception) {
            LOGGER.error("Cannot create this server run's CSV in {}. File logging is disabled for this run.",
                    logDirectory, exception);
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        writer = null;
        sessions.clear();
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Instant timestamp = Instant.now();
            sessions.login(player.getUUID(), timestamp);
            record(player, timestamp, "LOGIN", "");
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Instant timestamp = Instant.now();
            record(player, timestamp, "LOGOUT", sessions.logoutInfo(player.getUUID(), timestamp));
        }
    }

    private void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var advancement = event.getAdvancement();
            String id = advancement.id().toString();
            String info = advancement.value().display()
                    .map(display -> display.getTitle().getString() + " [" + id + "]")
                    .orElse(id);
            record(player, Instant.now(), "ADVANCEMENT", info);
        }
    }

    private void onCommand(CommandEvent event) {
        if (event.isCanceled()) {
            return;
        }
        var parse = event.getParseResults();
        if (parse.getContext().getSource().getEntity() instanceof ServerPlayer player) {
            String command = parse.getReader().getString();
            record(player, Instant.now(), "COMMAND", command.startsWith("/") ? command : "/" + command);
        }
    }

    private void record(ServerPlayer player, Instant timestamp, String action, String info) {
        if (writer == null) {
            return;
        }
        try {
            writer.append(timestamp, action, player.getGameProfile().getName(), player.getUUID(), info);
        } catch (IOException | SecurityException exception) {
            LOGGER.error("Failed to record {} for player {} ({}) at {}",
                    action, player.getGameProfile().getName(), player.getUUID(), timestamp, exception);
        }
    }
}
