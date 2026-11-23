package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.ArrayList;
import java.util.HashMap;
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

/**
 * Settlement teams hang cloth markers (supplies and a note) on the pillars of the halls. They turn up now and then
 * near a wanderer, never more than a couple at a time nearby, and stay put until taken.
 */
final class Markers {
	private static final int FIRST_DELAY = 30 * 20, DELAY = 90 * 20, DELAY_SPREAD = 120 * 20;
	private static final int MAX_NEAR = 2, NEAR = 48;

	private static final List<BlockPos> PLACED = new ArrayList<>();
	private static final Map<UUID, Integer> NEXT = new HashMap<>();

	private Markers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Markers::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PLACED.clear();
			NEXT.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level1.LEVEL_1);
		int now = server.getTickCount();
		if (level == null || now % 20 != 0) return;
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			int next = NEXT.computeIfAbsent(player.getUUID(), id -> now + FIRST_DELAY + random.nextInt(DELAY_SPREAD));
			if (now < next) continue;
			hangNear(level, player);
			NEXT.put(player.getUUID(), now + DELAY + random.nextInt(DELAY_SPREAD));
		}
	}

	/** Hangs a marker at eye height on a pillar 10 to 28 blocks from the player; returns where, or null. */
	static BlockPos hangNear(ServerLevel level, ServerPlayer player) {
		PLACED.removeIf(pos -> !level.isLoaded(pos) || !level.getBlockState(pos).is(Level1Blocks.SETTLEMENT_MARKER));
		int near = 0;
		for (BlockPos pos : PLACED) {
			if (pos.closerThan(player.blockPosition(), NEAR)) near++;
		}
		if (near >= MAX_NEAR) return null;

		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 60; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2, distance = 10 + random.nextDouble() * 18;
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			Tile tile = Level1Layout.tile(seed, x, z);
			if (tile != Tile.FLOOR && tile != Tile.LINE && tile != Tile.DAMP) continue;
			BlockPos pos = new BlockPos(x, Level1ChunkGenerator.FLOOR_Y + 2, z);
			if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos behind = pos.relative(side);
				if (Level1Layout.tile(seed, behind.getX(), behind.getZ()) != Tile.PILLAR) continue;
				level.setBlock(pos, Level1Blocks.SETTLEMENT_MARKER.defaultBlockState()
						.setValue(SettlementMarkerBlock.FACING, side.getOpposite()), Block.UPDATE_ALL);
				PLACED.add(pos);
				return pos;
			}
		}
		return null;
	}
}
