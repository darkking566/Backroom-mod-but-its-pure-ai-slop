package dev.noclip.backrooms;

import dev.noclip.backrooms.level1.Level1Blocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Handles interactive electrical breaker levers inside upper Utility Lofts:
 * throwing a breaker from OFF to ON restores nearby broken fluorescent lights and bare bulbs.
 */
final class StaircaseBreaker {
	private static final int RADIUS_XZ = 24;
	private static final int RADIUS_DOWN = 12;
	private static final int RADIUS_UP = 6;

	private StaircaseBreaker() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.PASS;
			if (!(level instanceof ServerLevel serverLevel) || !serverLevel.dimension().equals(BackroomsMod.LEVEL_0)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = serverLevel.getBlockState(pos);
			if (!state.is(Blocks.LEVER) || pos.getY() <= Level0ChunkGenerator.CEILING_Y) {
				return InteractionResult.PASS;
			}
			long seed = Level0ChunkGenerator.mazeSeed(serverLevel.getChunkSource().randomState());
			StaircasePlan.Complex complex = StaircasePlan.complexAtBlock(seed, pos.getX(), pos.getZ());
			if (complex == null || complex.type() != StaircasePlan.Type.UTILITY_LOFT) {
				return InteractionResult.PASS;
			}

			boolean turningOn = !state.getValue(LeverBlock.POWERED);
			if (turningOn) {
				int restored = restoreNearbyLights(serverLevel, pos);
				serverLevel.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 0.75F);
				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(restored > 0
							? Component.translatable("message.noclip_backrooms.breaker.restored", restored)
							: Component.translatable("message.noclip_backrooms.breaker.already_lit"));
				}
			} else {
				serverLevel.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 0.85F);
			}
			return InteractionResult.PASS;
		});
	}

	private static int restoreNearbyLights(ServerLevel level, BlockPos center) {
		int restored = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -RADIUS_XZ; dx <= RADIUS_XZ; dx++) {
			for (int dz = -RADIUS_XZ; dz <= RADIUS_XZ; dz++) {
				for (int dy = -RADIUS_DOWN; dy <= RADIUS_UP; dy++) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState target = level.getBlockState(cursor);
					if (target.is(ModBlocks.BROKEN_FLUORESCENT_LIGHT)) {
						level.setBlockAndUpdate(cursor, ModBlocks.FLUORESCENT_LIGHT.defaultBlockState());
						restored++;
					} else if (target.is(Level1Blocks.BROKEN_BARE_BULB)) {
						level.setBlockAndUpdate(cursor, Level1Blocks.BARE_BULB.defaultBlockState());
						restored++;
					}
				}
			}
		}
		return restored;
	}
}
