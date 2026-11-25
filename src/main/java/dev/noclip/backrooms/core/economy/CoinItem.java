package dev.noclip.backrooms.core.economy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Money found in the Backrooms: right-click to put the whole stack into the permanent account (Wallet). */
public final class CoinItem extends Item {
	private final int value;

	public CoinItem(Properties properties, int value) {
		super(properties);
		this.value = value;
	}

	public int value() {
		return value;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			ItemStack stack = player.getItemInHand(hand);
			long credits = (long) value * stack.getCount();
			stack.shrink(stack.getCount());
			Wallet.add(serverPlayer, credits);
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.deposited", credits, Wallet.of(serverPlayer)));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.3F);
		}
		return InteractionResult.SUCCESS;
	}
}
