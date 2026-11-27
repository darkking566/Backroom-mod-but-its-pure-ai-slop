package dev.noclip.backrooms.level0p1;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0.Level0Space;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The way in from Level 0, as the Wiki has it: so rare that finding it by chance is nearly impossible. Level 0 is one
 * 300 x 300 Stage repeated all round (level0.Level0Space); every second Stage hides one white steel hatch, in a stretch
 * of wall that faces open floor. The spot is read from Level 0's plan, not from loaded blocks, so every copy of the Stage
 * shows the hatch in the same place; it is set into the chunk as it loads, in a wall that is already there, so it never
 * changes where anyone can walk.
 */
public final class ZenithEntrance {
	private static final TagKey<Block> WALLS = TagKey.create(Registries.BLOCK, BackroomsMod.id("level_0_walls"));
	private static final int FLOOR_Y = 32;
	/** Kept off a Stage's edge so the floor it faces is in the same Stage. */
	private static final int MARGIN = 3;
	private static final Map<String, Optional<Hatch>> HATCHES = new ConcurrentHashMap<>();

	/** A hatch: its lower block (in the centre Stage) and the open side it faces. */
	public record Hatch(BlockPos pos, Direction facing) {
	}

	private ZenithEntrance() {
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != Level0.LEVEL_0) return;
			int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
			// The Stages (and copies) this chunk touches: at most two each way.
			for (int sx = Math.floorDiv(minX, Level0Space.PERIOD); sx <= Math.floorDiv(minX + 15, Level0Space.PERIOD); sx++) {
				for (int sz = Math.floorDiv(minZ, Level0Space.PERIOD); sz <= Math.floorDiv(minZ + 15, Level0Space.PERIOD); sz++) {
					int tx = Math.floorDiv(Level0Space.template(sx * Level0Space.PERIOD), Level0Space.PERIOD);
					int tz = Math.floorDiv(Level0Space.template(sz * Level0Space.PERIOD), Level0Space.PERIOD);
					Hatch hatch = hatchInStage(level, tx, tz);
					if (hatch == null) continue;
					BlockPos copy = hatch.pos().offset((sx - tx) * Level0Space.PERIOD, 0, (sz - tz) * Level0Space.PERIOD);
					if (copy.getX() < minX || copy.getX() > minX + 15 || copy.getZ() < minZ || copy.getZ() > minZ + 15) continue;
					// Straight away and on the chunk itself: asking the level for blocks here would wait for this very chunk to finish loading.
					place(chunk, copy, hatch.facing());
				}
			}
		});
	}

	/** The hatch of a centre Stage (Stage coordinates), or null if this Stage has none. */
	public static Hatch hatchInStage(ServerLevel level0, int stx, int stz) {
		long seed = level0.getSeed();
		return HATCHES.computeIfAbsent(seed + ":" + stx + ":" + stz, key -> Optional.ofNullable(find(level0, seed, stx, stz))).orElse(null);
	}

	private static Hatch find(ServerLevel level0, long seed, int stx, int stz) {
		int roll = ZenithLayout.hash(seed, stx, stz, 4321);
		if (roll % 2 != 0) return null;
		int span = Level0Space.PERIOD - 2 * MARGIN;
		int ox = stx * Level0Space.PERIOD + MARGIN, oz = stz * Level0Space.PERIOD + MARGIN;
		int start = Math.floorMod(roll / 2, span * span);
		for (int i = 0; i < span * span; i++) {
			int cell = (start + i * 7919) % (span * span);
			int x = ox + cell % span, z = oz + cell / span;
			if (!isWall(Level0Space.planned(level0, x, FLOOR_Y + 1, z)) || !isWall(Level0Space.planned(level0, x, FLOOR_Y + 2, z))
					|| !isWall(Level0Space.planned(level0, x, FLOOR_Y + 3, z))) continue;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (Level0Space.planned(level0, x + side.getStepX(), FLOOR_Y + 1, z + side.getStepZ()).isAir()
						&& Level0Space.planned(level0, x + side.getStepX(), FLOOR_Y + 2, z + side.getStepZ()).isAir()) {
					return new Hatch(new BlockPos(x, FLOOR_Y + 1, z), side);
				}
			}
		}
		return null;
	}

	private static void place(LevelChunk chunk, BlockPos low, Direction facing) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.maybeHas(state -> state.is(ZenithBlocks.STATION_HATCH))) return;
		}
		if (!isWall(chunk.getBlockState(low)) || !isWall(chunk.getBlockState(low.above()))) return;
		BlockState hatch = ZenithBlocks.STATION_HATCH.defaultBlockState().setValue(StationHatchBlock.FACING, facing);
		chunk.setBlockState(low, hatch.setValue(ZenithPortalBlock.PART, 0), 0);
		chunk.setBlockState(low.above(), hatch.setValue(ZenithPortalBlock.PART, 1), 0);
	}

	private static boolean isWall(BlockState state) {
		return state.is(WALLS);
	}
}
