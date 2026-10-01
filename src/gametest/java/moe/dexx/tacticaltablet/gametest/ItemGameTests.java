package moe.dexx.tacticaltablet.gametest;

import moe.dexx.tacticaltablet.destruction.ModTags;
import moe.dexx.tacticaltablet.item.ModItems;
import moe.dexx.tacticaltablet.item.TabletSettings;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class ItemGameTests implements FabricGameTest {
    private static final ResourceLocation TABLET_ID = new ResourceLocation("tactical_tablet", "tactical_tablet");

    @GameTest(template = EMPTY_STRUCTURE)
    public void itemIsRegisteredAndDoesNotStack(GameTestHelper helper) {
        Item item = BuiltInRegistries.ITEM.get(TABLET_ID);
        helper.assertTrue(item == ModItems.TACTICAL_TABLET, "the tablet item is not registered");
        helper.assertTrue(new ItemStack(item).getMaxStackSize() == 1, "the tablet must not stack");
        helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.get(ModItems.TAB_KEY) != null, "the creative tab is missing");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void recipeIsLoaded(GameTestHelper helper) {
        boolean present = helper.getLevel().getServer().getRecipeManager().byKey(TABLET_ID).isPresent();
        helper.assertTrue(present, "the crafting recipe did not load");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void protectedTagIsLoaded(GameTestHelper helper) {
        helper.assertTrue(Blocks.BEDROCK.defaultBlockState().is(ModTags.STRIKE_PROTECTED), "bedrock must be protected");
        helper.assertTrue(Blocks.END_PORTAL_FRAME.defaultBlockState().is(ModTags.STRIKE_PROTECTED), "end portal frame must be protected");
        helper.assertTrue(Blocks.END_GATEWAY.defaultBlockState().is(ModTags.STRIKE_PROTECTED), "end gateway must be protected");
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(ModTags.STRIKE_PROTECTED), "stone must not be protected");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void freshTabletHasDefaultSettings(GameTestHelper helper) {
        StrikeParams params = TabletSettings.read(new ItemStack(ModItems.TACTICAL_TABLET));
        helper.assertTrue(params.equals(StrikeParams.DEFAULT), "a fresh tablet must report the defaults");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void settingsSurviveARoundTrip(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.TACTICAL_TABLET);
        StrikeParams written = new StrikeParams(true, -120, 71, 3456, 777, 9, 7, 42, StrikeType.VISUAL_ONLY, false, false, false);
        TabletSettings.write(stack, written);
        helper.assertTrue(written.equals(TabletSettings.read(stack)), "settings changed in a round trip");
        helper.assertTrue(written.equals(TabletSettings.read(stack.copy())), "settings were lost when the stack was copied");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void garbageNbtFallsBackToDefaults(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.TACTICAL_TABLET);
        CompoundTag tag = stack.getOrCreateTagElement(TabletSettings.ROOT);
        tag.putString("Radius", "huge");
        tag.putInt("Power", 9999);
        tag.putInt("Salvos", -4);
        tag.putString("Type", "NUKE");
        tag.putString("DestroyBlocks", "maybe");
        StrikeParams params = TabletSettings.read(stack);
        helper.assertTrue(params.radius() == 50, "a non-numeric radius must fall back to the default");
        helper.assertTrue(params.power() == 10, "power must be brought into range");
        helper.assertTrue(params.salvos() == 1, "salvos must be brought into range");
        helper.assertTrue(params.type() == StrikeType.ORBITAL_LASER, "an unknown type must fall back to the default");
        helper.assertTrue(params.destroyBlocks(), "a non-numeric flag must fall back to the default");
        helper.assertTrue(params.countdownSeconds() == 10, "a missing key must fall back to the default");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void oversizedRadiusInNbtIsBroughtIntoRange(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModItems.TACTICAL_TABLET);
        stack.getOrCreateTagElement(TabletSettings.ROOT).putInt("Radius", 99_999);
        helper.assertTrue(TabletSettings.read(stack).radius() == 1000, "radius must not exceed 1000");
        helper.succeed();
    }
}
