package com.igrium.replaylab.anim.constraint;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.ObjectEditState;
import com.igrium.replaylab.scene.obj.ReplayObject;
import imgui.ImGui;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Constraint<R> {

    @Getter
    private final ConstraintType<R, ?> type;

    @Getter
    private final R object;

    public Constraint(ConstraintType<R, ?> type, R object) {
        this.type = type;
        this.object = object;
    }

    protected abstract void writeJson(JsonObject jsonObject, JsonSerializationContext context);
    protected abstract void readJson(JsonObject jsonObject, JsonDeserializationContext context);

    public abstract void evaluate(int time, ObjectAccessor objAccessor);

    public @Nullable String tryGetId() {
        return ((ReplayObject) object).getConstraints().getValues().inverse().get(this);
    }

    public @NotNull String getId() {
        String id = tryGetId();
        if (id == null) throw new IllegalStateException("Constraint is not part of any replay object");
        return id;
    }

    public JsonObject save(JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        writeJson(jsonObject, context);
        jsonObject.addProperty("type", type.getId());
        return jsonObject;
    }

    public void parse(JsonObject jsonObject, JsonDeserializationContext context) {
        readJson(jsonObject, context);
    }

    /**
     * Called during the ImGui render process to draw the constraint's configurable properties.
     *
     * @return {@link ObjectEditState}
     */
    public int drawPropertiesPanel(EditorState editor) {
        ImGui.text("This constraint has no editable properties.");
        return ObjectEditState.NONE;
    }
}
