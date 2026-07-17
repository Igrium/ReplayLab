package com.igrium.replaylab.scene.obj;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * An object that exposes a set of named, double-valued properties which can be read, written,
 * and referenced by name (e.g. by animation channels).
 *
 * @see ReplayObject
 */
public interface PropertyHolder {
    /**
     * Thrown when a non-existent property is attempted to be accessed.
     */
    class UnknownPropertyException extends IllegalArgumentException {
        @Getter
        private final String propName;

        public UnknownPropertyException(String propName) {
            super("Unknown property name: " + propName);
            this.propName = propName;
        }
    }

    /**
     * A single named property: a getter/setter pair with optional value bounds.
     *
     * @param getter Reads the current value.
     * @param setter Writes a new value.
     * @param minVal Minimum allowed value.
     * @param maxVal Maximum allowed value.
     * @param noMods If set, this property is not allowed to have curve modifiers (UI only)
     */
    record Property(DoubleSupplier getter, DoubleConsumer setter,
                    double minVal, double maxVal, boolean noMods) {
        public Property(DoubleSupplier getter, DoubleConsumer setter) {
            this(getter, setter, Double.MIN_VALUE, Double.MAX_VALUE, false);
        }

        public double getValue() {
            return getter.getAsDouble();
        }

        public void setValue(double value) {
            setter.accept(value);
        }
    }

    /**
     * Look up a property by name.
     *
     * @param name Property name. Use <code>.</code> to access a nested property.
     * @return The property, or <code>null</code> if no property exists under that name.
     */
    @Nullable Property getPropertyRef(String name);

    /**
     * Get the current value of a property.
     *
     * @param name Property name. Use <code>.</code> to access a nested property.
     * @return The property's value, or <code>null</code> if no such property exists.
     */
    default @Nullable Double getProperty(String name) {
        var ref = getPropertyRef(name);
        return ref != null ? ref.getValue() : null;
    }

    /**
     * Get the current value of a property.
     *
     * @param name Property name. Use <code>.</code> to access a nested property.
     * @return The property's value.
     * @throws UnknownPropertyException If no property exists under that name.
     */
    default double getPropertyOrThrow(String name) throws UnknownPropertyException {
        var ref = getPropertyRef(name);
        if (ref == null) {
            throw new UnknownPropertyException(name);
        }
        return ref.getValue();
    }

    /**
     * Set the value of a property.
     *
     * @param propName Property name. Use <code>.</code> to access a nested property.
     * @param value    New value.
     * @throws UnknownPropertyException If no property exists under that name.
     */
    default void setPropertyOrThrow(String propName, double value) throws UnknownPropertyException {
        if (!setProperty(propName, value)) {
            throw new UnknownPropertyException(propName);
        }
    }

    /**
     * Set the value of a property if it exists.
     *
     * @param propName Property name. Use <code>.</code> to access a nested property.
     * @param value    New value.
     * @return Whether the property exists and was set.
     */
    default boolean setProperty(String propName, double value) {
        Property prop = getPropertyRef(propName);
        if (prop != null) {
            prop.setValue(value);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check whether a property exists under a given name.
     *
     * @param name Property name. Use <code>.</code> to access a nested property.
     * @return Whether the property exists.
     */
    default boolean hasProperty(String name) {
        return getPropertyRef(name) != null;
    }
}
