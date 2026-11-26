package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.smiler.Smiler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/**
 * Sparse Level 4 entity ecology: Wiki notes Level 4 is nearly devoid of entities, with Hounds and Dullers being
 * the only confirmed species, plus an unverified Smiler sighting rumor. Spawns are rare, banned inside M.E.G. Base
 * Omega, and strongly biased toward dark wings.
 */
final class Level4Monsters {
	static final String TAG = "noclip_backrooms_level4_monster";
	static final String RUMOR_SMILER_TAG = "noclip_backrooms_level4_rumor_smiler";
	private static final int INTERVAL = 20 * 28;
	private static final int MAX_NEAR = 2, NEAR = 56, MIN_DIST = 18, MAX_DIST = 34;
	private static final Map<UUID, Integer> RUMOR_SMILERS = new HashMap<>();
	private static int heldOffUntil;

	private Level4Monsters() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level4Monsters::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RUMOR_SMILERS.clear();
			heldOffUntil = 0;
		});
	}

	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null) return;

		if (now % 10 == 0 && !RUMOR_SMILERS.isEmpty()) {
			tickRumorSmilers(level, now);
		}

		if (now % INTERVAL != 0 || now < heldOffUntil || level.getDifficulty() == Difficulty.PEACEFUL) return;
		RandomSource rng = level.getRandom();
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());

		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			if (Level4Layout.inOmegaBase(seed, player.getBlockX(), player.getBlockZ())) continue;
			if (countNear(level, player) >= MAX_NEAR) continue;

			int light = level.getBrightness(LightLayer.BLOCK, player.blockPosition());
			int chance = light < 4 ? 24 : 7;
			if (rng.nextInt(100) >= chance) continue;

			int roll = rng.nextInt(100);
			if (roll < 5 && light == 0) {
				spawnRumorSmiler(level, seed, player, now);
			} else if (roll < 55) {
				spawnEntity(level, seed, player, Monsters.DULLER);
			} else {
				spawnEntity(level, seed, player, Monsters.HOUND);
			}
		}
	}

	private static int countNear(ServerLevel level, ServerPlayer player) {
		AABB box = new AABB(player.blockPosition()).inflate(NEAR);
		int count = 0;
		count += level.getEntities(Monsters.HOUND, box, Entity::isAlive).size();
		count += level.getEntities(Monsters.DULLER, box, Entity::isAlive).size();
		return count;
	}

	static <T extends Mob> T spawnEntity(ServerLevel level, long seed, ServerPlayer player, EntityType<T> type) {
		RandomSource rng = level.getRandom();
		T mob = type.create(level, EntitySpawnReason.NATURAL);
		if (mob == null) return null;
		for (int attempt = 0; attempt < 36; attempt++) {
			double angle = rng.nextDouble() * Math.PI * 2;
			double dist = MIN_DIST + rng.nextDouble() * (MAX_DIST - MIN_DIST);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
			var tile = Level4Layout.tile(seed, x, z);
			if (tile.kind() != Kind.AIR || tile.zone() == ZoneKind.OMEGA_BASE || tile.zone() == ZoneKind.AMOR_CAMP) continue;
			BlockPos pos = new BlockPos(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
			if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
			if (level.getBrightness(LightLayer.BLOCK, pos) > 7 && tile.zone() != ZoneKind.DARK_WING) continue;
			mob.snapTo(x + 0.5, pos.getY(), z + 0.5, rng.nextFloat() * 360, 0);
			if (!level.noCollision(mob)) continue;
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
			mob.addTag(TAG);
			level.addFreshEntity(mob);
			return mob;
		}
		mob.discard();
		return null;
	}

	private static void spawnRumorSmiler(ServerLevel level, long seed, ServerPlayer player, int now) {
		Smiler smiler = spawnEntity(level, seed, player, Monsters.SMILER);
		if (smiler == null) return;
		smiler.setNoAi(true);
		smiler.addTag(RUMOR_SMILER_TAG);
		RUMOR_SMILERS.put(smiler.getUUID(), now + 80);
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level4.smiler_rumor"));
	}

	private static void tickRumorSmilers(ServerLevel level, int now) {
		Iterator<Map.Entry<UUID, Integer>> it = RUMOR_SMILERS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> e = it.next();
			Entity entity = level.getEntity(e.getKey());
			if (entity == null || !entity.isAlive()) {
				it.remove();
				continue;
			}
			boolean approached = level.players().stream().anyMatch(p -> !p.isSpectator() && p.distanceTo(entity) < 7.0);
			if (now >= e.getValue() || approached) {
				level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 1.1, entity.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
				entity.discard();
				it.remove();
			}
		}
	}
}
