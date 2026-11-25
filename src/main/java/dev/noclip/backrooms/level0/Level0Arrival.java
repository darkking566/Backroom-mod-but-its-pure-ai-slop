package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.BackroomsMod;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Arriving in Level 0 through an exit from another level (registered with core.LevelExits as "0"):
 * in the middle of a free cell near the same coordinates. Never lands in a Red Room, which is meant to be walked into.
 */
public final class Level0Arrival {
	private static final TagKey<Block> RED_WALLS = TagKey.create(Registries.BLOCK, BackroomsMod.id("level_0_red_walls"));
	private static final int CELL = Maze.CELL, CENTER = Maze.CENTER, STAND_Y = Level0ChunkGenerator.FLOOR_Y + 1, RINGS = 12;
	private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> LEVEL_0 = Level0.LEVEL_0;

	private Level0Arrival() {
	}

	public static boolean arrive(ServerPlayer player) {
		ServerLevel level0 = player.level().getServer().getLevel(LEVEL_0);
		if (level0 == null) return false;
		// Through an exit, a fresh stretch of Level 0 (docs/FLOORS.md); falling in from the Overworld, under the player.
		int[] at = dev.noclip.backrooms.core.LevelExits.anchor(player);
		int cx = Math.floorDiv(at[0], CELL), cz = Math.floorDiv(at[1], CELL);
		for (int ring = 0; ring < RINGS; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
					int x = (cx + i) * CELL + CENTER, z = (cz + j) * CELL + CENTER;
					Vec3 landing = new Vec3(x + 0.5, STAND_Y, z + 0.5);
					if (!free(level0, player, landing)) continue;
					player.stopRiding();
					player.teleportTo(level0, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), 0, true);
					player.setDeltaMovement(Vec3.ZERO);
					player.resetFallDistance();
					return true;
				}
			}
		}
		return false;
	}

	private static boolean free(ServerLevel level, ServerPlayer player, Vec3 landing) {
		BlockPos feet = BlockPos.containing(landing);
		level.getChunk(feet);
		if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) return false;
		if (!level.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(landing))) return false;
		// Every wall within reach of a Red Room cell is red.
		for (int[] d : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
			if (level.getBlockState(feet.offset(d[0], 1, d[1])).is(RED_WALLS)) return false;
		}
		return true;
	}
}
