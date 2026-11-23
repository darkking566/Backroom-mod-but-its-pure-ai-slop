package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lock that was simply there, hanging in a room's doorway. Use it and it does lock, and then it is gone from where you
 * are looking, and every doorway of the room is shut for good: "never use a natural lock".
 */
final class NaturalLockBlock extends ShapedBlock {
	NaturalLockBlock(Properties properties, VoxelShape shape) {
		super(properties, shape);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
		long seed = Level1ChunkGenerator.planSeed(server.getChunkSource().randomState());
		int dx = Math.floorDiv(pos.getX(), Level1Layout.DISTRICT), dz = Math.floorDiv(pos.getZ(), Level1Layout.DISTRICT);
		int lx = Math.floorMod(pos.getX(), Level1Layout.DISTRICT), lz = Math.floorMod(pos.getZ(), Level1Layout.DISTRICT);
		for (CorridorPlan.Doorway doorway : Level1Layout.doorways(seed, dx, dz)) {
			if (doorway.lockX() != lx || doorway.lockZ() != lz) continue;
			for (int[] tile : doorway.tiles()) {
				for (int y = Level1ChunkGenerator.FLOOR_Y + 1; y < Level1ChunkGenerator.LOW_CEILING_Y; y++) {
					BlockPos at = new BlockPos(dx * Level1Layout.DISTRICT + tile[0], y, dz * Level1Layout.DISTRICT + tile[1]);
					if (server.getBlockState(at).isAir() || server.getBlockState(at).is(this)) server.setBlock(at, Level1Blocks.SEALED_DOOR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			server.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
			if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.lock_gone"));
			return InteractionResult.SUCCESS;
		}
		server.removeBlock(pos, false);
		return InteractionResult.SUCCESS;
	}
}
