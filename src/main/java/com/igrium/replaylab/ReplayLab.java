package com.igrium.replaylab;

import com.igrium.craftui.app.AppManager;
import com.igrium.replaylab.camera.AnimatedCameraEntity;
import com.igrium.replaylab.camera.AnimatedCameraRenderer;
import com.igrium.replaylab.debug.PrintCameraCommand;
import com.igrium.replaylab.ui.ReplayLabUI;
import com.replaymod.core.ReplayMod;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replaystudio.replay.ReplayFile;
import lombok.Getter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class ReplayLab implements ModInitializer, ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(ReplayLab.class);
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static Logger getLogger(String name) {
        return LoggerFactory.getLogger("ReplayLab/" + name);
    }

    public static Logger getLogger() {
        return LoggerFactory.getLogger("ReplayLab/" + STACK_WALKER.getCallerClass().getName());
    }

    @Getter
    private static ReplayLab instance;

    @Getter @Nullable
    private ReplayLabUI appInstance;

    public ReplayLab() {
        instance = this;
    }

    @Override
    public void onInitialize() {
        ReplayLabEntities.register();
    }

    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            ClientCommandRegistrationCallback.EVENT.register(PrintCameraCommand::register);
        }
        EntityRendererRegistry.register(
                ReplayLabEntities.CAMERA,
                AnimatedCameraRenderer::new
        );
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
        appInstance.afterOpen();
    }
}