package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import dev.noclip.backrooms.level4.Level4PoiPlan.PlacedPoi;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.BlenderSinglePropBlock;
import dev.noclip.backrooms.props.MultiPartPropBlock;
import dev.noclip.backrooms.props.PropBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Populates Level 4 on first chunk generation with sparse office leftovers (chairs, folding tables, PCs,
 * file cabinets, glass partitions, cardboard piles) and workstations inside M.E.G. Base Omega.
 * All multi-part props stay strictly within a single chunk and avoid doorways, exits, and atriums.
 */
final class Level4Props {
	private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private Level4Props() {
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (!generated || level.dimension() != Level4.LEVEL_4) return;
			long seed = Level4ChunkGenerator.planSeed(level.getChunkSource().randomState());
			populateChunk(chunk, seed);
		});
	}

	private static void populateChunk(LevelChunk chunk, long seed) {
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		long h = Level4Layout.hash(seed, chunk.getPos().x(), chunk.getPos().z(), 491);
		int roll = Math.floorMod((int) h, 100);
		boolean placed = false;

		// Place workstations inside Omega Base if this chunk overlaps its center
		int benchesPlaced = 0;
		for (int x = minX + 2; x <= minX + 13; x += 4) {
			for (int z = minZ + 2; z <= minZ + 13; z += 4) {
				var tile = Level4Layout.tile(seed, x, z);
				if (tile.zone() == ZoneKind.OMEGA_BASE && tile.kind() == Kind.AIR) {
					Block bench = benchesPlaced == 0 ? BackroomsProps.SALVAGE_BENCH : BackroomsProps.WORKSHOP_BENCH;
					BlockPos pos = new BlockPos(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
					if (canPlace(chunk, minX, minZ, pos, bench, Direction.SOUTH)) {
						place(chunk, pos, bench, Direction.SOUTH);
						placed = true;
						benchesPlaced++;
						if (benchesPlaced >= 2) break;
					}
				}
			}
			if (benchesPlaced >= 2) break;
		}

		// Batch 1 POI 3D furniture inside single-chunk bounds
		int i0 = Math.floorDiv(minX, Level4Plan.CELL), i1 = Math.floorDiv(minX + 15, Level4Plan.CELL);
		int j0 = Math.floorDiv(minZ, Level4Plan.CELL), j1 = Math.floorDiv(minZ + 15, Level4Plan.CELL);
		for (int gi = i0; gi <= i1; gi++) {
			for (int gj = j0; gj <= j1; gj++) {
				int rx = Math.floorDiv(gi, Level4Plan.N), rz = Math.floorDiv(gj, Level4Plan.N);
				int ci = Math.floorMod(gi, Level4Plan.N), cj = Math.floorMod(gj, Level4Plan.N);
				PlacedPoi poi = Level4PoiPlan.of(seed, rx, rz).atCellIndex(Level4Plan.cell(ci, cj));
				if (poi == null || poi.cellI() != ci || poi.cellJ() != cj) continue;
				int bx = gi * Level4Plan.CELL, bz = gj * Level4Plan.CELL;
				if (populatePoiProps(chunk, minX, minZ, bx, bz, poi)) placed = true;
			}
		}

		// Very sparse abandoned office leftovers (~5% of chunks get 1 small prop or leftover desk)
		if (roll < 5) {
			Block prop = pickOfficeProp(h >>> 8);
			int startIdx = Math.floorMod((int) (h >>> 16), 196);
			int facingBase = Math.floorMod((int) (h >>> 24), 4);
			for (int attempt = 0; attempt < 24; attempt++) {
				int cell = (startIdx + attempt * 13) % 196;
				int x = minX + 1 + (cell % 14);
				int z = minZ + 1 + (cell / 14);
				if (Level4PoiPlan.atCell(seed, x, z) != null) continue;
				var tile = Level4Layout.tile(seed, x, z);
				if (tile.kind() != Kind.AIR || (tile.zone() != ZoneKind.OPEN_OFFICE && tile.zone() != ZoneKind.SUITE)) continue;
				BlockPos pos = new BlockPos(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
				Direction facing = HORIZONTALS[(facingBase + attempt) & 3];
				if (canPlace(chunk, minX, minZ, pos, prop, facing)) {
					place(chunk, pos, prop, facing);
					placed = true;
					break;
				}
			}
		}

		// Rare scattered Almond Water bottle clusters (~4% of chunks in open office / suites)
		int waterRoll = Math.floorMod((int) (h >>> 32), 100);
		if (waterRoll < 4) {
			int wStart = Math.floorMod((int) (h >>> 40), 196);
			for (int attempt = 0; attempt < 18; attempt++) {
				int cell = (wStart + attempt * 17) % 196;
				int x = minX + 1 + (cell % 14);
				int z = minZ + 1 + (cell / 14);
				if (Level4PoiPlan.atCell(seed, x, z) != null) continue;
				var tile = Level4Layout.tile(seed, x, z);
				if (tile.kind() != Kind.AIR || (tile.zone() != ZoneKind.OPEN_OFFICE && tile.zone() != ZoneKind.SUITE)) continue;
				BlockPos pos = new BlockPos(x, Level4ChunkGenerator.FLOOR_Y + 1, z);
				if (canPlace(chunk, minX, minZ, pos, Level4Blocks.ALMOND_BOTTLE_CLUSTER, Direction.NORTH)) {
					chunk.setBlockState(pos, Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState(), 0);
					placed = true;
					break;
				}
			}
		}
		if (placed) chunk.markUnsaved();
	}

	private static boolean populatePoiProps(LevelChunk chunk, int minX, int minZ, int bx, int bz, PlacedPoi poi) {
		int y = poi.baseY() + 1;
		boolean any = false;
		switch (poi.kind()) {
			case BREAKROOM_CORNER -> {
				int[] uv = Level4PoiPlan.uvToLocal(poi.dir(), 4, 1);
				any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + uv[0], y, bz + uv[1]),
						BackroomsProps.PROP_POTTED_PLANT_PLASTIC, Direction.SOUTH);
			}
			case SEALED_MEETING_ROOM -> {
				// Folding table (2x1 facing NORTH occupies x, x+1), chairs, cabinet, and damaged PC
				any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 2, y, bz + 3), BackroomsProps.PROP_FOLDING_TABLE, Direction.NORTH);
				any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 2, y, bz + 2), BackroomsProps.PROP_OFFICE_CHAIR, Direction.SOUTH);
				any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 3, y, bz + 4), BackroomsProps.PROP_OFFICE_CHAIR, Direction.NORTH);
				any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 5, y, bz + 1), BackroomsProps.PROP_FILE_CABINET, Direction.SOUTH);
				any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 5, y, bz + 5), BackroomsProps.PROP_DESKTOP_COMPUTER_DAMAGED, Direction.WEST);
			}
			case ACTIVE_TERMINAL_ISLAND -> {
				if (poi.dir() == Level4Plan.SOUTH) {
					any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 2, y, bz + 3), BackroomsProps.PROP_DESKTOP_COMPUTER, Direction.EAST);
					any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 4, y, bz + 3), BackroomsProps.PROP_DESKTOP_COMPUTER, Direction.WEST);
					any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 1, y, bz + 3), BackroomsProps.PROP_OFFICE_CHAIR, Direction.EAST);
					any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 5, y, bz + 3), BackroomsProps.PROP_OFFICE_CHAIR, Direction.WEST);
				} else {
					any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 3, y, bz + 2), BackroomsProps.PROP_DESKTOP_COMPUTER, Direction.SOUTH);
					any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 3, y, bz + 4), BackroomsProps.PROP_DESKTOP_COMPUTER, Direction.NORTH);
					any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 3, y, bz + 1), BackroomsProps.PROP_OFFICE_CHAIR, Direction.SOUTH);
					any |= tryPlacePoi(chunk, minX, minZ, new BlockPos(bx + 3, y, bz + 5), BackroomsProps.PROP_OFFICE_CHAIR, Direction.NORTH);
				}
			}
			default -> {
			}
		}
		return any;
	}

	private static boolean tryPlacePoi(LevelChunk chunk, int minX, int minZ, BlockPos pos, Block block, Direction facing) {
		if (!canPlace(chunk, minX, minZ, pos, block, facing, 1)) return false;
		place(chunk, pos, block, facing);
		return true;
	}

	private static Block pickOfficeProp(long r) {
		int v = Math.floorMod((int) r, 100);
		if (v < 22) return BackroomsProps.PROP_OFFICE_CHAIR;
		if (v < 36) return BackroomsProps.PROP_FOLDING_TABLE;
		if (v < 50) return BackroomsProps.PROP_DESKTOP_COMPUTER;
		if (v < 62) return BackroomsProps.PROP_DESKTOP_COMPUTER_DAMAGED;
		if (v < 72) return BackroomsProps.PROP_FILE_CABINET;
		if (v < 82) return BackroomsProps.PROP_GLASS_PARTITION;
		if (v < 90) return BackroomsProps.PROP_CARDBOARD_PILE;
		if (v < 95) return BackroomsProps.PROP_ELECTRIC_FAN;
		return BackroomsProps.PROP_POTTED_PLANT_PLASTIC;
	}

	private static boolean validFloor(BlockState state) {
		return state.is(Level4Blocks.OFFICE_CARPET)
				|| state.is(Level4Blocks.OFFICE_CARPET_DENT)
				|| state.is(Level4Blocks.OFFICE_DAMP_CARPET);
	}

	private static boolean canPlace(LevelChunk chunk, int minX, int minZ, BlockPos masterPos, Block block, Direction facing) {
		return canPlace(chunk, minX, minZ, masterPos, block, facing, 3);
	}

	private static boolean canPlace(LevelChunk chunk, int minX, int minZ, BlockPos masterPos, Block block, Direction facing, int minOpen) {
		int maxX = minX + 15, maxZ = minZ + 15;
		if (block instanceof MultiPartPropBlock multi) {
			List<BlockPos> cells = multi.occupiedPositions(masterPos, facing);
			for (BlockPos p : cells) {
				if (p.getX() < minX + 1 || p.getX() > maxX - 1 || p.getZ() < minZ + 1 || p.getZ() > maxZ - 1) return false;
				if (!chunk.getBlockState(p).isAir()) return false;
				if (p.getY() == masterPos.getY() && !validFloor(chunk.getBlockState(p.below()))) return false;
			}
			return true;
		}
		if (masterPos.getX() < minX + 1 || masterPos.getX() > maxX - 1 || masterPos.getZ() < minZ + 1 || masterPos.getZ() > maxZ - 1) {
			return false;
		}
		if (!chunk.getBlockState(masterPos).isAir() || !chunk.getBlockState(masterPos.above()).isAir()) return false;
		if (!validFloor(chunk.getBlockState(masterPos.below()))) return false;
		int open = 0;
		for (Direction d : HORIZONTALS) {
			if (chunk.getBlockState(masterPos.relative(d)).isAir()) open++;
		}
		return open >= minOpen;
	}

	private static void place(LevelChunk chunk, BlockPos masterPos, Block block, Direction facing) {
		BlockState masterState = block.defaultBlockState();
		if (masterState.hasProperty(HorizontalDirectionalBlock.FACING)) {
			masterState = masterState.setValue(HorizontalDirectionalBlock.FACING, facing);
		}
		chunk.setBlockState(masterPos, masterState, 0);
		if (block instanceof MultiPartPropBlock multi) {
			chunk.setBlockEntity(new PropBlockEntity(masterPos, masterState));
			BlockState partState = BackroomsProps.PROP_PART.defaultBlockState();
			for (BlockPos cell : multi.occupiedPositions(masterPos, facing)) {
				if (cell.equals(masterPos)) continue;
				chunk.setBlockState(cell, partState, 0);
				PropBlockEntity be = new PropBlockEntity(cell, partState);
				be.setMasterPos(masterPos);
				chunk.setBlockEntity(be);
			}
		} else if (block instanceof BlenderSinglePropBlock) {
			chunk.setBlockEntity(new PropBlockEntity(masterPos, masterState));
		}
	}
}
