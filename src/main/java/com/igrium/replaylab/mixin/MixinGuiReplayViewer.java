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
import java.util.List;

@Mixin(value = GuiReplayViewer.class, remap = false)
public class MixinGuiReplayViewer {

    @Final
    @Shadow
    public GuiReplayViewer.GuiReplayList list;

    @Final
    @Shadow
    public GuiPanel upperButtonPanel;

    @Shadow @Final public GuiButton loadButton;
    @Unique
    final GuiButton openReplayLabButton = new GuiButton().setI18nLabel("replaylab.gui.viewer.openReplayLab").onClick(new Runnable() {
        private boolean loading = false;

        @Override
        public void run() {
            // Prevent the player from opening the replay twice at the same time
            if (loading) {
                return;
            }
            loading = true;
            openReplayLabButton.setDisabled(); // visual hint

            var selected = list.getSelected();
            if (selected.isEmpty())
                return;

            File file = selected.getFirst().file;
            ReplayLab.LOGGER.info("Opening file in ReplayLab: {}", file);
            try {
                ReplayLab.getInstance().openReplay(file);
            } catch (Exception e) {
                ReplayLab.LOGGER.error("Error opening replay file: ", e);
                loading = false;
            }
        }
    }).setSize(150, 20);;

    @Inject(method = "<init>", at = @At("RETURN"))
    void onInit(ReplayModReplay mod, CallbackInfo ci) {
        upperButtonPanel.addElements(null, openReplayLabButton);
    }

    @Inject(method = "updateButtons", at = @At("RETURN"))
    void onUpdateButtons(CallbackInfo ci) {
        // Easier to recalculate than dealing with local capture
        List<GuiReplayViewer.GuiReplayEntry> selected = list.getSelected();
        int count = selected.size();
        openReplayLabButton.setEnabled(count == 1 && !((AccessorGuiReplayEntry) selected.getFirst()).isIncompatible());
    }
}