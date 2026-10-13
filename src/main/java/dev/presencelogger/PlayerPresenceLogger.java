package dev.presencelogger;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;

// Physical-side restriction: neither clients nor integrated servers instantiate this entrypoint.
@Mod(value = PlayerPresenceLogger.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class PlayerPresenceLogger {
    public static final String MOD_ID = "player_presence_logger";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final CsvLogWriter writer;

    public PlayerPresenceLogger() {
        var gameDirectory = FMLPaths.GAMEDIR.get();
        var configFile = FMLPaths.CONFIGDIR.get().resolve("player-presence-logger.properties");
        LoggerSettings settings;
        try {
            settings = LoggerSettings.load(configFile);
        } catch (IOException | IllegalArgumentException | DateTimeException | SecurityException exception) {
            LOGGER.error("Unable to load {}. Using default settings for this launch.", configFile, exception);
            settings = LoggerSettings.defaults();
        }
        writer = new CsvLogWriter(settings.resolveDirectory(gameDirectory), settings.timeZone());
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        LOGGER.info("Player Presence Logger enabled: directory={}, timeZone={}",
                writer.directory(), settings.timeZone());
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        record(event, "LOGIN");
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        record(event, "LOGOUT");
    }

    private void record(PlayerEvent event, String action) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Capture the timestamp at the event, before doing file I/O.
        Instant timestamp = Instant.now();
        try {
            writer.append(timestamp, action, player.getGameProfile().getName(), player.getUUID());
        } catch (IOException | SecurityException exception) {
            // A logging failure must not interrupt login/logout processing.
            LOGGER.error("Failed to record {} for player {} ({}) at {}",
                    action, player.getGameProfile().getName(), player.getUUID(), timestamp, exception);
        }
    }
}
