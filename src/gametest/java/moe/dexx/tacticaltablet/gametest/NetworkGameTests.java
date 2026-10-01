package moe.dexx.tacticaltablet.gametest;

import java.util.List;
import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.item.ModItems;
import moe.dexx.tacticaltablet.item.TabletSettings;
import moe.dexx.tacticaltablet.net.ServerNetworking;
import moe.dexx.tacticaltablet.strike.PlayerChecks;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

public class NetworkGameTests implements FabricGameTest {
    private static void remove(GameTestHelper helper, ServerPlayer player) {
        if (TacticalTablet.manager() != null) {
            TacticalTablet.manager().forceStop(player.getUUID());
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static StrikeParams aimed(GameTestHelper helper) {
        BlockPos target = TestArena.target(helper);
        return new StrikeParams(true, target.getX(), target.getY(), target.getZ(), 3, 5, 1, 3,
                StrikeType.VISUAL_ONLY, true, true, true);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void launchRequiresTheTabletAndAnAllowedGameMode(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ServerConfig config = new ServerConfig();
            player.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(PlayerChecks.REJECT_NO_TABLET.equals(PlayerChecks.launchReject(player, config)),
                    "a launch without the tablet must be rejected");
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.TACTICAL_TABLET));
            helper.assertTrue(PlayerChecks.launchReject(player, config) == null, "the off hand must count");
            config.allowedGameModes = List.of("creative");
            helper.assertTrue(PlayerChecks.REJECT_GAMEMODE.equals(PlayerChecks.launchReject(player, config)),
                    "a disallowed game mode must be rejected");
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(PlayerChecks.launchReject(player, config) == null, "an allowed game mode must pass");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void launchRequestStartsAStrikeAndSavesTheSettings(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            ItemStack tablet = new ItemStack(ModItems.TACTICAL_TABLET);
            player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
            StrikeParams params = aimed(helper);
            ServerNetworking.handleLaunch(player, params);
            helper.assertTrue(TacticalTablet.manager().strikeOf(player.getUUID()) != null, "the strike did not start");
            helper.assertTrue(TacticalTablet.manager().strikeOf(player.getUUID()).phase() == StrikePhase.COUNTDOWN,
                    "a launched strike must begin with the countdown");
            helper.assertTrue(params.equals(TabletSettings.read(tablet)), "the settings were not saved on the tablet");

            ServerNetworking.handleLaunch(player, params);
            helper.assertTrue(TacticalTablet.manager().active().stream().filter(s -> s.owner().equals(player.getUUID())).count() == 1,
                    "a second request must not start a second strike");

            ServerNetworking.handleCancel(player, false);
            helper.assertTrue(TacticalTablet.manager().strikeOf(player.getUUID()) == null, "the cancel request was ignored");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void requestsWithoutTheTabletDoNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            ServerNetworking.handleLaunch(player, aimed(helper));
            helper.assertTrue(TacticalTablet.manager().strikeOf(player.getUUID()) == null,
                    "a launch without the tablet must not start a strike");
            ServerNetworking.handleUpdateSettings(player, aimed(helper));
            ServerNetworking.handleCancel(player, true);
            ServerNetworking.handleEmergencyStop(player);
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void invalidSettingsAreNotSaved(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack tablet = new ItemStack(ModItems.TACTICAL_TABLET);
            player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
            ServerNetworking.handleUpdateSettings(player, StrikeParams.DEFAULT.withRadius(5000));
            helper.assertTrue(StrikeParams.DEFAULT.equals(TabletSettings.read(tablet)), "invalid settings must not be stored");
            ServerNetworking.handleUpdateSettings(player, StrikeParams.DEFAULT.withRadius(250));
            helper.assertTrue(TabletSettings.read(tablet).radius() == 250, "valid settings must be stored");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }
}
