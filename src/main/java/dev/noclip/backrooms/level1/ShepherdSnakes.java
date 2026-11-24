package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.shepherdsnake.ShepherdSnake;
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
 * Rarely spawns a colossal wall-smashing Shepherd Snake in wide Level 1 halls (well away from settlements).
 */
final class ShepherdSnakes {
	private static final int CHECK_INTERVAL = 20 * 140; // rare boss-grade encounter: every 140 seconds
	private static final int MIN_DIST = 28, MAX_DIST = 48;

	private ShepherdSnakes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ShepherdSnakes::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) return;
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) return;
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			if (random.nextInt(100) >= 14) continue;
			AABB near = player.getBoundingBox().inflate(96, 24, 96);
			if (!level.getEntitiesOfClass(ShepherdSnake.class, near).isEmpty()) continue;
			spawnNear(level, seed, player, random);
		}
	}

	private static void spawnNear(ServerLevel level, long seed, ServerPlayer player, RandomSource random) {
		int baseY = player.getBlockY();
		for (int attempt = 0; attempt < 20; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = MIN_DIST + random.nextDouble() * (MAX_DIST - MIN_DIST);
			int bx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
			int bz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
			if (!Level1Layout.tile(seed, bx, bz).walkable() || Marauders.nearSettlement(seed, bx, bz)) continue;
			BlockPos feet = new BlockPos(bx, baseY, bz);
			if (!level.isLoaded(feet) || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above(2)).isAir()) {
				feet = new BlockPos(bx, Level1ChunkGenerator.FLOOR_Y + 1, bz);
				if (!level.isLoaded(feet) || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above(2)).isAir()) continue;
			}
			ShepherdSnake mob = Monsters.SHEPHERD_SNAKE.create(level, EntitySpawnReason.NATURAL);
			if (mob == null) return;
			mob.setPos(bx + 0.5, feet.getY(), bz + 0.5);
			level.addFreshEntity(mob);
			return;
		}
	}
}
