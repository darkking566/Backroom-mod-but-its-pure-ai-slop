package dev.noclip.backrooms.props;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Single-block Backrooms furniture / half-sliced noclipped prop rendered via Blender 3D mesh
 * (e.g. tilted sliced computer, phone, chair, CRT TV, desk fan, microwave, office chair).
 */
public final class BlenderSinglePropBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<BlenderSinglePropBlock> CODEC = simpleCodec(BlenderSinglePropBlock::new);

	public BlenderSinglePropBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PropShapes.outline(this, state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PropShapes.collisionAt(this, state.getValue(FACING), 0, 0, 0);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PropBlockEntity(pos, state);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel sl) {
			PropBreakEffects.emitCell(sl, pos, state);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
