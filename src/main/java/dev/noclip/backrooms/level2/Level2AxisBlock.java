package dev.noclip.backrooms.level2;

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

/** Something that runs along a tunnel: a pipe, a cable tray, a strip light. AXIS is the way it runs. */
class Level2AxisBlock extends Block {
	static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private final VoxelShape alongX, alongZ;

	/** A full block's outline and collision: a bank of pipes fills its column. */
	Level2AxisBlock(Properties properties) {
		this(properties, Shapes.block(), Shapes.block());
	}

	Level2AxisBlock(Properties properties, VoxelShape alongX, VoxelShape alongZ) {
		super(properties);
		this.alongX = alongX;
		this.alongZ = alongZ;
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	/** A box given for a run along X, turned for a run along Z. */
	static VoxelShape[] turned(double x0, double y0, double z0, double x1, double y1, double z1) {
		return new VoxelShape[] {Block.box(x0, y0, z0, x1, y1, z1), Block.box(z0, y0, x0, z1, y1, x1)};
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? alongX : alongZ;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}
}
