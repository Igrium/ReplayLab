package com.igrium.replaylab.anim.constraint;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.math.Transform3;
import com.igrium.replaylab.object.EditFlags;
import com.igrium.replaylab.object.ReplayObject;
import com.igrium.replaylab.object.ReplayObject3D;
import com.igrium.replaylab.object.TransformProvider;
import com.igrium.replaylab.ui.util.ReplayLabControls;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import net.minecraft.util.Language;
import org.apache.commons.lang3.mutable.Mutable;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Parents an object to another object
 */
public class ConstraintParent extends Constraint<ReplayObject3D> {

    private record MutableRef<T>(Supplier<T> getter, Consumer<T> setter) implements Mutable<T> {

        @Override
            public T getValue() {
                return getter.get();
            }

            @Override
            public void setValue(T value) {
                setter.accept(value);
            }
        }

    /**
     * The object we're parented to
     */
    @Getter @Setter
    private @NonNull String parent = "";

    @Getter @Setter
    private boolean affectPos = true;

    @Getter @Setter
    private boolean affectRot = true;

    @Getter @Setter
    private boolean affectScale = true;

    /**
     * The parent's "inverse".
     */
    @Getter
    private final Transform3 inverse = new Transform3();

    public void setInverse(Transform3 inverse) {
        this.inverse.set(inverse);
    }

    private @Nullable String parentFailReason;

    public ConstraintParent(ConstraintType<ReplayObject3D, ?> type, ReplayObject3D object) {
        super(type, object);
        affectPos = object.hasPos();
        affectRot = object.hasRot();
        affectScale = object.hasScale();
    }

    @Override
    protected JsonObject writeJson(JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("parent", getParent());
        obj.add("inverse", context.serialize(getInverse()));
        obj.addProperty("affectPos", isAffectPos());
        obj.addProperty("affectRot", isAffectRot());
        obj.addProperty("affectScale", isAffectScale());
        return obj;
    }

    @Override
    protected void readJson(JsonObject json, JsonDeserializationContext context) {
        if (json.has("parent")) {
            setParent(json.get("parent").getAsString());
        }
        if (json.has("inverse")) {
            setInverse(context.deserialize(json.get("inverse"), Transform3.class));
        }
        if (json.has("affectPos")) {
            setAffectPos(json.get("affectPos").getAsBoolean());
        }
        if (json.has("affectRot")) {
            setAffectRot(json.get("affectRot").getAsBoolean());
        }
        if (json.has("affectScale")) {
            setAffectScale(json.get("affectScale").getAsBoolean());
        }
    }

    private final Transform3 evalTransform = new Transform3();

    @Override
    public void evaluate(int time, ObjectAccessor objAccessor) {
        TransformProvider parentObj = getParent(objAccessor);
        if (parentObj == null) return;

        if (!affectPos && !affectRot && !affectScale) return;

        Transform3 transform = getObject().computedTransform();

        evalTransform.rot().setAutoModeSwitch(true);
        evalTransform.identity();
        parentObj.getTransform(evalTransform);

        if (!affectPos || !affectRot || !affectScale) {
            Transform3 bindParent = inverse.invert(new Transform3());
            if (!affectPos) evalTransform.pos().set(bindParent.pos());
            if (!affectRot) evalTransform.rot().set(bindParent.rot());
            if (!affectScale) evalTransform.scale().set(bindParent.scale());
        }

        evalTransform.mul(inverse).mul(transform);
        transform.set(evalTransform);
    }

    private @Nullable TransformProvider getParent(ObjectAccessor accessor) {
        TransformProvider parentObj = null;
        try {
            parentObj = (TransformProvider) accessor.getObject(parent);
            parentFailReason = parentObj == null ? tt("gui.replaylab.obj_not_found") : null;
        } catch (DependencyLoopException e) {
            parentFailReason = tt("gui.replaylab.dependency_loop").formatted(e.getObjId());
        } catch (ClassCastException e) {
            parentFailReason = tt("gui.replaylab.parent_must_be_transform");
        }

        return parentObj;
    }

    Mutable<String> parentRef = new MutableRef<>(this::getParent, this::setParent);

    private void applyInverse(EditorState editor) {
        ReplayObject parent = editor.getScene().getObject(getParent());
        inverse.identity();
        if (parent instanceof TransformProvider tProv) {
            tProv.getTransform(inverse).invert();
        }
    }

    private void clearInverse() {
        inverse.identity();
    }

    private final ImBoolean tmpBool = new ImBoolean(false);

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        int flags = EditFlags.NONE;

        if (parentFailReason != null) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0xFF0000AA);
        }

        if (ReplayLabControls.objectSelector(t("gui.replaylab.parent"), parentRef, o -> true,
                editor.getScene().getObjects())) {
            applyInverse(editor);
            flags |= EditFlags.COMMIT;
        }

        if (parentFailReason != null) {
            ImGui.setItemTooltip(parentFailReason);
            ImGui.popStyleColor();
        }

        // POSITION
        ImGui.beginDisabled(!getObject().hasPos());
        tmpBool.set(isAffectPos());
        if (ImGui.checkbox(t("gui.replaylab.pos"), tmpBool)) {
            setAffectPos(tmpBool.get());
            flags |= EditFlags.COMMIT;
        }
        ImGui.endDisabled();

        // ROTATION
        ImGui.beginDisabled(!getObject().hasRot());
        tmpBool.set(isAffectRot());
        if (ImGui.checkbox(t("gui.replaylab.rot"), tmpBool)) {
            setAffectRot(tmpBool.get());
            flags |= EditFlags.COMMIT;
        }
        ImGui.endDisabled();

        // SCALE
        ImGui.beginDisabled(!getObject().hasScale());
        tmpBool.set(isAffectScale());
        if (ImGui.checkbox(t("gui.replaylab.scale"), tmpBool)) {
            setAffectScale(tmpBool.get());
            flags |= EditFlags.COMMIT;
        }
        ImGui.endDisabled();

        float buttonWidth = ImGui.getContentRegionAvailX() / 2 - ImGui.getStyle().getItemSpacingX();
        float buttonHeight = ImGui.getFrameHeight();

        ImGui.beginDisabled(parentFailReason != null);
        if (ImGui.button(t("gui.replaylab.set_inverse"), buttonWidth, buttonHeight)) {
            applyInverse(editor);
            flags |= EditFlags.COMMIT;
        }
        ImGui.endDisabled();
        ImGui.sameLine();

        if (ImGui.button(t("gui.replaylab.clear_inverse"), buttonWidth, buttonHeight)) {
            clearInverse();
            flags |= EditFlags.COMMIT;
        }

        return flags;
    }

    @Override
    public void remapReferences(String oldName, String newName) {
        super.remapReferences(oldName, newName);
        if (getParent().equals(oldName)) {
            setParent(newName);
        }
    }

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }

    private static String t(String key) {
        return tt(key) + "###" + key;
    }
}
