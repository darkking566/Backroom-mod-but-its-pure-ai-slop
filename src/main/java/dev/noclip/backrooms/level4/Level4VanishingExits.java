package dev.noclip.backrooms.level4;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Wiki mechanic: "As with many other exits in the Backrooms, once you lose sight of an exit, they may vanish unexpectedly."
 * Wild vanishing exits ({@link Level4Exit#vanishing}) observed by a wanderer are tracked; once every nearby
 * player looks away for ~4 seconds, there is a 55% chance the entire exit (including its stairwell or basement
 * staircase) dissolves silently into ordinary office drywall and carpet with no sound or prompt.
 * M.E.G. Base Omega stairwells and Level 3 elevators are permanent and never vanish.
 */
final class Level4VanishingExits {
	private static final int CHECK_INTERVAL = 10;
	private static final int VANISH_TICKS = 80;
	private static final int SCAN_RADIUS = 18;
	private static final int DEFAULT_VANISH_CHANCE = 55;
	private static final Map<BlockPos, Integer> UNSEEN_TICKS = new HashMap<>();
	private static int vanishChance = DEFAULT_VANISH_CHANCE;
	private static int heldOffUntil;

	private Level4VanishingExits() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Level4VanishingExits::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			UNSEEN_TICKS.clear();
			vanishChance = DEFAULT_VANISH_CHANCE;
			heldOffUntil = 0;
		});
	}

	static void holdOff(MinecraftServer server, int ticks) {
		heldOffUntil = server.getTickCount() + ticks;
	}

	static void setChanceForTest(int percent) {
		vanishChance = percent;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % CHECK_INTERVAL != 0 || now < heldOffUntil) return;
		ServerLevel level = server.getLevel(Level4.LEVEL_4);
		if (level == null || level.players().isEmpty()) return;
		long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());

		// Discover nearby wild vanishing exits when a player looks at them
		BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getLookAngle().normalize();
			for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
				for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
					int x = player.getBlockX() + dx, z = player.getBlockZ() + dz;
					if (Level4Layout.inOmegaBase(seed, x, z) || Level4PoiPlan.isInvertedLightwell(seed, x, z)) continue;
					for (int y = Level4ChunkGenerator.FLOOR_Y - 1; y <= Level4ChunkGenerator.FLOOR_Y + 3; y++) {
						scan.set(x, y, z);
						if (!level.isLoaded(scan)) continue;
						BlockState st = level.getBlockState(scan);
						if (!(st.getBlock() instanceof Level4ExitBlock eb) || !eb.exit.vanishing) continue;
						if (!eb.hatch && st.getValue(Level4ExitBlock.PART) != 0) continue;
						Vec3 toExit = Vec3.atCenterOf(scan).subtract(eye);
						double dist = toExit.length();
						if (dist > 1.2 && dist < SCAN_RADIUS && look.dot(toExit.normalize()) > 0.65) {
							UNSEEN_TICKS.putIfAbsent(scan.immutable(), 0);
						}
					}
				}
			}
		}

		// Update tracked exits: reset while watched, advance when nobody looks at them
		Iterator<Map.Entry<BlockPos, Integer>> it = UNSEEN_TICKS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Integer> entry = it.next();
			BlockPos pos = entry.getKey();
			if (!level.isLoaded(pos)) {
				it.remove();
				continue;
			}
			BlockState st = level.getBlockState(pos);
			if (!(st.getBlock() instanceof Level4ExitBlock eb) || !eb.exit.vanishing
					|| Level4Layout.inOmegaBase(seed, pos.getX(), pos.getZ())
					|| Level4PoiPlan.isInvertedLightwell(seed, pos.getX(), pos.getZ())) {
				it.remove();
				continue;
			}
			boolean watched = false;
			boolean someoneClose = false;
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) continue;
				Vec3 toExit = Vec3.atCenterOf(pos).subtract(player.getEyePosition());
				double dist = toExit.length();
				if (dist < 28.0) someoneClose = true;
				if (dist < 24.0 && player.getLookAngle().normalize().dot(toExit.normalize()) > 0.25) {
					watched = true;
					break;
				}
			}
			if (!someoneClose) {
				it.remove();
				continue;
			}
			if (watched) {
				entry.setValue(0);
			} else {
				int next = entry.getValue() + CHECK_INTERVAL;
				if (next >= VANISH_TICKS) {
					if (level.getRandom().nextInt(100) < vanishChance) {
						dissolve(level, seed, pos, eb);
					}
					it.remove();
				} else {
					entry.setValue(next);
				}
			}
		}
	}

	private static void dissolve(ServerLevel level, long seed, BlockPos pos, Level4ExitBlock eb) {
		if (eb.hatch) {
			level.setBlock(pos, Level4Blocks.OFFICE_CARPET.defaultBlockState(), 3);
			return;
		}
		if (eb.exit.hasStructure()) {
			int cellX = Math.floorDiv(pos.getX(), Level4Plan.CELL) * Level4Plan.CELL;
			int cellZ = Math.floorDiv(pos.getZ(), Level4Plan.CELL) * Level4Plan.CELL;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int x = cellX; x < cellX + Level4Plan.CELL; x++) {
				for (int z = cellZ; z < cellZ + Level4Plan.CELL; z++) {
					Level4Layout.ExitStruct es = Level4Layout.exitStructure(seed, x, z);
					if (es == null || es.exit() != eb.exit) continue;
					for (int y = Level4ChunkGenerator.FLOOR_Y - 3; y <= Level4ChunkGenerator.FLOOR_Y - 1; y++) {
						level.setBlock(p.set(x, y, z), Blocks.CONCRETE.gray().defaultBlockState(), 3);
					}
					boolean backWall = es.depth() == 0;
					level.setBlock(p.set(x, Level4ChunkGenerator.FLOOR_Y, z),
							(backWall ? Level4Blocks.OFFICE_DRYWALL : Level4Blocks.OFFICE_CARPET).defaultBlockState(), 3);
					for (int y = Level4ChunkGenerator.FLOOR_Y + 1; y <= Level4ChunkGenerator.FLOOR_Y + 4; y++) {
						BlockState fill = backWall
								? (y == Level4ChunkGenerator.FLOOR_Y + 1 ? Level4Blocks.OFFICE_DRYWALL : Level4Blocks.OFFICE_DRYWALL_UPPER).defaultBlockState()
								: Blocks.AIR.defaultBlockState();
						level.setBlock(p.set(x, y, z), fill, 3);
					}
					level.setBlock(p.set(x, Level4ChunkGenerator.FLOOR_Y + 5, z), Level4Blocks.OFFICE_ACOUSTIC_CEILING.defaultBlockState(), 3);
					level.setBlock(p.set(x, Level4ChunkGenerator.FLOOR_Y + 6, z), Blocks.CONCRETE.gray().defaultBlockState(), 3);
				}
			}
			return;
		}
		level.setBlock(pos, Level4Blocks.OFFICE_DRYWALL.defaultBlockState(), 3);
		if (level.getBlockState(pos.above()).getBlock() instanceof Level4ExitBlock) {
			level.setBlock(pos.above(), Level4Blocks.OFFICE_DRYWALL_UPPER.defaultBlockState(), 3);
		}
	}
}
