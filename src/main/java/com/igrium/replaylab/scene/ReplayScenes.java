package com.igrium.replaylab.scene;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.igrium.replaylab.scene.obj.SerializedReplayObject;
import com.replaymod.replaystudio.data.ReplayAssetEntry;
import com.replaymod.replaystudio.replay.ReplayFile;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Util;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Utility methods regarding scenes.
 */
@UtilityClass
public final class ReplayScenes {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplayLab/ReplayScenes");
    private static final Gson GSON = new Gson();

    /**
     * Return a scene's name from its path within the replay file.
     * @param path Path relative to the replay file.
     * @return Scene name.
     */
    public static String getSceneName(String path) {
        return FilenameUtils.getBaseName(path);
    }

    /**
     * Return a scene's path within the replay file from its name.
     * @param name Scene name.
     * @return Path relative to the replay file.
     */
    public static String getScenePath(String name) {
        return "scenes/" + name + ".json";
    }

    private static final TypeToken<Map<String, SerializedReplayObject>> serializedType = new TypeToken<>() {};

    /**
     * Read a scene from a replay file.
     *
     * @param name              Name of the scene
     * @param replayFile        Replay file to load from
     * @param exceptionCallback An optional consumer for non-fatal exceptions
     * @return Loaded scene
     * @throws FileNotFoundException If the scene does not exist.
     * @throws IOException           If a fatal IO exception occurs loading the file.
     */
    public static ReplayScene readScene(String name, ReplayFile replayFile, @Nullable Consumer<Exception> exceptionCallback)
            throws FileNotFoundException, IOException {
        String path = getScenePath(name);
        var opt = replayFile.get(path);
        if (!opt.isPresent()) {
            throw new FileNotFoundException("Tried to load non-existent scene: " + path);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(opt.get()))) {
            var serialized = GSON.fromJson(reader, serializedType);
            ReplayScene scene = new ReplayScene();
            scene.setExceptionCallback(exceptionCallback);
            scene.readSerializedObjects(serialized);
            LOGGER.info("Loaded scene from {}", path);
            return scene;
        }
    }

    /**
     * Save a scene to the replay file.
     *
     * @param scene      Scene to save.
     * @param name       Name to save under.
     * @param replayFile Replay file to save to.
     * @throws IOException If an IO exception occurs writing to the replay file.
     */
    public static void saveScene(ReplayScene scene, String name, ReplayFile replayFile) throws IOException {
        String path = getScenePath(name);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(replayFile.write(path)))) {
            GSON.toJson(scene.getSavedObjects(), writer);
        }
        LOGGER.info("Saved scene to {}", path);
    }

    /**
     * Scan a replay file for ReplayLab scenes.
     * @param file File to scan.
     * @return List of all scenes.
     * @throws IOException If an IO exception occurs trying to read the file.
     */
    public static List<String> listScenes(ReplayFile file) throws IOException {
        return file.getAssets().stream()
                .map(ReplayAssetEntry::getName)
                .filter(s -> s.startsWith("scenes") && s.endsWith(".json"))
                .map(ReplayScenes::getSceneName).toList();
    }

    public static CompletableFuture<List<String>> listScenesAsync(ReplayFile file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return listScenes(file);
            } catch (IOException e) {
                throw ExceptionUtils.asRuntimeException(e);
            }
        }, Util.getIoWorkerExecutor());
    }
}
