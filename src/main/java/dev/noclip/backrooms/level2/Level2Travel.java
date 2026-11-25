package dev.noclip.backrooms.level2;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Arriving in Level 2: in the middle of a tunnel near the same coordinates, never in a hot stretch. */
final class Level2Travel {
	private static final Logger LOGGER = LoggerFactory.getLogger(Level2.MOD_ID);
	private static final int TRIES = 8;

	private Level2Travel() {
	}

	/** Returns whether the player was moved. */
	static boolean enter(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return false;
		ServerLevel level2 = player.level().getServer().getLevel(Level2.LEVEL_2);
		if (level2 == null) return false;
		long seed = Level2ChunkGenerator.planSeed(level2.getChunkSource().randomState());
		int[] at = dev.noclip.backrooms.core.LevelExits.anchor(player);
		int x = at[0], z = at[1];
		// If players have built on the nearest spot, look a little further along.
		for (int i = 0; i < TRIES; i++) {
			int[] spot = Level2Layout.landing(seed, x + i * Level2Plan.CELL * 3, z);
			Vec3 landing = new Vec3(spot[0] + 0.5, Level2ChunkGenerator.FLOOR_Y + 1, spot[1] + 0.5);
			BlockPos floor = BlockPos.containing(landing).below();
			level2.getChunk(floor);
			if (level2.getBlockState(floor).getCollisionShape(level2, floor).isEmpty()) continue;
			if (!level2.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(landing))) continue;
			player.stopRiding();
			player.teleportTo(level2, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), 0, true);
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
			return true;
		}
		LOGGER.warn("No free landing spot in Level 2 near {}, {}", x, z);
		return false;
	}
}
