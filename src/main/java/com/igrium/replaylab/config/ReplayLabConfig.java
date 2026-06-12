package com.igrium.replaylab.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.igrium.replaylab.ReplayLab;
import com.igrium.replaylab.math.DynamicRotation.RotationMode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Getter @Setter
public class ReplayLabConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = ReplayLab.getLogger();

    public static ReplayLabConfig loadOrInit(Path path) {
        if (Files.exists(path)) {
            try(BufferedReader reader = Files.newBufferedReader(path)) {
                LOGGER.info("Loading ReplayLab config from {}", path);
                return GSON.fromJson(reader, ReplayLabConfig.class);
            } catch (Exception e) {
                LOGGER.error("Failed to load replay lab config", e);
                return new ReplayLabConfig();
            }
        } else {
            ReplayLabConfig config = new ReplayLabConfig();
            try {
                config.saveConfig(path);
            } catch (IOException e) {
                LOGGER.error("Failed to save replay lab config", e);
            }
            return config;
        }
    }

    public static ReplayLabConfig getInstance() {
        return ReplayLab.getInstance().getConfig();
    }


    private final Keybinds keybinds = new Keybinds();

    // Behavior
    /**
     * The first camera added to the scene automatically becomes the scene camera
     */
    private boolean autoSetCamera = true;

    /**
     * When a new object is created, open the inspector to that object
     */
    private boolean inspectOnCreate = true;

    // 3D objects
    private RotationMode defaultRotMode = RotationMode.EULER_YXZ;
    private boolean rotModeConvert = true;
    private boolean displayDegrees = true;

    public synchronized void saveConfig(Path path) throws IOException {
        LOGGER.info("Saving ReplayLab config to {}", path);
        try(BufferedWriter writer = Files.newBufferedWriter(path)) {
            GSON.toJson(this, writer);
        }
    }

    public CompletableFuture<?> saveConfigAsync(Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveConfig(path);
            } catch (Exception e) {
                LOGGER.error("Failed to save replay lab config", e);
                throw ExceptionUtils.asRuntimeException(e);
            }
        });
    }
}
