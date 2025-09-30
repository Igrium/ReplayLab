package com.igrium.replaylab.testcommands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class PrintCameraCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                CommandRegistryAccess commandRegistryAccess) {

        dispatcher.register(literal("printcamera").executes(PrintCameraCommand::execPrintCamera));
    }

    private static int execPrintCamera(CommandContext<FabricClientCommandSource> context) {
        var camEnt = context.getSource().getClient().getCameraEntity();
        if (camEnt == null) {
            context.getSource().sendFeedback(Text.literal("No camera entity"));
        } else {
            context.getSource().sendFeedback(
                    Text.literal("The camera is a ").append(camEnt.getClass().getSimpleName())
                            .append(" called ").append(camEnt.getDisplayName()));
        }

        return 0;
    }
}
