package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A crate with eyes peering out between the planks. Open it and it gives up something good, and the eyes close for good. */
final class EyedCrateBlock extends Block {
	EyedCrateBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
			server.playSound(null, pos, Level1Sounds.EYES, SoundSource.BLOCKS, 1.0F, 0.8F);
			server.playSound(null, pos, Level1Sounds.CRATE_OPEN, SoundSource.BLOCKS, 0.9F, 0.8F);
			Block.dropResources(state, server, pos, null, player, ItemStack.EMPTY);
			server.setBlock(pos, Level1Blocks.SHUT_EYED_CRATE.defaultBlockState(), Block.UPDATE_ALL);
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.eyes_shut"));
		}
		return InteractionResult.SUCCESS;
	}
}
