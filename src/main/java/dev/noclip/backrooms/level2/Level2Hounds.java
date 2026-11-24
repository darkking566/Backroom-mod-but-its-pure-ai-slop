package dev.noclip.backrooms.level2;

import dev.noclip.backrooms.level2.Level2Layout.Kind;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.hound.Hound;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/**
 * Hounds hunt Level 2 in packs. In the dark stretches (a dead lighting circuit), now and then two of them come
 * up the tunnel behind you; in the light you are left alone.
 */
final class Level2Hounds {
	static final String TAG = "noclip_backrooms_level2_hound";
	private static final int INTERVAL = 5 * 20, CHANCE = 12, DARK = 4;
	private static final int MAX_NEAR = 3, NEAR = 48, NEAREST = 16, FARTHEST = 28;
	private static int heldOffUntil;

	private Level2Hounds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level2Hounds::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> heldOffUntil = 0);
	}

	/** No packs for a while, for tests. */
	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % INTERVAL != 0 || now < heldOffUntil) return;
		ServerLevel level = server.getLevel(Level2.LEVEL_2);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) return;
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) continue;
			if (level.getBrightness(LightLayer.BLOCK, player.blockPosition()) >= DARK) continue;
			if (random.nextInt(100) < CHANCE) spawnPack(player);
		}
	}

	/** Two hounds somewhere in the tunnels behind the player; returns how many came. */
	static int spawnPack(ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.getEntitiesOfClass(Hound.class, new AABB(player.blockPosition()).inflate(NEAR)).size() >= MAX_NEAR) return 0;
		BlockPos spot = findSpot(player);
		if (spot == null) return 0;
		int came = 0;
		for (int i = 0; i < 2; i++) {
			Hound hound = Monsters.HOUND.create(level, EntitySpawnReason.EVENT);
			if (hound == null) continue;
			hound.snapTo(spot.getX() + 0.5 + i * 0.3, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
			hound.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
			hound.addTag(TAG);
			level.addFreshEntity(hound);
			came++;
		}
		return came;
	}

	private static BlockPos findSpot(ServerPlayer player) {
		ServerLevel level = player.level();
		long seed = Level2ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 40; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2, distance = NEAREST + random.nextDouble() * (FARTHEST - NEAREST);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance), z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			if (Level2Layout.tile(seed, x, z).kind() != Kind.FLOOR || Level2Layout.isSafeZone(seed, x, z)) continue;
			BlockPos at = new BlockPos(x, Level2ChunkGenerator.FLOOR_Y + 1, z);
			if (level.isLoaded(at) && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) return at;
		}
		return null;
	}
}
