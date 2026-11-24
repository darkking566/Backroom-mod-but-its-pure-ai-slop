package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A settlement counter that sells for alloy: hold one, right-click, and what the counter stocks (its loot table) is handed over. */
final class CounterBlock extends ShapedBlock {
	private final String deal;

	CounterBlock(Properties properties, VoxelShape shape, String deal) {
		super(properties, shape);
		this.deal = deal;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		ItemStack held = player.getMainHandItem();
		if (!held.is(Level1Items.ALLOY)) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.counter_" + deal + "_ask"));
			return InteractionResult.SUCCESS;
		}
		if (!player.getAbilities().instabuild) held.shrink(1);
		server.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
		Block.dropResources(state, server, pos, null, player, ItemStack.EMPTY);
		serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.counter_" + deal + "_done"));
		return InteractionResult.SUCCESS;
	}
}
