package com.igrium.replaylab.mixin;

import com.igrium.replaylab.ReplayLab;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.gui.screen.GuiReplayViewer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Mixin(value = GuiReplayViewer.class, remap = false)
public class MixinGuiReplayViewer {

    @Final @Shadow
    public GuiReplayViewer.GuiReplayList list;

    @Final @Shadow
    public GuiPanel upperButtonPanel;

    @Unique
    final GuiButton openReplayLabButton = new GuiButton().setI18nLabel("replaylab.gui.viewer.openReplayLab").onClick(new Runnable() {
        boolean loading = false;

        @Override
        public void run() {
            // Prevent the user from opening the replay twice at the same time
            if (loading)
                return;

            loading = true;
            openReplayLabButton.setDisabled();

            List<GuiReplayViewer.GuiReplayEntry> selected = list.getSelected();
            if (!selected.isEmpty()) {
                File file = selected.getFirst().file;
                ReplayLab.LOGGER.info("Opening replay in ReplayLab: {}", file);
                try {
                    ReplayLab.getInstance().openEditor(file.toPath());
                } catch (IOException e) {
                    ReplayLab.LOGGER.error("Error opening replay file", e);
                }
            }
        }
    }).setSize(150, 20);

    @Inject(method = "<init>", at = @At("RETURN"))
    void onInit(ReplayModReplay mod, CallbackInfo ci) {
        upperButtonPanel.addElements(null, openReplayLabButton);
    }

    @Inject(method = "updateButtons", at = @At("RETURN"))
    void onUpdateButtons(CallbackInfo ci) {
        // Easier than trying to capture locals
        List<GuiReplayViewer.GuiReplayEntry> selected = list.getSelected();
        int count = selected.size();

        openReplayLabButton.setEnabled(count == 1 && !((AccessorGuiReplayEntry) selected.getFirst()).isIncompatible());
    }
}
