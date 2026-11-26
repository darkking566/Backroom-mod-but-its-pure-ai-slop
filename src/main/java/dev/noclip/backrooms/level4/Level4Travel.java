package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.LevelExits;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arriving in Level 4: "Level 4 is easy to leave and easy to return to."
 * If the wanderer has previously settled at M.E.G. Base Omega in Level 4, returning to Level 4 brings them
 * directly back to their settled base; otherwise they arrive in an open office bay near the anchor coordinates.
 */
final class Level4Travel {
	private static final Logger LOGGER = LoggerFactory.getLogger(Level4.MOD_ID);
	private static final int TRIES = 8;

	private Level4Travel() {
	}

	static boolean enter(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return false;
		ServerLevel level4 = player.level().getServer().getLevel(Level4.LEVEL_4);
		if (level4 == null) return false;

		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn != null && respawn.respawnData().dimension() == Level4.LEVEL_4) {
			BlockPos base = respawn.respawnData().pos();
			for (int r = 0; r <= 2; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
						BlockPos target = base.offset(dx, 0, dz);
						Vec3 landing = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
						BlockPos floor = target.below();
						level4.getChunk(floor);
						if (level4.getBlockState(floor).getCollisionShape(level4, floor).isEmpty()) continue;
						if (!level4.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(landing))) continue;
						player.stopRiding();
						player.teleportTo(level4, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), 0, true);
						player.setDeltaMovement(Vec3.ZERO);
						player.resetFallDistance();
						player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.returned_omega"));
						return true;
					}
				}
			}
		}

		long seed = Level4ChunkGenerator.planSeed(level4.getChunkSource().randomState());
		int[] at = LevelExits.anchor(player);
		int x = at[0], z = at[1];
		for (int i = 0; i < TRIES; i++) {
			int[] spot = Level4Layout.landing(seed, x + i * Level4Plan.CELL * 3, z);
			Vec3 landing = new Vec3(spot[0] + 0.5, Level4ChunkGenerator.FLOOR_Y + 1, spot[1] + 0.5);
			BlockPos floor = BlockPos.containing(landing).below();
			level4.getChunk(floor);
			if (level4.getBlockState(floor).getCollisionShape(level4, floor).isEmpty()) continue;
			if (!level4.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(landing))) continue;
			player.stopRiding();
			player.teleportTo(level4, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), 0, true);
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
			return true;
		}
		LOGGER.warn("No free landing spot in Level 4 near {}, {}", x, z);
		return false;
	}
}
