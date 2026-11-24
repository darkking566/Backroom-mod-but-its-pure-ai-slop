package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.wretch.Wretch;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/**
 * Occasionally spawns a hunched, sobbing Wretch in dim corners of Level 1 (outside settlements, peaceful-safe).
 */
final class Wretches {
	private static final int CHECK_INTERVAL = 20 * 65; // every 65 seconds
	private static final int MIN_DIST = 18, MAX_DIST = 34;

	private Wretches() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Wretches::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) return;
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) return;
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			if (random.nextInt(100) >= 28) continue;
			AABB near = player.getBoundingBox().inflate(48, 16, 48);
			if (!level.getEntitiesOfClass(Wretch.class, near).isEmpty()) continue;
			spawnNear(level, seed, player, random);
		}
	}

	private static void spawnNear(ServerLevel level, long seed, ServerPlayer player, RandomSource random) {
		int baseY = player.getBlockY();
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = MIN_DIST + random.nextDouble() * (MAX_DIST - MIN_DIST);
			int bx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
			int bz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
			if (!Level1Layout.tile(seed, bx, bz).walkable() || Marauders.nearSettlement(seed, bx, bz)) continue;
			BlockPos feet = new BlockPos(bx, baseY, bz);
			if (!level.isLoaded(feet) || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) {
				feet = new BlockPos(bx, Level1ChunkGenerator.FLOOR_Y + 1, bz);
				if (!level.isLoaded(feet) || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
			}
			Wretch mob = Monsters.WRETCH.create(level, EntitySpawnReason.NATURAL);
			if (mob == null) return;
			mob.setPos(bx + 0.5, feet.getY(), bz + 0.5);
			level.addFreshEntity(mob);
			return;
		}
	}
}
