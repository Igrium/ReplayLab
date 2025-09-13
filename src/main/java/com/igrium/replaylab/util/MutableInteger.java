package com.igrium.replaylab.util;

import com.replaymod.lib.org.apache.commons.lang3.mutable.Mutable;
import imgui.type.ImInt;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public interface MutableInteger extends Mutable<Integer> {
    @Override @Deprecated
    default Integer getValue() {
        return getIntValue();
    }

    @Override @Deprecated
    default void setValue(Integer integer) {
        setIntValue(integer);
    };

    int getIntValue();

    void setIntValue(int value);

    static MutableInteger of(IntSupplier getter, IntConsumer setter) {
        return new MutableIntGetterSetter(getter, setter);
    }

    static MutableInteger createSimple() {
        return new SimpleMutableInt();
    }

    static MutableInteger of(MutableInt value) {
        return new WrappedMutableInt(value);
    }

    static MutableInteger of(ImInt value) {
        return new WrappedImInt(value);
    }
}

class MutableIntGetterSetter implements MutableInteger {

    private final IntSupplier getter;
    private final IntConsumer setter;

    MutableIntGetterSetter(IntSupplier getter, IntConsumer setter) {
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public int getIntValue() {
        return getter.getAsInt();
    }

    @Override
    public void setIntValue(int value) {
        setter.accept(value);
    }
}

class SimpleMutableInt implements MutableInteger {
    private int value;

    @Override
    public int getIntValue() {
        return value;
    }

    @Override
    public void setIntValue(int value) {
        this.value = value;
    }
}

class WrappedMutableInt implements MutableInteger {
    private final MutableInt value;

    WrappedMutableInt(MutableInt value) {
        this.value = value;
    }

    @Override
    public int getIntValue() {
        return value.getValue();
    }

    @Override
    public void setIntValue(int value) {
        this.value.setValue(value);
    }
}

class WrappedImInt implements MutableInteger {
    private final ImInt value;

    WrappedImInt(ImInt value) {
        this.value = value;
    }

    @Override
    public int getIntValue() {
        return value.get();
    }

    @Override
    public void setIntValue(int value) {
        this.value.set(value);
    }
}