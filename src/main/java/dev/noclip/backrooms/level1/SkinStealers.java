package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.skinstealer.SkinStealer;
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
 * Rarely spawns a solitary Skin-Stealer in Level 1 halls (outside settlements, even when lights are on)
 * to keep exploration tense between blackouts.
 */
final class SkinStealers {
	private static final int CHECK_INTERVAL = 20 * 75; // every 75 seconds
	private static final int MIN_DIST = 22, MAX_DIST = 38;

	private SkinStealers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SkinStealers::tick);
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
			if (!level.getEntitiesOfClass(SkinStealer.class, near).isEmpty()) continue;
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
			BlockPos feet = Spots.feet(level, Monsters.SKIN_STEALER.getDimensions(), bx, baseY, bz);
			if (feet == null) continue;
			SkinStealer mob = Monsters.SKIN_STEALER.create(level, EntitySpawnReason.NATURAL);
			if (mob == null) return;
			mob.setPos(bx + 0.5, feet.getY(), bz + 0.5);
			level.addFreshEntity(mob);
			return;
		}
	}
}
