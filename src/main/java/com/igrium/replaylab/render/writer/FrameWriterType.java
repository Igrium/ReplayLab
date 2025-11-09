package com.igrium.replaylab.render.writer;

import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.render.VideoRenderer;

import java.util.function.Function;

/**
 * Responsible for the creation of a frame writer.
 * @param <T> Type of frame writer to create.
 * @param <C> Stores the config values for the UI.
 */
public interface FrameWriterType<T extends FrameWriter, C> {
    T create(VideoRenderer renderer, C config);

    C newConfig();

    default void drawConfig(VideoRenderSettings settings, C config) {
    }

    class Simple<T extends FrameWriter> implements FrameWriterType<T, Object> {

        private final Function<VideoRenderer, T> factory;

        public Simple(Function<VideoRenderer, T> factory) {
            this.factory = factory;
        }

        @Override
        public T create(VideoRenderer renderer, Object config) {
            return factory.apply(renderer);
        }

        @Override
        public Object newConfig() {
            return new Object();
        }
    }
}
