package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.core.LevelExits;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The stable threshold to Level 2: walk far enough toward where the copper pipes and gauges crowd in (see
 * Level1Layout.pipeZone) and you pass through. Until Level 2 exists the way says it is shut, like every other exit.
 */
final class Thresholds {
	private static final double CENTRE = 0.08;

	private Thresholds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Thresholds::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || server.getTickCount() % 20 != 0) return;
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			double zone = Level1Layout.pipeZone(seed, player.getBlockX(), player.getBlockZ());
			if (zone >= 0 && zone <= CENTRE) LevelExits.use(player, "2");
			double glitch = Level1Layout.glitchZone(seed, player.getBlockX(), player.getBlockZ());
			if (glitch >= 0 && glitch <= 0.15) LevelExits.use(player, "38");
		}
	}
}
