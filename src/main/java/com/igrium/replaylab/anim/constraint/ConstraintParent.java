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
import com.igrium.replaylab.util.SimpleMutable;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import net.minecraft.util.Language;
import org.apache.commons.lang3.mutable.Mutable;
import org.jetbrains.annotations.Nullable;

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
    }

    @Override
    protected JsonObject writeJson(JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("parent", getParent());
        obj.add("inverse", context.serialize(getInverse()));
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
    }

    private final Transform3 evalTransform = new Transform3();

    @Override
    public void evaluate(int time, ObjectAccessor objAccessor) {
        TransformProvider parentObj = getParent(objAccessor);
        if (parentObj == null) return;

        // computedTransform currently holds the child's own (base) transform, C.
        Transform3 child = getObject().computedTransform();

        evalTransform.rot().setAutoModeSwitch(true);
        evalTransform.identity();
        parentObj.getTransform(evalTransform);

        // final = P * inverse * C
        evalTransform.mul(inverse).mul(child);
        child.set(evalTransform);
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

    Mutable<String> parentRef = new SimpleMutable<>(null);

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

    @Override
    public int drawPropertiesPanel(EditorState editor) {
        int flags = EditFlags.NONE;

        if (parentFailReason != null) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0xFF0000AA);
        }

        if (ReplayLabControls.objectSelector(t("gui.replaylab.parent"), new MutableRef<>(this::getParent, this::setParent), o -> true,
                editor.getScene().getObjects())) {
            applyInverse(editor);
            flags |= EditFlags.COMMIT;
        }

        if (parentFailReason != null) {
            ImGui.setItemTooltip(parentFailReason);
            ImGui.popStyleColor();
        }

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

    private static String tt(String key) {
        return Language.getInstance().get(key);
    }

    private static String t(String key) {
        return tt(key) + "###" + key;
    }
}
