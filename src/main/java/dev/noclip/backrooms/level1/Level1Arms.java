package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.arms.Arms;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Spawns ceiling-hanging Arms in Level 1 during blackouts, vanishing them when the lights return.
 * Manually placed Arms (via spawn egg) stay permanently.
 */
final class Level1Arms {
	private static final List<UUID> ACTIVE = new ArrayList<>();
	private static boolean wasDark;

	private Level1Arms() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level1Arms::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(Arms.BLACKOUT_TAG)) entity.discard();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ACTIVE.clear();
			wasDark = false;
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) return;
		boolean dark = Blackouts.phase() == Blackouts.Phase.DARK;
		if (!dark) {
			if (!ACTIVE.isEmpty()) vanishAll(level);
			wasDark = false;
			return;
		}
		if (!wasDark) {
			wasDark = true;
			long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
			RandomSource random = level.getRandom();
			for (ServerPlayer player : level.players()) {
				if (player.isCreative() || player.isSpectator()) continue;
				int count = 1 + random.nextInt(2);
				for (int i = 0; i < count; i++) spawnNear(level, seed, player, random);
			}
		}
	}

	private static void spawnNear(ServerLevel level, long seed, ServerPlayer player, RandomSource random) {
		int baseY = player.getBlockY();
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 8 + random.nextDouble() * 14;
			int bx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
			int bz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
			if (!Level1Layout.tile(seed, bx, bz).walkable() || Marauders.nearSettlement(seed, bx, bz)) continue;
			BlockPos foot = new BlockPos(bx, baseY, bz);
			if (!level.isLoaded(foot) || !level.getBlockState(foot).isAir()) continue;
			// Hang from the real ceiling (2..6 blocks up), so the pale arms dangle at head height in any room.
			int ceiling = -1;
			for (int y = baseY + 2; y <= baseY + 6 && ceiling < 0; y++) {
				if (!level.getBlockState(new BlockPos(bx, y, bz)).isAir()) ceiling = y;
			}
			if (ceiling < 0) continue;
			Arms mob = Monsters.ARMS.create(level, EntitySpawnReason.NATURAL);
			if (mob == null) return;
			mob.setPos(bx + 0.5, Math.max(baseY + 0.5, ceiling - mob.getBbHeight()), bz + 0.5);
			level.addFreshEntity(mob);
			mob.addTag(Arms.BLACKOUT_TAG); // after adding, or the load-time cleanup discards it at once
			ACTIVE.add(mob.getUUID());
			return;
		}
	}

	private static void vanishAll(ServerLevel level) {
		for (UUID id : ACTIVE) {
			Entity e = level.getEntity(id);
			if (e != null) {
				level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 1.2, e.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
				e.discard();
			}
		}
		ACTIVE.clear();
	}
}
