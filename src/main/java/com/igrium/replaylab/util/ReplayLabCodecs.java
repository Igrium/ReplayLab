package com.igrium.replaylab.util;

import com.mojang.serialization.Codec;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Util;
import org.joml.*;

import java.util.List;
import java.util.function.Function;

@SuppressWarnings("SequencedCollectionMethodCanBeUsed") // Makes it more readable
@UtilityClass
public class ReplayLabCodecs {

    private static <T, V> Codec<T> arrayCodec(Codec<V> internalCodec, Function<T, List<V>> toList,
                                              Function<List<V>, T> fromList, int size) {
        return internalCodec.listOf().comapFlatMap(input -> Util.fixedSize(input, size).map(fromList), toList);
    }

    public static final Codec<Vector3dc> VECTOR3D = arrayCodec(Codec.DOUBLE,
            vec -> List.of(vec.x(), vec.y(), vec.z()),
            l -> new Vector3d(l.getFirst(), l.get(1), l.get(2)), 3);

    public static final Codec<Vector2dc> VECTOR2D = arrayCodec(Codec.DOUBLE,
            vec -> List.of(vec.x(), vec.y()),
            l -> new Vector2d(l.get(0), l.get(1)), 2);

}
