package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.core.floors.FloorDifficulty;
import dev.noclip.backrooms.core.story.FirstTrip;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.bacteria.Bacteria;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
 * Spawns Bacteria (無盡之菌) in the deeper stretches of Level 0:
 * near the landing Level 0 stays eerie and empty; once a wanderer has walked 250+ blocks from landing
 * ({@code FloorDifficulty.band >= 1}) or stepped into a Red Room, Bacteria begin stalking the halls.
 * Never spawns during a newcomer's first trip along the cable or inside the Manila Room sanctuary.
 */
public final class Level0Bacteria {
	public static final String TAG = "noclip_backrooms_level0_bacteria";
	private static final int INTERVAL = 20 * 30;
	private static final int BASE_CHANCE = 18;
	private static final int BASE_CAP = 1;
	private static final int NEAR = 56, MIN_DIST = 18, MAX_DIST = 34;
	private static int heldOffUntil;

	private Level0Bacteria() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level0Bacteria::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> heldOffUntil = 0);
	}

	/** Pauses Bacteria spawns for a while, for tests. */
	public static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % INTERVAL != 0 || now < heldOffUntil) return;
		ServerLevel level = server.getLevel(Level0.LEVEL_0);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) return;
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
			if (FirstTrip.following(player)) continue;
			if (ManilaRoomPlan.nearbyRoom(seed, player.getBlockX(), player.getBlockZ(), 20) != null) continue;
			FloorDifficulty.Scale scale = FloorDifficulty.of(player);
			Maze.Zone zone = Maze.zoneAt(seed, Level0Space.template(player.getBlockX()), Level0Space.template(player.getBlockZ()));
			if (scale.band() < 1 && zone != Maze.Zone.RED_ROOM) continue;
			int cap = FloorDifficulty.scaledCap(player, BASE_CAP);
			if (level.getEntitiesOfClass(Bacteria.class, new AABB(player.blockPosition()).inflate(NEAR)).size() >= cap) continue;
			int chance = FloorDifficulty.scaledChance(player, zone == Maze.Zone.RED_ROOM ? BASE_CHANCE + 14 : BASE_CHANCE);
			if (random.nextInt(100) >= chance) continue;
			spawnNear(player);
		}
	}

	/** Spawns one Bacteria in an open corridor near the player (3 blocks of headroom, away from Manila Room). */
	public static Bacteria spawnNear(ServerPlayer player) {
		ServerLevel level = player.level();
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		Bacteria mob = Monsters.BACTERIA.create(level, EntitySpawnReason.NATURAL);
		if (mob == null) return null;
		int y = Level0ChunkGenerator.FLOOR_Y + 1;
		for (int attempt = 0; attempt < 36; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = MIN_DIST + random.nextDouble() * (MAX_DIST - MIN_DIST);
			int x = Level0Space.template((int) Math.floor(player.getX() + Math.cos(angle) * dist));
			int z = Level0Space.template((int) Math.floor(player.getZ() + Math.sin(angle) * dist));
			if (ManilaRoomPlan.roomAtBlock(seed, x, z) != null) continue;
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.isLoaded(pos)) continue;
			if (level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) continue;
			if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above(2)).isAir()) continue;
			mob.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
			if (!level.noCollision(mob)) continue;
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
			level.addFreshEntity(mob);
			mob.addTag(TAG);
			return mob;
		}
		mob.discard();
		return null;
	}
}
