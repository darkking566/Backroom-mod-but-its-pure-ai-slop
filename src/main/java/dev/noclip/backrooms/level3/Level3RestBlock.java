package dev.noclip.backrooms.level3;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * A cot in Base Gamma: right-click to settle here so respawning in the Backrooms brings you back to this outpost.
 */
final class Level3RestBlock extends Level3FacingBlock {
	private final String settlement;

	Level3RestBlock(Properties properties, Map<Direction, VoxelShape> shapes, String settlement) {
		super(properties, shapes, Effect.NONE);
		this.settlement = settlement;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		LevelData.RespawnData spot = LevelData.RespawnData.of(level.dimension(), serverPlayer.blockPosition(), serverPlayer.getYRot(), 0);
		// Settling, like a cot anywhere else: you wake up here, and the people here can take you home.
		dev.noclip.backrooms.core.trip.Trips.settle(serverPlayer, spot);
		level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F);
		serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.settled",
				Component.translatable("settlement.noclip_backrooms." + settlement)));
		return InteractionResult.SUCCESS;
	}
}
