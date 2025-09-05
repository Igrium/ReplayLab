package com.igrium.replaylab;

import com.igrium.craftui.app.AppManager;
import com.igrium.replaylab.ui.ReplayLabUI;
import com.replaymod.core.ReplayMod;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replaystudio.replay.ReplayFile;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class ReplayLab implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(ReplayLab.class);

    @Getter
    private static ReplayLab instance;

    @Getter @Nullable
    private ReplayLabUI appInstance;

    public ReplayLab() {
        instance = this;
    }

    @Override
    public void onInitialize() {
    }

    public boolean isEditorOpen() {
        return appInstance != null && appInstance.isOpen();
    }

    public void openEditor(Path replayFile) throws IOException, IllegalStateException {
        openEditor(ReplayMod.instance.files.open(replayFile), true);
    }

    public void openEditor(ReplayFile replayFile, boolean checkModCompat) throws IOException, IllegalStateException {
        if (isEditorOpen()) {
            throw new IllegalStateException("The editor is already open.");
        }

        ReplayModReplay.instance.startReplay(replayFile, false, true);
        appInstance = new ReplayLabUI();
        AppManager.openApp(appInstance);


    }

}