package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Sector;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How each sector of the Habitable Zone dresses the halls: the same floor plan, other materials, every one of them drawn
 * for this mod at the same 128 px as the rest of Level 1 (nothing here is a stock block).
 * Talon (grey concrete, the plain default), Gild (colourful, bright, crates everywhere), Gothic (columns and vaults),
 * Tailchase (endless construction), Garden (overgrown) and Legend (red lacquer under neon). Only hall blocks are dressed;
 * the white corridors stay as they are, except on a glitch trail.
 */
final class Level1Sectors {
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	private static final Block[] GILD_PILLARS = {Level1Blocks.GILD_PILLAR_0, Level1Blocks.GILD_PILLAR_1, Level1Blocks.GILD_PILLAR_2,
			Level1Blocks.GILD_PILLAR_3, Level1Blocks.GILD_PILLAR_4, Level1Blocks.GILD_PILLAR_5};
	private static final Block[] GARDEN_PLANTS = {Level1Blocks.GARDEN_GRASS, Level1Blocks.GARDEN_GRASS, Level1Blocks.GARDEN_GRASS,
			Level1Blocks.GARDEN_FERN, Level1Blocks.GARDEN_FERN, Level1Blocks.GARDEN_FLOWER_BLUE, Level1Blocks.GARDEN_FLOWER_RED,
			Level1Blocks.GARDEN_FLOWER_YELLOW};
	private static final Block[] NEON = {Level1Blocks.NEON_CYAN, Level1Blocks.NEON_MAGENTA, Level1Blocks.NEON_AMBER};
	/** What a glitch trail turns its walls and floor into: black, with cyan and magenta where it tears. */
	private static final Block[] GLITCH = {Level1Blocks.GLITCH_BLACK, Level1Blocks.GLITCH_MAGENTA, Level1Blocks.GLITCH_CYAN, Level1Blocks.GLITCH_BLACK};

	private Level1Sectors() {
	}

	static BlockState dress(Sector sector, Tile tile, BlockState state, long seed, int x, int y, int z) {
		Block block = state.getBlock();
		int roll = Math.floorMod(Level1Layout.hash(seed, x * 31 + y, z, 17), 100);
		double zone = Level1Layout.pipeZone(seed, x, z);
		if (zone >= 0 && roll < (1 - zone) * 85 && isPillar(block)) return Level1Blocks.COPPER_PILLAR.defaultBlockState();
		if (zone >= 0 && state.isAir()) {
			BlockState piping = piping(seed, tile, zone, roll, x, y, z);
			if (piping != null) return piping;
		}
		double glitch = Level1Layout.glitchZone(seed, x, z);
		if (glitch >= 0 && roll < (1 - glitch) * 55 && (block == Level1Blocks.PAINTED_CONCRETE_WALL || block == Level1Blocks.CORRIDOR_FLOOR)) {
			return GLITCH[roll % GLITCH.length].defaultBlockState();
		}
		if (sector == Sector.TALON) return state;
		BlockState dressed = switch (sector) {
			case GILD -> gild(block, seed, x, z);
			case GOTHIC -> gothic(block);
			case GARDEN -> garden(block, roll);
			case LEGEND -> legend(block, roll);
			case TAILCHASE, TALON -> null;
		};
		if (dressed != null) return dressed;
		if (state.isAir() && y > FLOOR && y <= FLOOR + 3) {
			// The same roll up a whole column, so poles and heaps stand rather than float.
			BlockState scene = scenery(sector, tile, Math.floorMod(Level1Layout.hash(seed, x, z, 23), 100), y);
			if (scene != null) return scene;
		}
		return state;
	}

	private static boolean isPillar(Block block) {
		return block == Level1Blocks.GARAGE_PILLAR || block == Level1Blocks.GARAGE_PILLAR_MARKED || block == Level1Blocks.GARAGE_PILLAR_BASE;
	}

	private static boolean isFloor(Block block) {
		return block == Level1Blocks.GARAGE_FLOOR || block == Level1Blocks.GARAGE_FLOOR_LINE || block == Level1Blocks.DAMP_GARAGE_FLOOR
				|| block == Level1Blocks.PUDDLED_GARAGE_FLOOR;
	}

	private static BlockState gild(Block block, long seed, int x, int z) {
		if (block == Level1Blocks.GARAGE_PILLAR || block == Level1Blocks.GARAGE_PILLAR_MARKED) {
			int pillar = Math.floorMod(Level1Layout.hash(seed, Math.floorDiv(x, Level1Layout.BAY_X), Math.floorDiv(z, Level1Layout.BAY_Z), 19), GILD_PILLARS.length);
			return GILD_PILLARS[pillar].defaultBlockState();
		}
		if (block == Level1Blocks.GARAGE_WALL) return Level1Blocks.GILD_WALL.defaultBlockState();
		if (block == Level1Blocks.GARAGE_FLOOR) return Level1Blocks.GILD_FLOOR.defaultBlockState();
		return null;
	}

