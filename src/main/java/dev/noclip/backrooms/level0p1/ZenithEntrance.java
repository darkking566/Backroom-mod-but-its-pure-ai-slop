package dev.noclip.backrooms.level0p1;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.level0.Level0;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The way in from Level 0, as the Wiki has it: so rare that finding it by chance is nearly impossible. In every
 * second block of 64x64 chunks (a kilometre across) one flat stretch of wall holds a white steel hatch. It is put
 * in after the chunk loads, in a wall that is already there, so it never changes where anyone can walk.
 */
final class ZenithEntrance {
	private static final TagKey<Block> WALLS = TagKey.create(Registries.BLOCK, BackroomsMod.id("level_0_walls"));
	private static final int REGION = 64;
	private static final int FLOOR_Y = 32;

	private ZenithEntrance() {
	}

	/** The chunk of block (rx, rz) that holds the hatch, or null if this block has none. */
	static int[] targetChunk(long seed, int rx, int rz) {
		int roll = ZenithLayout.hash(seed, rx, rz, 4321);
		if (roll % 2 != 0) return null;
		return new int[] {rx * REGION + roll / 2 % REGION, rz * REGION + roll / 131 % REGION, roll};
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != Level0.LEVEL_0) return;
			int[] target = targetChunk(level.getSeed(), Math.floorDiv(chunk.getPos().x(), REGION), Math.floorDiv(chunk.getPos().z(), REGION));
			if (target == null || chunk.getPos().x() != target[0] || chunk.getPos().z() != target[1]) return;
			// Straight away and on the chunk itself: asking the level for blocks here would wait for this very chunk to finish loading.
			place(chunk, target[2]);
		});
	}

	private static boolean place(LevelChunk chunk, int roll) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.maybeHas(state -> state.is(ZenithBlocks.STATION_HATCH))) return false;
		}
		int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
		int start = roll / 17 % 196;
		for (int i = 0; i < 196; i++) {
			int cell = (start + i) % 196;
			int x = minX + 1 + cell % 14, z = minZ + 1 + cell / 14;
			BlockPos low = new BlockPos(x, FLOOR_Y + 1, z), high = low.above();
			if (!isWall(chunk.getBlockState(low)) || !isWall(chunk.getBlockState(high)) || !isWall(chunk.getBlockState(high.above()))) continue;
			Direction open = null;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (chunk.getBlockState(low.relative(side)).isAir() && chunk.getBlockState(high.relative(side)).isAir()) open = side;
			}
			if (open == null) continue;
			BlockState hatch = ZenithBlocks.STATION_HATCH.defaultBlockState().setValue(StationHatchBlock.FACING, open);
			chunk.setBlockState(low, hatch.setValue(ZenithPortalBlock.PART, 0), 0);
			chunk.setBlockState(high, hatch.setValue(ZenithPortalBlock.PART, 1), 0);
			return true;
		}
		return false;
	}

	private static boolean isWall(BlockState state) {
		return state.is(WALLS);
	}
}
