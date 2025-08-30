package com.igrium.replaylab.replay;

import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.QuickReplaySender;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.util.Location;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * A re-implementation of <code>ReplayHandler</code> that doesn't handles
 * only playback and doesn't try to pack UI bullshit.
 */
//public class LabReplayHandler {
//    private static MinecraftClient mc = MinecraftClient.getInstance();
//
//    /**
//     * The file currently being played.
//     */
//    private final ReplayFile replayFile;
//
//    private final FullReplaySender fullReplaySender;
//    private final QuickReplaySender quickReplaySender;
//
//    // Is it even worth implementing Restrictions? I'm pressed to find a server that actually uses them.
//
//    /**
//     * Whether camera movements by user input and/or server packets should be suppressed.
//     */
//    private boolean suppressCameraMovements;
//
//    private Set<Marker> markers;
//
//    private EmbeddedChannel channel;
//    private int replayDuration;
//
//    /**
//     * The position at which the camera should be located after the next jump.
//     */
//    private Location targetCameraPosition;
//
//    @Nullable
//    private UUID spectating;
//
//    public LabReplayHandler(ReplayFile replayFile, boolean asyncMode) throws IOException {
//
//    }
//}
