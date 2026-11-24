package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.crawler.Crawler;
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
 * Spawns white-mold Crawlers in Level 1 (with higher odds in the damp Garden sector; peaceful-safe).
 */
final class Crawlers {
	private static final int CHECK_INTERVAL = 20 * 60; // every 60 seconds
	private static final int MIN_DIST = 18, MAX_DIST = 34;

	private Crawlers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Crawlers::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) return;
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) return;
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			boolean inGarden = Level1Layout.sector(seed, player.getBlockX(), player.getBlockZ()) == Level1Layout.Sector.GARDEN;
			int chance = inGarden ? 48 : 22;
			if (random.nextInt(100) >= chance) continue;
			AABB near = player.getBoundingBox().inflate(44, 16, 44);
			if (!level.getEntitiesOfClass(Crawler.class, near).isEmpty()) continue;
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
			Crawler mob = Monsters.CRAWLER.create(level, EntitySpawnReason.NATURAL);
			if (mob == null) return;
			mob.setPos(bx + 0.5, feet.getY(), bz + 0.5);
			level.addFreshEntity(mob);
			return;
		}
	}
}
