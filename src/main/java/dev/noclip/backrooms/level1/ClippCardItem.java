package dev.noclip.backrooms.level1;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The Ariane Circle's C.L.I.P.P. card: what to do on arriving anywhere new. Right-click to read it. */
final class ClippCardItem extends Item {
	ClippCardItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.translatable("message.noclip_backrooms.clipp"));
		}
		return InteractionResult.SUCCESS;
	}
}
