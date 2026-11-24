package dev.noclip.backrooms.level2;

import dev.noclip.backrooms.level2.Level2Layout.Kind;
import dev.noclip.backrooms.monsters.Monsters;
import java.util.List;
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
 * Spawns the rest of Level 2's documented entities alongside {@link Level2Hounds}:
 * Smilers in the dark (most common after the blackout), Crawlers near tunnels/pipes,
 * Deathmoths, Clumps, Facelings, Wretches, and Skin-Stealers.
 */
final class Level2Monsters {
	static final String TAG = "noclip_backrooms_level2_monster";
	private static final int INTERVAL = 20 * 15;
	private static final int DARK = 4;
	private static final int NEAR = 44, MIN_DIST = 14, MAX_DIST = 30;
	private static final int MAX_MONSTERS_NEAR = 5;
	private static int heldOffUntil;

	record Entry(EntityType<? extends Mob> type, int weight, boolean darkOnly, boolean allowPeaceful) {
	}

	static final List<Entry> POOL = List.of(
			// Smilers are the most common entity in dark stretches after the Great Blackout.
			new Entry(Monsters.SMILER, 30, true, false),
			new Entry(Monsters.CRAWLER, 20, false, false),
			new Entry(Monsters.DEATHMOTH, 16, true, false),
			new Entry(Monsters.CLUMP, 12, true, false),
			new Entry(Monsters.WRETCH, 10, false, false),
			new Entry(Monsters.SKIN_STEALER, 8, false, false),
			new Entry(Monsters.FACELING, 10, false, true)
	);

	private Level2Monsters() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level2Monsters::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> heldOffUntil = 0);
	}

	/** Pauses natural spawns for a while, for tests. */
	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % INTERVAL != 0 || now < heldOffUntil) return;
		ServerLevel level = server.getLevel(Level2.LEVEL_2);
		if (level == null) return;
		boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			if (countNear(level, player) >= MAX_MONSTERS_NEAR) continue;
			boolean inDark = level.getBrightness(LightLayer.BLOCK, player.blockPosition()) < DARK;
			int chance = inDark ? 45 : 22;
			if (random.nextInt(100) >= chance) continue;
			Entry picked = pick(random, inDark, peaceful);
			if (picked != null) spawnOne(player, picked.type());
		}
	}

	private static int countNear(ServerLevel level, ServerPlayer player) {
		AABB box = new AABB(player.blockPosition()).inflate(NEAR);
		int count = 0;
		for (Entry entry : POOL) {
			count += level.getEntities(entry.type(), box, Entity::isAlive).size();
		}
		return count;
	}

	private static Entry pick(RandomSource random, boolean inDark, boolean peaceful) {
		int total = 0;
		for (Entry entry : POOL) {
			if (peaceful && !entry.allowPeaceful()) continue;
			if (entry.darkOnly() && !inDark) continue;
			total += entry.weight();
		}
		if (total <= 0) return null;
		int roll = random.nextInt(total);
		for (Entry entry : POOL) {
			if (peaceful && !entry.allowPeaceful()) continue;
			if (entry.darkOnly() && !inDark) continue;
			roll -= entry.weight();
			if (roll < 0) return entry;
		}
		return null;
	}

	/** Spawns one entity of the given type in a walkable Level 2 spot near the player. */
	static <T extends Mob> T spawnOne(ServerPlayer player, EntityType<T> type) {
		ServerLevel level = player.level();
		long seed = Level2ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		T mob = type.create(level, EntitySpawnReason.NATURAL);
		if (mob == null) return null;
		for (int attempt = 0; attempt < 40; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = MIN_DIST + random.nextDouble() * (MAX_DIST - MIN_DIST);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			Kind kind = Level2Layout.tile(seed, x, z).kind();
			if ((kind != Kind.FLOOR && kind != Kind.ROOM) || Level2Layout.isSafeZone(seed, x, z)) continue;
			BlockPos spot = new BlockPos(x, Level2ChunkGenerator.FLOOR_Y + 1, z);
			if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir() || !level.getBlockState(spot.above()).isAir()) continue;
			mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360, 0);
			if (!level.noCollision(mob)) continue;
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.NATURAL, null);
			mob.addTag(TAG);
			level.addFreshEntity(mob);
			return mob;
		}
		mob.discard();
		return null;
	}
}
