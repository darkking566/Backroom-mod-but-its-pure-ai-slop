package dev.noclip.backrooms.level4;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Horizontal-facing block for Level 4 props, fixtures, and radiators. */
class Level4FacingBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final Map<Direction, VoxelShape> shapes;

	Level4FacingBlock(Properties properties, Map<Direction, VoxelShape> shapes) {
		super(properties);
		this.shapes = shapes;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	static Map<Direction, VoxelShape> full() {
		Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
		for (Direction d : Direction.Plane.HORIZONTAL) map.put(d, Shapes.block());
		return map;
	}

	/** Box specified for facing NORTH (front at -Z / z0..z1), rotated to all horizontal directions. */
	static Map<Direction, VoxelShape> northBox(double x0, double y0, double z0, double x1, double y1, double z1) {
		Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
		map.put(Direction.NORTH, Block.box(x0, y0, z0, x1, y1, z1));
		map.put(Direction.SOUTH, Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0));
		map.put(Direction.EAST, Block.box(16 - z1, y0, x0, 16 - z0, y1, x1));
		map.put(Direction.WEST, Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0));
		return map;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state.getValue(FACING));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
