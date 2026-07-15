package com.igrium.replaylab.ui;

import com.igrium.craftui.MaterialIcons;
import com.igrium.craftui.util.RaycastUtils;
import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiStorage;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Colors;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A tool that lets the user pick entities from the viewport by clicking on them, similar to a color eyedropper.
 * Only one picker may be active at a time, identified by a string name.
 */
public final class EntPicker {

    private final List<Entity> pickedEntities = new ArrayList<>();
    private final List<Entity> pickedEntitiesUnmod = Collections.unmodifiableList(pickedEntities);
    private final int id;

    private EntPicker(int id) {
        this.id = id;
    }

    /**
     * @return All entities picked so far, in the order they were picked.
     */
    public List<Entity> getPickedEntities() {
        return pickedEntitiesUnmod;
    }

    /**
     * @return The first picked entity, or <code>null</code> if none have been picked yet.
     */
    public @Nullable Entity getPickedEntity() {
        return pickedEntities.isEmpty() ? null : pickedEntities.getFirst();
    }

    /**
     * @return Whether this picker is the currently active picker.
     */
    public boolean isActive() {
        return instance == this;
    }

    /**
     * Closes this picker if it is the active one.
     *
     * @return Whether this picker was active and has now been closed.
     */
    public boolean close() {
        if (instance == this) {
            instance = null;
            return true;
        }
        return false;
    }

    private void draw() {
        // To be drawn during viewport render
        // TODO: figure out how to show eyedropper
        ImGui.setMouseCursor(ImGuiMouseCursor.Hand);

        if (!ImGui.isWindowHovered()) {
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                close();
            }
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;

        Mouse mouse = mc.mouse;
        HitResult raycast = RaycastUtils.raycastViewport((float) mouse.getX(), (float) mouse.getY(), 1000,
                e -> e != mc.getCameraEntity(), false);

        if (raycast instanceof EntityHitResult entHit) {
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                pickedEntities.add(entHit.getEntity());
            }
            ImGui.setTooltip(entHit.getEntity().getName().getString());
        }
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            close();
        }
    }

    private static @Nullable EntPicker instance;

    /**
     * Opens a picker with the given name, activating it. If a picker with this name is already active, does nothing.
     *
     * @param name Name identifying the picker, matched via {@link ImGui#getID(String)}.
     * @return Whether a new picker was opened (<code>false</code> if one was already active under this name).
     */
    public static boolean open(String name) {
        int id = ImGui.getID(name);
        if (instance == null || instance.id != id) {
            instance = new EntPicker(id);
            return true;
        }
        return false;
    }

    /**
     * Gets the active picker if it matches the given name.
     *
     * @param name Name identifying the picker, matched via {@link ImGui#getID(String)}.
     * @return The active picker, or <code>null</code> if no picker is active under this name.
     */
    public static @Nullable EntPicker get(String name) {
        int id = ImGui.getID(name);
        if (instance != null && instance.id == id) {
            return instance;
        }
        return null;
    }

    @ApiStatus.Internal
    public static void drawPicker() {
        if (instance != null) instance.draw();
    }
}