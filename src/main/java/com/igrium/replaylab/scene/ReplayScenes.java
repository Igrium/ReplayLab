package com.igrium.replaylab.scene;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.replaymod.replaystudio.data.ReplayAssetEntry;
import com.replaymod.replaystudio.replay.ReplayFile;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Util;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@UtilityClass
public class ReplayScenes {

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Obtain the filename of a scene within the replay zip.
     * @param name The scene's name.
     * @return Scene filename
     */
    public static String getScenePath(String name) {
        return "scenes/" + name + ".json";
    }

    /**
     * Obtain the name of a scene based on its filename.
     * @param path File path of scene within the replay zip.
     * @return Scene's name.
     */
    public static String getSceneName(String path) {
        return FilenameUtils.getBaseName(path);
    }


    public static void saveScene(ReplayFile file, ReplayScene scene, String sceneName) throws IOException {
        try (Writer writer = new OutputStreamWriter(file.write(getScenePath(sceneName)))) {
            GSON.toJson(scene, writer);
        }
    }

    public static CompletableFuture<?> saveSceneAsync(ReplayFile file, ReplayScene scene, String sceneName) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveScene(file, scene, sceneName);
            } catch (IOException e) {
                throw ExceptionUtils.asRuntimeException(e);
            }
        }, Util.getIoWorkerExecutor());
    }

    public static ReplayScene loadScene(ReplayFile file, String sceneName) throws IOException {
        var inOpt = file.get(getScenePath(sceneName));
        if (!inOpt.isPresent()) {
            throw new FileNotFoundException(getScenePath(sceneName) + " not found in replay file.");
        }

        try (Reader reader = new InputStreamReader(inOpt.get())) {
            return GSON.fromJson(reader, ReplayScene.class);
        }
    }

    public static CompletableFuture<ReplayScene> loadSceneAsync(ReplayFile file, String sceneName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadScene(file, sceneName);
            } catch (IOException e) {
                throw ExceptionUtils.asRuntimeException(e);
            }
        });
    }

    /**
     * List all the scenes within a replay file.
     * @param file File to scan.
     * @return A list of all ReplayLab scenes.
     * @throws IOException If there's an error reading the replay file.
     */
    public static List<String> listScenes(ReplayFile file) throws IOException {
        return file.getAssets().stream()
                .map(ReplayAssetEntry::getName)
                .filter(s -> s.startsWith("scenes") && s.endsWith(".json"))
                .map(ReplayScenes::getSceneName)
                .toList();
    }

    /**
     * List all the scenes within a replay file.
     * @param file File to scan.
     * @return A future that returns with all the scenes, or fails if there's a problem reading the replay file.
     */
    public static CompletableFuture<List<String>> listScenesAsync(ReplayFile file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return listScenes(file);
            } catch (IOException e) {
                throw ExceptionUtils.asRuntimeException(e);
            }
        });
    }
}
