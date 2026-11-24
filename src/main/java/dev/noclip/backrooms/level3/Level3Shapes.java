package dev.noclip.backrooms.level3;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shapes given for one orientation, turned for the others. */
final class Level3Shapes {
	private Level3Shapes() {
	}

	/**
	 * A box given for something facing south (mounted on the north side of its block, sticking out towards +Z),
	 * for each horizontal facing.
	 */
	static Map<Direction, VoxelShape> facing(double x0, double y0, double z0, double x1, double y1, double z1) {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		shapes.put(Direction.SOUTH, Block.box(x0, y0, z0, x1, y1, z1));
		shapes.put(Direction.NORTH, Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0));
		shapes.put(Direction.EAST, Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0));
		shapes.put(Direction.WEST, Block.box(16 - z1, y0, x0, 16 - z0, y1, x1));
		return shapes;
	}

	static Map<Direction, VoxelShape> full() {
		Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, Shapes.block());
		return shapes;
	}

	/** A box given for a run along X, and turned for a run along Z. */
	static VoxelShape[] axis(double x0, double y0, double z0, double x1, double y1, double z1) {
		return new VoxelShape[] {Block.box(x0, y0, z0, x1, y1, z1), Block.box(z0, y0, x0, z1, y1, x1)};
	}
}
