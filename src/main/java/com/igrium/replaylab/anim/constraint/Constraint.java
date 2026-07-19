package com.igrium.replaylab.anim.constraint;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.igrium.replaylab.editor.EditorState;
import com.igrium.replaylab.scene.obj.EditFlags;
import com.igrium.replaylab.scene.obj.PropertyHolder;
import com.igrium.replaylab.scene.obj.ReplayObject;
import imgui.ImGui;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public abstract class Constraint<R> implements PropertyHolder {

    @Getter
    private final ConstraintType<R, ?> type;

    @Getter
    private final R object;

    @Getter
    private final Map<String, Property> properties = new HashMap<>();

    public Constraint(ConstraintType<R, ?> type, R object) {
        this.type = type;
        this.object = object;
    }

    /// === PROPERTIES ===

    protected final void addProperty(String name, DoubleSupplier getter, DoubleConsumer setter) {
        addProperty(name, new Property(getter, setter));
    }

    protected final void addProperty(String name, Property property) {
        if (name.contains(".")) {
            throw new IllegalArgumentException("Property names may not contain '.'");
        }
        getProperties().put(name, property);
    }

    @Override
    public @Nullable Property getPropertyRef(String name) {
        return properties.get(name);
    }

    /**
     * Get the name a property will use once it's in an object
     * @param name The local property name
     * @return The global property name
     */
    public final String propName(String name) {
        return getId() + ":" + name;
    }

    /// === SERIALIZATION ===

    protected abstract JsonObject writeJson(JsonSerializationContext context);
    protected abstract void readJson(JsonObject jsonObject, JsonDeserializationContext context);


    public JsonObject save(JsonSerializationContext context) {
        var jsonObject = writeJson(context);
        jsonObject.addProperty("type", type.getId());
        return jsonObject;
    }

    public void parse(JsonObject jsonObject, JsonDeserializationContext context) {
        readJson(jsonObject, context);
    }

    /// === INTEGRATION ===

    public abstract void evaluate(int time, ObjectAccessor objAccessor);

    public @Nullable String tryGetId() {
        return ((ReplayObject) object).getConstraints().getValues().inverse().get(this);
    }

    public @NotNull String getId() {
        String id = tryGetId();
        if (id == null) throw new IllegalStateException("Constraint is not part of any replay object");
        return id;
    }


    /**
     * Called during the ImGui render process to draw the constraint's configurable properties.
     *
     * @return {@link EditFlags}
     */
    public int drawPropertiesPanel(EditorState editor) {
        ImGui.text("This constraint has no editable properties.");
        return EditFlags.NONE;
    }
}
