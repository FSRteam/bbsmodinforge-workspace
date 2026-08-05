package mchorse.bbs_together;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;

public final class TogetherCommands
{
    private TogetherCommands()
    {}

    public static void register(
        CommandDispatcher<CommandSourceStack> dispatcher,
        CommandBuildContext context,
        Commands.CommandSelection selection
    )
    {
        dispatcher.register(Commands.literal("bbstogether")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("upload")
                .executes(command -> requestAssetSync(command.getSource())))
            .then(Commands.literal("record")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(command -> toggleRecording(EntityArgument.getPlayers(command, "targets"))))));
    }

    private static int requestAssetSync(CommandSourceStack source)
    {
        int requested = TogetherServerNetwork.requestAssetSync(source.getServer());
        source.sendSuccess(() -> Component.literal("BBS Together: requested asset synchronization for " + requested + " player(s)."), false);
        return requested;
    }

    private static int toggleRecording(Collection<ServerPlayer> players)
    {
        boolean anyRecording = players.stream().anyMatch(TogetherServerNetwork::isPlayerRecording);
        ArrayList<ServerPlayer> selected = new ArrayList<>();

        for (ServerPlayer player : players)
        {
            if (!anyRecording || TogetherServerNetwork.isPlayerRecording(player))
            {
                selected.add(player);
            }
        }

        if (anyRecording)
        {
            for (ServerPlayer player : selected)
            {
                TogetherServerNetwork.setRecording(player, false);
            }
        }
        else
        {
            for (ServerPlayer player : selected)
            {
                TogetherServerNetwork.setRecording(player, true);
            }
        }

        return selected.size();
    }
}
