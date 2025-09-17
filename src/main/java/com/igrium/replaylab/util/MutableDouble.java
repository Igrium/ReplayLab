package com.igrium.replaylab.util;

import org.apache.commons.lang3.mutable.Mutable;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

public interface MutableDouble extends Mutable<Double> {

    @Override @Deprecated
    default Double getValue() {
        return getDoubleValue();
    }

    @Override @Deprecated
    default void setValue(Double value) {
        setDoubleValue(value);
    }

    double getDoubleValue();
    void setDoubleValue(double value);

    static MutableDouble of(DoubleSupplier getter, DoubleConsumer setter) {
        return new MutableDoubleGetterSetter(getter, setter);
    }
}

class MutableDoubleGetterSetter implements MutableDouble {

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    MutableDoubleGetterSetter(DoubleSupplier getter, DoubleConsumer setter) {
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public double getDoubleValue() {
        return getter.getAsDouble();
    }

    @Override
    public void setDoubleValue(double value) {
        setter.accept(value);
    }
}
