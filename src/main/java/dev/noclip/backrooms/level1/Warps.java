package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Kind;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Habitable Zone is not Euclidean: "you have walked a few dozen seconds and reached what looked miles away", or
 * "wandered minutes and are almost where you began". Now and then, while a wanderer is on the move in the halls, the
 * hall carries them a bay or two further along their way, or back. The jump is a whole number of pillar grids
 * (9 x 16 blocks, so 144) and the player keeps their heading; only the rest of the hall around them is different.
 */
final class Warps {
	private static final int CHECK = 10 * 20, CHANCE = 10, JUMP = Level1Layout.BAY_X * Level1Layout.BAY_Z;
	private static final Map<UUID, Vec3> LAST = new HashMap<>();
	private static int heldOffUntil;

	private Warps() {
	}

	/** No warps for a while, for tests. */
	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Warps::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> LAST.clear());
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % CHECK != 0 || now < heldOffUntil) return;
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null) return;
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()) continue;
			Vec3 here = player.position(), before = LAST.put(player.getUUID(), here);
			if (before == null || before.distanceTo(here) < 6 || random.nextInt(100) >= CHANCE) continue;
			warp(level, player, here.subtract(before), random.nextBoolean() ? 1 : -1, 1 + random.nextInt(2));
		}
	}

	/** Carries the player some jumps along (or against) the way they have been going; returns whether it did. */
	static boolean warp(ServerLevel level, ServerPlayer player, Vec3 heading, int sign, int jumps) {
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		int x = player.getBlockX(), z = player.getBlockZ();
		if (player.getBlockY() != Level1ChunkGenerator.FLOOR_Y + 1) return false; // not from upper decks or tunnels
		if (Level1Layout.kindAt(seed, x, z) == Kind.CORRIDORS || Level1Layout.basePiece(seed, x, z) != null) return false;
		boolean alongX = Math.abs(heading.x) >= Math.abs(heading.z);
		int step = (alongX ? heading.x : heading.z) >= 0 ? 1 : -1;
		int tx = x + (alongX ? step * sign * jumps * JUMP : 0), tz = z + (alongX ? 0 : step * sign * jumps * JUMP);
		Tile tile = Level1Layout.tile(seed, tx, tz);
		if ((tile != Tile.FLOOR && tile != Tile.LINE && tile != Tile.DAMP) || Level1Layout.kindAt(seed, tx, tz) == Kind.CORRIDORS) return false;
		if (Marauders.nearSettlement(seed, tx, tz)) return false;
		Vec3 landing = new Vec3(tx + 0.5, Level1ChunkGenerator.FLOOR_Y + 1, tz + 0.5);
		level.getChunk(BlockPos.containing(landing));
		if (!level.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(landing))) return false;
		BlockPos floor = BlockPos.containing(landing).below(); // pits, basins and shafts have no floor at the usual height
		if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) return false;
		player.teleportTo(level, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), player.getXRot(), true);
		LAST.put(player.getUUID(), landing);
		return true;
	}
}
