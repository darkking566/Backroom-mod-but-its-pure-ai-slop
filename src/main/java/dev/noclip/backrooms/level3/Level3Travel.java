package dev.noclip.backrooms.level3;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Arriving in Level 3: in the middle of a roomy passage near the same coordinates, clear of hazards. */
final class Level3Travel {
	private static final Logger LOGGER = LoggerFactory.getLogger(Level3.MOD_ID);
	private static final int TRIES = 8;

	private Level3Travel() {
	}

	/** Returns whether the player was moved. */
	static boolean enter(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return false;
		ServerLevel level3 = player.level().getServer().getLevel(Level3.LEVEL_3);
		if (level3 == null) return false;
		long seed = Level3ChunkGenerator.planSeed(level3.getChunkSource().randomState());
		int x = player.getBlockX(), z = player.getBlockZ();
		for (int i = 0; i < TRIES; i++) {
			int[] spot = Level3Layout.landing(seed, x + i * Level3Plan.CELL * 3, z);
			Vec3 landing = new Vec3(spot[0] + 0.5, Level3ChunkGenerator.FLOOR_Y + 1, spot[1] + 0.5);
			BlockPos floor = BlockPos.containing(landing).below();
			level3.getChunk(floor);
			if (level3.getBlockState(floor).getCollisionShape(level3, floor).isEmpty()) continue;
			if (!level3.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(landing))) continue;
			player.stopRiding();
			player.teleportTo(level3, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), 0, true);
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
			return true;
		}
		LOGGER.warn("No free landing spot in Level 3 near {}, {}", x, z);
		return false;
	}
}
