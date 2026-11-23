package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Sector;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How each sector of the Habitable Zone dresses the halls: the same floor plan, other materials.
 * The wiki's sectors: Talon (grey concrete, the plain default), Gild (colourful, bright, crates everywhere),
 * Gothic (arches and columns), Tailchase (endless construction), Garden (overgrown) and Legend
 * (red lacquered wood under neon). Only hall blocks are dressed; the white corridors stay as they are.
 */
final class Level1Sectors {
	private static final int FLOOR = Level1ChunkGenerator.FLOOR_Y;
	private static final Block[] GILD_PILLARS = {Blocks.CONCRETE.yellow(), Blocks.CONCRETE.orange(), Blocks.CONCRETE.lightBlue(),
			Blocks.CONCRETE.lime(), Blocks.CONCRETE.red(), Blocks.CONCRETE.purple()};
	private static final Block[] GARDEN_PLANTS = {Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.FERN,
			Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY};
	private static final Block[] NEON = {Blocks.PEARLESCENT_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.OCHRE_FROGLIGHT};

	private Level1Sectors() {
	}

	static BlockState dress(Sector sector, Tile tile, BlockState state, long seed, int x, int y, int z) {
		Block block = state.getBlock();
		int roll = Math.floorMod(Level1Layout.hash(seed, x * 31 + y, z, 17), 100);
		double zone = Level1Layout.pipeZone(seed, x, z);
		if (zone >= 0 && roll < (1 - zone) * 85 && (block == Level1Blocks.GARAGE_PILLAR || block == Level1Blocks.GARAGE_PILLAR_MARKED || block == Level1Blocks.GARAGE_PILLAR_BASE)) {
			return Blocks.RAW_COPPER_BLOCK.defaultBlockState();
		}
		if (sector == Sector.TALON) return state;
		BlockState dressed = switch (sector) {
			case GILD -> gild(block, seed, x, z);
			case GOTHIC -> gothic(block);
			case GARDEN -> garden(block, roll);
			case LEGEND -> legend(block, roll);
			case TAILCHASE -> null;
			case TALON -> null;
		};
		if (dressed != null) return dressed;
		if (state.isAir() && y > FLOOR && y <= FLOOR + 3) {
			// The same roll up a whole column, so poles and heaps stand rather than float.
			BlockState scene = scenery(sector, tile, Math.floorMod(Level1Layout.hash(seed, x, z, 23), 100), y);
			if (scene != null) return scene;
		}
		return state;
	}

	private static BlockState gild(Block block, long seed, int x, int z) {
		if (block == Level1Blocks.GARAGE_PILLAR || block == Level1Blocks.GARAGE_PILLAR_MARKED) {
			int pillar = Math.floorMod(Level1Layout.hash(seed, Math.floorDiv(x, Level1Layout.BAY_X), Math.floorDiv(z, Level1Layout.BAY_Z), 19), GILD_PILLARS.length);
			return GILD_PILLARS[pillar].defaultBlockState();
		}
		if (block == Level1Blocks.GARAGE_WALL) return Blocks.CONCRETE.white().defaultBlockState();
		if (block == Level1Blocks.GARAGE_FLOOR) return Blocks.CONCRETE.lightGray().defaultBlockState();
		return null;
	}

	private static BlockState gothic(Block block) {
		if (block == Level1Blocks.GARAGE_PILLAR || block == Level1Blocks.GARAGE_PILLAR_MARKED) return Blocks.QUARTZ_PILLAR.defaultBlockState();
		if (block == Level1Blocks.GARAGE_PILLAR_BASE) return Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
		if (block == Level1Blocks.GARAGE_WALL) return Blocks.STONE_BRICKS.defaultBlockState();
		if (block == Level1Blocks.GARAGE_FLOOR || block == Level1Blocks.DAMP_GARAGE_FLOOR || block == Level1Blocks.PUDDLED_GARAGE_FLOOR) {
			return Blocks.POLISHED_ANDESITE.defaultBlockState();
		}
		if (block == Level1Blocks.GARAGE_FLOOR_LINE) return Blocks.STONE_BRICKS.defaultBlockState();
		if (block == Level1Blocks.GARAGE_CEILING) return Blocks.DEEPSLATE_TILES.defaultBlockState();
		return null;
	}

	private static BlockState garden(Block block, int roll) {
		if (block == Level1Blocks.GARAGE_FLOOR || block == Level1Blocks.GARAGE_FLOOR_LINE || block == Level1Blocks.DAMP_GARAGE_FLOOR
				|| block == Level1Blocks.PUDDLED_GARAGE_FLOOR) {
			return (roll < 65 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK).defaultBlockState();
		}
		if (block == Level1Blocks.GARAGE_WALL) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		if (block == Level1Blocks.GARAGE_PILLAR || block == Level1Blocks.GARAGE_PILLAR_MARKED || block == Level1Blocks.GARAGE_PILLAR_BASE) {
			return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		}
		if (block == Level1Blocks.GARAGE_CEILING && roll < 35) return Blocks.MOSS_BLOCK.defaultBlockState();
		return null;
	}

	private static BlockState legend(Block block, int roll) {
		if (block == Level1Blocks.GARAGE_FLOOR || block == Level1Blocks.DAMP_GARAGE_FLOOR || block == Level1Blocks.PUDDLED_GARAGE_FLOOR) {
			return Blocks.DARK_OAK_PLANKS.defaultBlockState();
		}
		if (block == Level1Blocks.GARAGE_FLOOR_LINE) return Blocks.CRIMSON_PLANKS.defaultBlockState();
		if (block == Level1Blocks.GARAGE_WALL) return Blocks.CRIMSON_PLANKS.defaultBlockState();
		if (block == Level1Blocks.GARAGE_PILLAR || block == Level1Blocks.GARAGE_PILLAR_MARKED) return Blocks.STRIPPED_CRIMSON_STEM.defaultBlockState();
		if (block == Level1Blocks.GARAGE_PILLAR_BASE) return Blocks.CRIMSON_STEM.defaultBlockState();
		if (block == Level1Blocks.GARAGE_CEILING) return roll < 4 ? NEON[roll % NEON.length].defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState();
		return null;
	}

	/** What fills open air in a sector (plants in the Garden, construction clutter in Tailchase), or null to leave it. */
	private static BlockState scenery(Sector sector, Tile tile, int roll, int y) {
		boolean open = tile == Tile.FLOOR || tile == Tile.DAMP;
		if (!open) return null;
		if (sector == Sector.GARDEN && y == FLOOR + 1 && roll < 22) return GARDEN_PLANTS[roll % GARDEN_PLANTS.length].defaultBlockState();
		if (sector == Sector.TAILCHASE) {
			if (y == FLOOR + 1 && roll < 2) return Blocks.CONCRETE.orange().defaultBlockState();
			if (roll == 2) return Blocks.IRON_BARS.defaultBlockState();
			if (roll == 3 && y <= FLOOR + 2) return Blocks.CONCRETE.yellow().defaultBlockState();
		}
		return null;
	}
}
