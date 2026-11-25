package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;

/** Where something can be put down in Level 1: solid floor under it and room for its whole body. */
final class Spots {
	private Spots() {
	}

	/** Feet for a body this size at (x, z): first at the player's height, then on the main floor; null if neither fits. */
	static BlockPos feet(ServerLevel level, EntityDimensions size, int x, int playerY, int z) {
		for (int y : new int[] {playerY, Level1ChunkGenerator.FLOOR_Y + 1}) {
			BlockPos at = new BlockPos(x, y, z);
			if (fits(level, size, at)) return at;
		}
		return null;
	}

	/** Whether a block put here would rest on solid floor (the main floor may be carved away into a pit or a basin). */
	static boolean onFloor(ServerLevel level, BlockPos at) {
		BlockPos below = at.below();
		return level.isLoaded(at) && level.getBlockState(at).isAir() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
	}

	/** Whether a body this size stands at these feet: loaded, on solid floor, touching nothing. */
	static boolean fits(ServerLevel level, EntityDimensions size, BlockPos at) {
		if (!level.isLoaded(at)) return false;
		BlockPos below = at.below();
		if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
		return level.noCollision(size.makeBoundingBox(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
	}
}
