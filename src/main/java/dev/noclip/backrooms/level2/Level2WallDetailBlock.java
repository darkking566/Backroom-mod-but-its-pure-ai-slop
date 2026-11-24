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
import net.minecraft.world.phys.shapes.VoxelShape;

/** Thin wall-mounted industrial detail (breaker box, pressure gauge, hazard sign). */
final class Level2WallDetailBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final VoxelShape northShape, southShape, eastShape, westShape;

	Level2WallDetailBlock(Properties properties, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		super(properties);
		this.northShape = Block.box(minX, minY, minZ, maxX, maxY, maxZ);
		this.southShape = Block.box(16 - maxX, minY, 16 - maxZ, 16 - minX, maxY, 16 - minZ);
		this.eastShape = Block.box(16 - maxZ, minY, minX, 16 - minZ, maxY, maxX);
		this.westShape = Block.box(minZ, minY, 16 - maxX, maxZ, maxY, 16 - minX);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case SOUTH -> southShape;
			case EAST -> eastShape;
			case WEST -> westShape;
			default -> northShape;
		};
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
