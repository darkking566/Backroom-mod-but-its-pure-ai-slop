package dev.noclip.backrooms.level1;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Arriving in Level 1: on an aisle of the parking hall nearest to where you left Level 0. */
final class Level1Travel {
	private static final Logger LOGGER = LoggerFactory.getLogger(Level1.MOD_ID);
	private static final int STEPS = 12;

	private Level1Travel() {
	}

	/** Returns whether the player was moved. */
	static boolean enter(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return false;
		ServerLevel level1 = player.level().getServer().getLevel(Level1.LEVEL_1);
		if (level1 == null) return false;

		long seed = Level1ChunkGenerator.planSeed(level1.getChunkSource().randomState());
		int[] spot = Level1Layout.landing(seed, player.getBlockX(), player.getBlockZ());
		// Walk along the aisle past anything a player has built on the landing spot.
		for (int i = 0; i < STEPS; i++) {
			int step = (i + 1) / 2 * (i % 2 == 0 ? 1 : -1);
			int x = spot[0] + step * Level1Layout.BAY_X, z = spot[1];
			if (!Level1Layout.tile(seed, x, z).walkable()) continue;
			Vec3 landing = new Vec3(x + 0.5, Level1ChunkGenerator.FLOOR_Y + 1, z + 0.5);
			BlockPos floor = BlockPos.containing(landing).below();
			level1.getChunk(floor);
			if (level1.getBlockState(floor).getCollisionShape(level1, floor).isEmpty()) continue;
			if (!level1.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(landing))) continue;

			player.stopRiding();
			player.teleportTo(level1, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), 0, true);
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
			return true;
		}
		LOGGER.warn("No free landing spot in Level 1 near {}, {}", spot[0], spot[1]);
		return false;
	}
}
