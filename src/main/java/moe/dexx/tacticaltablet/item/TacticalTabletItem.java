package moe.dexx.tacticaltablet.item;

import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TacticalTabletItem extends Item {
    /** Set by the client entrypoint; opens the tablet screen. Stays a no-op on a dedicated server. */
    public static Consumer<InteractionHand> clientUseHandler = hand -> {
    };

    public TacticalTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            clientUseHandler.accept(hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
