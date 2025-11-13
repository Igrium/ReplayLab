package com.igrium.replaylab.render.capture;

import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.render.VideoRenderer;

public abstract class FrameCaptureType<T extends FrameCapture, C> {
    private final Class<C> configClass;

    protected FrameCaptureType(Class<C> configClass) {
        this.configClass = configClass;
    }

    /**
     * Create an instance of the desired config type.
     * @return Config instance.
     */
    public abstract C createConfig();

    /**
     * Instantiate this frame capture.
     *
     * @param renderer Renderer being used.
     * @param settings Video settings being used.
     * @param config   The config for the frame writer. Always returns an instance from <code>createConfig</code>.
     * @return The new instance.
     */
    public abstract T create(VideoRenderer renderer, VideoRenderSettings settings, C config);

    /**
     * Draw the config editor in ImGui.
     * @param renderSettings The current editor settings.
     * @param config The config to edit.
     */
    public void drawConfigEditor(VideoRenderSettings renderSettings, C config) {};

    public interface Factory<T extends FrameCapture> {
        T create(VideoRenderer renderer, VideoRenderSettings settings);
    }

    public static class Simple<T extends FrameCapture> extends FrameCaptureType<T, Object> {

        private final Factory<T> factory;

        protected Simple(Factory<T> factory) {
            super(Object.class);
            this.factory = factory;
        }

        @Override
        public Object createConfig() {
            return new Object();
        }

        @Override
        public T create(VideoRenderer renderer, VideoRenderSettings settings, Object config) {
            return factory.create(renderer, settings);
        }
    }
}
