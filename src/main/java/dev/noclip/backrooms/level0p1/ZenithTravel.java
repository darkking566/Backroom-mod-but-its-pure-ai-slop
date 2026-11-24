package dev.noclip.backrooms.level0p1;

import dev.noclip.backrooms.core.Landing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Arriving in Zenith Station: in an airlock near the outer hull, on the side the player's coordinates point to. */
final class ZenithTravel {
	private static final Logger LOGGER = LoggerFactory.getLogger(Level0p1.class);
	private static final int[][] OFFSETS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 1}, {-2, -1}, {1, 2}, {-1, -2}};

	private ZenithTravel() {
	}

	static boolean enter(ServerPlayer player) {
		ServerLevel station = player.level().getServer().getLevel(Level0p1.LEVEL_0_1);
		if (station == null) return false;
		long seed = ZenithChunkGenerator.planSeed(station.getChunkSource().randomState());
		int[] spot = ZenithLayout.landing(seed, player.getBlockX(), player.getBlockZ());
		for (int[] offset : OFFSETS) {
			Vec3 feet = new Vec3(spot[0] + offset[0] + 0.5, spot[1], spot[2] + offset[1] + 0.5);
			if (Landing.tryLand(player, station, feet)) return true;
		}
		LOGGER.warn("No free landing spot in Zenith Station near {}", java.util.Arrays.toString(spot));
		return false;
	}
}
