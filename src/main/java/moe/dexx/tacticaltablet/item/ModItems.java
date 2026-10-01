package moe.dexx.tacticaltablet.item;

import moe.dexx.tacticaltablet.TacticalTablet;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    public static final Item TACTICAL_TABLET = new TacticalTabletItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, TacticalTablet.id("main"));

    private ModItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, TacticalTablet.id("tactical_tablet"), TACTICAL_TABLET);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.tactical_tablet.main"))
                .icon(() -> new ItemStack(TACTICAL_TABLET))
                .displayItems((parameters, output) -> output.accept(TACTICAL_TABLET))
                .build());
    }
}
