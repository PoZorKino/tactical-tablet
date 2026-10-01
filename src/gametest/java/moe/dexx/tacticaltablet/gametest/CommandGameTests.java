package moe.dexx.tacticaltablet.gametest;

import moe.dexx.tacticaltablet.TacticalTablet;
import moe.dexx.tacticaltablet.command.TabletCommands;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class CommandGameTests implements FabricGameTest {
    private static int run(GameTestHelper helper, int permissionLevel, String command) {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel()).withPermission(permissionLevel).withSuppressedOutput();
        return helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "commands")
    public void strikeCommandDigsACraterAndStopAllClearsIt(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        BlockPos target = TestArena.target(helper);
        String position = target.getX() + " " + target.getY() + " " + target.getZ();
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 3") == 1, "the strike command failed");
        helper.assertTrue(TacticalTablet.manager().strikeOf(TabletCommands.CONSOLE_OWNER) != null, "no console strike is active");
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 3") == 0,
                "a second console strike must be refused while the first is active");
        helper.assertTrue(run(helper, 2, "tacticaltablet status") == 1, "status must report one strike");
        helper.runAfterDelay(2, () -> {
            helper.assertBlockPresent(Blocks.AIR, 4, 4, 4);
            helper.assertTrue(run(helper, 2, "tacticaltablet stop all") == 1, "stop all must stop one strike");
            helper.assertTrue(TacticalTablet.manager().strikeOf(TabletCommands.CONSOLE_OWNER) == null, "the strike is still active");
            helper.assertTrue(run(helper, 2, "tacticaltablet stop all") == 0, "there is nothing left to stop");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "commands")
    public void commandsRequireOperatorPermission(GameTestHelper helper) {
        TestArena.buildSlab(helper);
        BlockPos target = TestArena.target(helper);
        String position = target.getX() + " " + target.getY() + " " + target.getZ();
        helper.assertTrue(run(helper, 0, "tacticaltablet strike " + position + " 3") == 0, "a non-operator must be refused");
        helper.assertTrue(run(helper, 0, "tacticaltablet stop all") == 0, "a non-operator must be refused");
        helper.runAfterDelay(2, () -> {
            TestArena.assertSlabIntact(helper);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "commands")
    public void strikeCommandRejectsARadiusOutsideTheRange(GameTestHelper helper) {
        BlockPos target = TestArena.target(helper);
        String position = target.getX() + " " + target.getY() + " " + target.getZ();
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 1001") == 0, "radius 1001 must be refused");
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 0") == 0, "radius 0 must be refused");
        helper.assertTrue(run(helper, 2, "tacticaltablet strike " + position + " 3 11") == 0, "power 11 must be refused");
        helper.succeed();
    }
}
