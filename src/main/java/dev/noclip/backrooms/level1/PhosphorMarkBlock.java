package dev.noclip.backrooms.level1;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A glowing smear of phosphor on the face of the block behind it; FACING points away from that block. */
final class PhosphorMarkBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
			Direction.UP, Block.box(2, 0, 2, 14, 0.5, 14),
			Direction.DOWN, Block.box(2, 15.5, 2, 14, 16, 14),
			Direction.NORTH, Block.box(2, 2, 15.5, 14, 14, 16),
			Direction.SOUTH, Block.box(2, 2, 0, 14, 14, 0.5),
			Direction.EAST, Block.box(0, 2, 2, 0.5, 14, 14),
			Direction.WEST, Block.box(15.5, 2, 2, 16, 14, 14)));

	PhosphorMarkBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
