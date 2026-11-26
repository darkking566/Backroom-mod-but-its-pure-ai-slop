package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Two-block-tall exit door (PART 0..1) or floor hatch leading out of Level 4.
 * Wild exits marked {@link Level4Exit#vanishing} may disappear once a wanderer looks away.
 */
class Level4ExitBlock extends Block {
	static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape HATCH_SHAPE = Block.box(0, 0, 0, 16, 2, 16);
	final Level4Exit exit;
	final boolean hatch;

	Level4ExitBlock(Properties properties, Level4Exit exit) {
		super(properties);
		this.exit = exit;
		this.hatch = isHatch();
		if (hatch) {
			registerDefaultState(stateDefinition.any());
		} else {
			registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(FACING, Direction.SOUTH));
		}
	}

	static Level4ExitBlock create(Properties properties, Level4Exit exit, boolean hatch) {
		return hatch ? new Level4ExitBlock(properties, exit) {
			@Override
			boolean isHatch() {
				return true;
			}
		} : new Level4ExitBlock(properties, exit);
	}

	boolean isHatch() {
		return false;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return hatch ? HATCH_SHAPE : Shapes.block();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			if (exit == Level4Exit.ELEVATOR_3) {
				level.playSound(null, pos, Level4Sounds.ELEVATOR, SoundSource.BLOCKS, 0.9F, 1.0F);
			}
			LevelExits.use(serverPlayer, exit.level);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return hatch ? defaultBlockState() : defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		if (!isHatch()) builder.add(PART, FACING);
	}
}
