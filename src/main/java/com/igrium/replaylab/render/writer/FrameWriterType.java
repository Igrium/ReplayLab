package com.igrium.replaylab.render.writer;

import com.igrium.craftui.file.FileDialogs;
import com.igrium.replaylab.render.VideoRenderSettings;
import com.igrium.replaylab.render.VideoRenderer;
import lombok.Getter;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A type of frame writer. Used to setup settings for exporting to file.
 * @param <T> Type of frame writer this will create.
 * @param <C> The class used to store config variables
 */
public abstract class FrameWriterType<T extends FrameWriter, C> {

    @Getter
    private final Class<C> configClass;

    protected FrameWriterType(Class<C> configClass) {
        this.configClass = configClass;
    }

    /**
     * Create an instance of the desired config type.
     * @return Config instance.
     */
    public abstract C createConfig();

    /**
     * Instantiate this frame writer.
     *
     * @param renderer Renderer being used.
     * @param settings Video settings being used.
     * @param config   The config for the frame writer. Always returns an instance from <code>createConfig</code>.
     * @return The new instance.
     */
    public abstract T create(VideoRenderer renderer, VideoRenderSettings settings, C config);

    /**
     * Draw the config editor in ImGui
     * @param renderSettings The current editor settings.
     * @param config The config to edit.
     */
    public void drawConfigEditor(VideoRenderSettings renderSettings, C config) {};

    /**
     * Get a list of file filters to be used when selecting the output file for this writer.
     * <code>null</code> or an empty array to select a directory.
     */
    public FileDialogs.FileFilter @Nullable [] getFileFilters() {
        return null;
    }

    public Identifier getId() {
        return FrameWriters.REGISTRY.inverse().get(this);
    }

    public interface Factory<T extends FrameWriter> {
        T create(VideoRenderer renderer, VideoRenderSettings settings);
    }


    public static class Simple<T extends FrameWriter> extends FrameWriterType<T, Object> {


        private final Factory<T> factory;

        public Simple(Factory<T> factory) {
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
