package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Supply crates "usually appear behind your back, or after a Flickering event", and "can disappear once
 * not looked at". They turn up on hall floors, preferably against a pillar, wall or rack.
 */
final class Crates {
	private static final int FIRST_DELAY = 40 * 20, DELAY = 60 * 20, DELAY_SPREAD = 90 * 20;
	private static final int MAX_NEAR = 3, NEAR = 32, DEBRIS_PERCENT = 25;
	private static final int VANISH_CHECK = 40, VANISH_CHANCE = 10, VANISH_MIN_DISTANCE = 12;

	private static final List<BlockPos> PLACED = new ArrayList<>();
	private static final Map<UUID, Integer> NEXT = new HashMap<>();

	private Crates() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Crates::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PLACED.clear();
			NEXT.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		if (level == null) return;
		int now = server.getTickCount();
		RandomSource random = level.getRandom();

		if (now % 20 == 0) {
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) continue;
				int next = NEXT.computeIfAbsent(player.getUUID(), id -> now + FIRST_DELAY + random.nextInt(DELAY_SPREAD));
				if (now < next) continue;
				spawnBehind(player, random.nextInt(100) < DEBRIS_PERCENT);
				if (random.nextInt(100) < 40) paintingNear(level, player);
				// Gild Sector is full of crates.
				boolean gild = Level1Layout.sector(Level1ChunkGenerator.planSeed(level.getChunkSource().randomState()), player.getBlockX(), player.getBlockZ()) == Level1Layout.Sector.GILD;
				NEXT.put(player.getUUID(), now + (DELAY + random.nextInt(DELAY_SPREAD)) / (gild ? 3 : 1));
			}
		}
		if (now % VANISH_CHECK == 0) vanishUnwatched(level, random);
	}

	/** A free hall-floor block in front of a corridor doorway near the player, or null. */
	static BlockPos floorBeforeDoor(ServerLevel level, ServerPlayer player) {
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 300; attempt++) {
			int x = player.getBlockX() + random.nextInt(81) - 40, z = player.getBlockZ() + random.nextInt(81) - 40;
			if (Level1Layout.tile(seed, x, z) != Tile.OPENING_SIGN) continue;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int fx = x + side.getStepX(), fz = z + side.getStepZ();
				Tile front = Level1Layout.tile(seed, fx, fz);
				if (front != Tile.FLOOR && front != Tile.LINE && front != Tile.DAMP) continue;
				BlockPos pos = new BlockPos(fx, Level1ChunkGenerator.FLOOR_Y + 1, fz);
				if (Spots.onFloor(level, pos)) return pos;
			}
		}
		return null;
	}

	/** A painting lies before the door to the corridors; like the crates, it is gone once nobody is looking. */
	static BlockPos paintingNear(ServerLevel level, ServerPlayer player) {
		BlockPos pos = floorBeforeDoor(level, player);
		if (pos == null) return null;
		level.setBlock(pos, Level1Blocks.FLOOR_PAINTING.defaultBlockState(), Block.UPDATE_ALL);
		PLACED.add(pos);
		return pos;
	}

	static void afterFlickering(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && level.getRandom().nextInt(100) < 70) spawnBehind(player, false);
		}
	}

	/** Puts a crate on the floor somewhere behind the player; returns where, or null. */
	static BlockPos spawnBehind(ServerPlayer player) {
		return spawnBehind(player, false);
	}

	/** Or a heap of debris, which counts the same and can hold a crowbar. */
	static BlockPos spawnBehind(ServerPlayer player, boolean debris) {
		ServerLevel level = player.level();
		if (level.dimension() != Level1.LEVEL_1) return null;
		int near = 0;
		for (BlockPos pos : PLACED) {
			if (pos.closerThan(player.blockPosition(), NEAR)) near++;
		}
		if (near >= MAX_NEAR) return null;

		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		Vec3 look = player.getLookAngle();
		double behind = Math.atan2(look.z, look.x) + Math.PI;
		for (int attempt = 0; attempt < 40; attempt++) {
			double angle = behind + (random.nextDouble() - 0.5) * 2.2;
			double distance = 5 + random.nextDouble() * 9;
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			Tile tile = Level1Layout.tile(seed, x, z);
			if (tile != Tile.FLOOR && tile != Tile.LINE && tile != Tile.DAMP) continue;
			BlockPos pos = new BlockPos(x, Level1ChunkGenerator.FLOOR_Y + 1, z);
			if (!Spots.onFloor(level, pos) || !level.getBlockState(pos.above()).isAir()) continue;
			// Most of the time tucked against something; now and then out in the open.
			if (attempt < 30 && !againstSomething(level, pos)) continue;

			level.setBlock(pos, (debris ? Level1Blocks.DEBRIS_PILE : Level1Blocks.SUPPLY_CRATE).defaultBlockState(), Block.UPDATE_ALL);
			PLACED.add(pos);
			Strays.maybeBeside(level, pos);
			return pos;
		}
		return null;
	}

	private static boolean againstSomething(ServerLevel level, BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (!level.getBlockState(pos.relative(side)).getCollisionShape(level, pos.relative(side)).isEmpty()) return true;
		}
		return false;
	}

	private static void vanishUnwatched(ServerLevel level, RandomSource random) {
		for (Iterator<BlockPos> it = PLACED.iterator(); it.hasNext(); ) {
			BlockPos pos = it.next();
			if (!level.isLoaded(pos)) continue;
			if (!level.getBlockState(pos).is(Level1Blocks.SUPPLY_CRATE) && !level.getBlockState(pos).is(Level1Blocks.DEBRIS_PILE)
					&& !level.getBlockState(pos).is(Level1Blocks.FLOOR_PAINTING)) {
				it.remove();
				continue;
			}
			if (watched(level, pos) || random.nextInt(100) >= VANISH_CHANCE) continue;
			level.removeBlock(pos, false);
			it.remove();
		}
	}

	/** Someone is close, or looking its way. */
	private static boolean watched(ServerLevel level, BlockPos pos) {
		Vec3 centre = Vec3.atCenterOf(pos);
		for (ServerPlayer player : level.players()) {
			Vec3 toCrate = centre.subtract(player.getEyePosition());
			double distance = toCrate.length();
			if (distance < VANISH_MIN_DISTANCE) return true;
			if (distance < 48 && toCrate.normalize().dot(player.getLookAngle()) > 0.5) return true;
		}
		return false;
	}
}
