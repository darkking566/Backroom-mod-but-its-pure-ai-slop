package dev.noclip.backrooms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two-block wooden door set into the Manila Room's four thick walls.
 * Generates open by default so players can walk straight in, and toggles open/shut on right-click.
 */
final class ManilaDoorBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	static final BooleanProperty OPEN = BlockStateProperties.OPEN;

	private static final VoxelShape CLOSED_X = Block.box(0, 0, 6.8, 16, 16, 9.2);
	private static final VoxelShape CLOSED_Z = Block.box(6.8, 0, 0, 9.2, 16, 16);
	private static final VoxelShape OPEN_NORTH = Block.box(0, 0, 1, 2.4, 16, 15);
	private static final VoxelShape OPEN_EAST = Block.box(1, 0, 0, 15, 16, 2.4);
	private static final VoxelShape OPEN_SOUTH = Block.box(13.6, 0, 1, 16, 16, 15);
	private static final VoxelShape OPEN_WEST = Block.box(1, 0, 13.6, 15, 16, 16);

	private final boolean top;

	ManilaDoorBlock(Properties properties, boolean top) {
		super(properties);
		this.top = top;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPEN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(OPEN, false);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Direction facing = state.getValue(FACING);
		if (!state.getValue(OPEN)) {
			return facing.getAxis() == Direction.Axis.Z ? CLOSED_X : CLOSED_Z;
		}
		return switch (facing) {
			case EAST -> OPEN_EAST;
			case SOUTH -> OPEN_SOUTH;
			case WEST -> OPEN_WEST;
			default -> OPEN_NORTH;
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		boolean nextOpen = !state.getValue(OPEN);
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(OPEN, nextOpen), Block.UPDATE_ALL);
			BlockPos otherPos = top ? pos.below() : pos.above();
			BlockState otherState = level.getBlockState(otherPos);
			if (otherState.getBlock() instanceof ManilaDoorBlock otherDoor && otherDoor.top != this.top) {
				level.setBlock(otherPos, otherState.setValue(OPEN, nextOpen), Block.UPDATE_ALL);
			}
			level.playSound(null, pos, nextOpen ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE,
					SoundSource.BLOCKS, 0.9F, 0.95F + level.getRandom().nextFloat() * 0.1F);
		}
		return InteractionResult.SUCCESS;
	}
}