	private static BlockState gothic(Block block) {
		if (block == Level1Blocks.GARAGE_PILLAR || block == Level1Blocks.GARAGE_PILLAR_MARKED) return Level1Blocks.GOTHIC_COLUMN.defaultBlockState();
		if (block == Level1Blocks.GARAGE_PILLAR_BASE) return Level1Blocks.GOTHIC_BASE.defaultBlockState();
		if (block == Level1Blocks.GARAGE_WALL) return Level1Blocks.GOTHIC_BRICK.defaultBlockState();
		if (isFloor(block)) return Level1Blocks.GOTHIC_FLOOR.defaultBlockState();
		if (block == Level1Blocks.GARAGE_CEILING) return Level1Blocks.GOTHIC_CEILING.defaultBlockState();
		return null;
	}

	private static BlockState garden(Block block, int roll) {
		if (isFloor(block)) return (roll < 65 ? Level1Blocks.GARDEN_MOSS : Level1Blocks.GARDEN_GRASS_FLOOR).defaultBlockState();
		if (block == Level1Blocks.GARAGE_WALL) return Level1Blocks.GARDEN_WALL.defaultBlockState();
		if (isPillar(block)) return Level1Blocks.GARDEN_PILLAR.defaultBlockState();
		if (block == Level1Blocks.GARAGE_CEILING && roll < 40) return Level1Blocks.GARDEN_CEILING.defaultBlockState();
		return null;
	}

	private static BlockState legend(Block block, int roll) {
		if (block == Level1Blocks.GARAGE_FLOOR || block == Level1Blocks.DAMP_GARAGE_FLOOR || block == Level1Blocks.PUDDLED_GARAGE_FLOOR) {
			return Level1Blocks.LEGEND_FLOOR.defaultBlockState();
		}
		if (block == Level1Blocks.GARAGE_FLOOR_LINE) return Level1Blocks.LEGEND_FLOOR_LINE.defaultBlockState();
		if (block == Level1Blocks.GARAGE_WALL) return Level1Blocks.LEGEND_WALL.defaultBlockState();
		if (block == Level1Blocks.GARAGE_PILLAR || block == Level1Blocks.GARAGE_PILLAR_MARKED) return Level1Blocks.LEGEND_PILLAR.defaultBlockState();
		if (block == Level1Blocks.GARAGE_PILLAR_BASE) return Level1Blocks.LEGEND_BASE.defaultBlockState();
		if (block == Level1Blocks.GARAGE_CEILING) return roll < 4 ? NEON[roll % NEON.length].defaultBlockState() : Level1Blocks.LEGEND_CEILING.defaultBlockState();
		return null;
	}

	/** Thicker pipes overhead and gauges on the pillars the nearer you come to the way into Level 2. */
	private static BlockState piping(long seed, Tile tile, double zone, int roll, int x, int y, int z) {
		boolean open = tile == Tile.FLOOR || tile == Tile.LINE || tile == Tile.DAMP;
		if (!open) return null;
		if (y == Level1ChunkGenerator.CEILING_Y - 1 && roll < (1 - zone) * 60) return Level1Blocks.CEILING_PIPE.defaultBlockState();
		if (y == FLOOR + 2 && roll < (1 - zone) * 45) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (Level1Layout.tile(seed, x + side.getStepX(), z + side.getStepZ()) == Tile.PILLAR) {
					return Level1Blocks.PIPE_GAUGE.defaultBlockState().setValue(PhosphorMarkBlock.FACING, side.getOpposite());
				}
			}
		}
		return null;
	}

	/** What fills open air in a sector (plants in the Garden, construction clutter in Tailchase), or null to leave it. */
	private static BlockState scenery(Sector sector, Tile tile, int roll, int y) {
		boolean open = tile == Tile.FLOOR || tile == Tile.DAMP;
		if (!open) return null;
		if (sector == Sector.GARDEN && y == FLOOR + 1 && roll < 22) return GARDEN_PLANTS[roll % GARDEN_PLANTS.length].defaultBlockState();
		if (sector == Sector.TAILCHASE) {
			if (y == FLOOR + 1 && roll < 2) return Level1Blocks.CONSTRUCTION_CONE.defaultBlockState();
			if (roll == 2) return Level1Blocks.SCAFFOLD_POLE.defaultBlockState();
			if (roll == 3 && y == FLOOR + 1) return Level1Blocks.SITE_MACHINE.defaultBlockState();
		}
		return null;
	}
}
