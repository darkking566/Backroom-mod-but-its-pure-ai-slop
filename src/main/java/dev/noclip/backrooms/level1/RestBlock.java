package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cot or bedroll in a settlement. Lying down on it means settling there: from then on you wake up
 * here instead of at home, until you sleep in a bed somewhere else.
 */
final class RestBlock extends OldComputerBlock {
	private final String settlement;

	RestBlock(Properties properties, VoxelShape shape, int turns, String settlement) {
		super(properties, shape, turns);
		this.settlement = settlement;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		// Where the player stands, beside the cot: always somewhere a player fits.
		LevelData.RespawnData spot = LevelData.RespawnData.of(level.dimension(), serverPlayer.blockPosition(), serverPlayer.getYRot(), 0);
		serverPlayer.setRespawnPosition(new ServerPlayer.RespawnConfig(spot, true), false);
		level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
		serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.settled",
				Component.translatable("settlement.noclip_backrooms." + settlement)));
		return InteractionResult.SUCCESS;
	}
}
