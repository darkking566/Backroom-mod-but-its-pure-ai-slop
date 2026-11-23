package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.level1.Level1Items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Under-table supply cabinet stocked by Manila Mary Foundation volunteers.
 * Right-clicking opens the drawer once and spills Almond Water and rations.
 */
final class ManilaCabinetBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	static final BooleanProperty OPENED = BlockStateProperties.OPEN;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

	ManilaCabinetBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPENED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OPENED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(OPENED, false);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(OPENED)) {
			if (!level.isClientSide()) {
				level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.5F, 1.1F);
			}
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			level.setBlock(pos, state.setValue(OPENED, true), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.9F, 1.0F);
			RandomSource random = level.getRandom();
			BlockPos dropPos = pos.above();
			popResource(level, dropPos, new ItemStack(Level1Items.ALMOND_WATER, 2 + random.nextInt(3)));
			popResource(level, dropPos, new ItemStack(Items.BREAD, 2 + random.nextInt(3)));
			if (random.nextBoolean()) {
				popResource(level, dropPos, new ItemStack(Items.APPLE, 1 + random.nextInt(2)));
			}
			if (random.nextInt(3) == 0) {
				popResource(level, dropPos, new ItemStack(ModItems.FLASHLIGHT));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
