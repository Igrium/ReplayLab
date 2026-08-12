package com.igrium.replaylab.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class PrintCameraCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                CommandBuildContext commandRegistryAccess) {

        dispatcher.register(literal("printcamera").executes(PrintCameraCommand::execPrintCamera));
    }

    private static int execPrintCamera(CommandContext<FabricClientCommandSource> context) {
        var camEnt = context.getSource().getClient().getCameraEntity();
        if (camEnt == null) {
            context.getSource().sendFeedback(Component.literal("No camera entity"));
        } else {
            context.getSource().sendFeedback(
                    Component.literal("The camera is a ").append(camEnt.getClass().getSimpleName())
                            .append(" called ").append(camEnt.getDisplayName()));
        }

        return 0;
    }
}
