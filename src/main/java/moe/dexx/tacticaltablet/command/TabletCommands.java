package moe.dexx.tacticaltablet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.UUID;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.strike.Strike;
import moe.dexx.tacticaltablet.strike.StrikeManager;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TabletCommands {
    /** Owner of strikes started from the console or a command block. */
    public static final UUID CONSOLE_OWNER = new UUID(0L, 0L);

    private TabletCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("tacticaltablet")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("stop")
                                .executes(context -> stop(context.getSource(), ownerOf(context.getSource())))
                                .then(Commands.literal("all").executes(context -> stopAll(context.getSource())))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> stop(context.getSource(),
                                                EntityArgument.getPlayer(context, "player").getUUID()))))
                        .then(Commands.literal("strike")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(StrikeParams.MIN_RADIUS, StrikeParams.MAX_RADIUS))
                                                .executes(context -> strike(context, StrikeParams.DEFAULT.power()))
                                                .then(Commands.argument("power", IntegerArgumentType.integer(StrikeParams.MIN_POWER, StrikeParams.MAX_POWER))
                                                        .executes(context -> strike(context, IntegerArgumentType.getInteger(context, "power")))))))));
    }

    private static UUID ownerOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? player.getUUID() : CONSOLE_OWNER;
    }

    private static int status(CommandSourceStack source) {
        StrikeManager manager = TacticalTablet.manager();
        String averageTick = String.format(Locale.ROOT, "%.1f", TacticalTablet.tickTimes().averageMs());
        source.sendSuccess(() -> Component.translatable("tactical_tablet.command.status.server", averageTick), false);
        if (manager == null || manager.active().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("tactical_tablet.command.status.none"), false);
            return 0;
        }
        for (Strike strike : manager.active()) {
            BlockPos target = strike.target();
            int done = strike.job() == null ? 0 : strike.job().chunksDone();
            int total = strike.job() == null ? 0 : strike.job().chunksTotal();
            source.sendSuccess(() -> Component.translatable("tactical_tablet.command.status.line",
                    strike.owner().toString(), strike.phase().name(), target.getX(), target.getY(), target.getZ(),
                    strike.params().radius(), done, total), false);
        }
        return manager.active().size();
    }

    private static int stop(CommandSourceStack source, UUID owner) {
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null || !manager.forceStop(owner)) {
            source.sendFailure(Component.translatable("tactical_tablet.reject.nothing_to_stop"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("tactical_tablet.command.stop.done", 1), true);
        return 1;
    }

    private static int stopAll(CommandSourceStack source) {
        StrikeManager manager = TacticalTablet.manager();
        int stopped = manager == null ? 0 : manager.forceStopAll();
        if (stopped == 0) {
            source.sendFailure(Component.translatable("tactical_tablet.reject.nothing_to_stop"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("tactical_tablet.command.stop.done", stopped), true);
        return stopped;
    }

    private static int strike(CommandContext<CommandSourceStack> context, int power) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        StrikeManager manager = TacticalTablet.manager();
        if (manager == null) {
            return 0;
        }
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        StrikeParams params = StrikeParams.DEFAULT.withTarget(pos.getX(), pos.getY(), pos.getZ()).withRadius(radius).withPower(power);
        // Skips the countdown and the cinematic: the strike starts at impact.
        String reject = manager.launch(ownerOf(source), source.getLevel(), params, StrikePhase.IMPACT);
        if (reject != null) {
            source.sendFailure(Component.translatable(reject));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("tactical_tablet.command.strike.started",
                pos.getX(), pos.getY(), pos.getZ(), radius, power), true);
        return 1;
    }
}
