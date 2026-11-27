package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.floors.FloorDifficulty;
import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import dev.noclip.backrooms.monsters.Monsters;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
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
 * the only confirmed species. Spawns are very rare, banned inside M.E.G. Base Omega, and biased toward dark wings.
 * The unverified Smiler sighting rumor is handled 100% client-side in {@code Level4Client} so teammates cannot verify it.
 */
final class Level4Monsters {
	static final String TAG = "noclip_backrooms_level4_monster";
	private static final int INTERVAL = 20 * 45;
	private static final int MAX_NEAR = 1, NEAR = 64, MIN_DIST = 18, MAX_DIST = 34;
	private static int heldOffUntil;

	private Level4Monsters() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level4Monsters::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> heldOffUntil = 0);
	}

	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null) return;

		if (now % INTERVAL != 0 || now < heldOffUntil || level.getDifficulty() == Difficulty.PEACEFUL) return;
		RandomSource rng = level.getRandom();
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());

		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			if (Level4Layout.inOmegaBase(seed, player.getBlockX(), player.getBlockZ())) continue;
			if (Level4PoiPlan.isFrozenFriday(seed, player.getBlockX(), player.getBlockZ())) continue;
			if (countNear(level, player) >= FloorDifficulty.scaledCap(player, MAX_NEAR)) continue;

			int light = level.getBrightness(LightLayer.BLOCK, player.blockPosition());
			int chance = FloorDifficulty.scaledChance(player, light < 4 ? 14 : 3);
			if (rng.nextInt(100) >= chance) continue;

			if (rng.nextInt(100) < 55) {
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
			if (tile.kind() != Kind.AIR || tile.zone() == ZoneKind.OMEGA_BASE || tile.zone() == ZoneKind.AMOR_CAMP
					|| Level4PoiPlan.isFrozenFriday(seed, x, z)) continue;
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
}
