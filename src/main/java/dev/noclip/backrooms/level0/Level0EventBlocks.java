package dev.noclip.backrooms.level0;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Interactive architectural blocks for Level 0 micro-events:
 * - {@link LooseCeilingTileBlock}: sagging ceiling tile that sheds dust and can be searched.
 * - {@link HollowWallBlock}: bulging peeling wallpaper that rings hollow when knocked.
 * - {@link GraffitiWallBlock}: sector labels, black arrows, and wanderer warnings whose reading warps at low sanity.
 * - {@link LockedUtilityDoorBlock}: shuttered mini-puzzle door unlocked by nearby breaker / fuse box power.
 */
final class Level0EventBlocks {
	private Level0EventBlocks() {
	}

	static final class LooseCeilingTileBlock extends Block {
		static final BooleanProperty SEARCHED = BooleanProperty.create("searched");
		private static final VoxelShape SAGGING = Block.box(0, -1.5, 0, 16, 16, 16);
		private static final VoxelShape OPENED = Block.box(0, 12, 0, 16, 16, 16);

		LooseCeilingTileBlock(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(SEARCHED, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(SEARCHED);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return state.getValue(SEARCHED) ? OPENED : SAGGING;
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (!state.getValue(SEARCHED) && random.nextInt(14) == 0) {
				level.addParticle(ParticleTypes.ASH,
						pos.getX() + 0.2 + random.nextDouble() * 0.6,
						pos.getY() - 0.1,
						pos.getZ() + 0.2 + random.nextDouble() * 0.6,
						0.0, -0.02, 0.0);
			}
		}
	}

	static final class HollowWallBlock extends Block {
		static final BooleanProperty OPENED = BooleanProperty.create("opened");

		HollowWallBlock(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(OPENED, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(OPENED);
		}
	}

	static final class GraffitiWallBlock extends Block {
		/** 0 = Sector/Pillar code (A-14), 1 = Black arrow, 2 = Survival warning, 3 = Pitfalls escape arrow. */
		static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 3);

		GraffitiWallBlock(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(VARIANT, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(VARIANT);
		}
	}

	static final class LockedUtilityDoorBlock extends HorizontalDirectionalBlock {
		static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
		static final BooleanProperty OPEN = BlockStateProperties.OPEN;
		private static final VoxelShape CLOSED_NS = Block.box(0, 0, 6, 16, 16, 10);
		private static final VoxelShape CLOSED_EW = Block.box(6, 0, 0, 10, 16, 16);

		LockedUtilityDoorBlock(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any()
					.setValue(FACING, Direction.SOUTH)
					.setValue(HALF, DoubleBlockHalf.LOWER)
					.setValue(OPEN, false));
		}

		@Override
		protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return simpleCodec(LockedUtilityDoorBlock::new);
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING, HALF, OPEN);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState()
					.setValue(FACING, context.getHorizontalDirection().getOpposite())
					.setValue(HALF, DoubleBlockHalf.LOWER)
					.setValue(OPEN, false);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			if (state.getValue(OPEN)) return Shapes.empty();
			return state.getValue(FACING).getAxis() == Direction.Axis.Z ? CLOSED_NS : CLOSED_EW;
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			if (state.getValue(OPEN)) return Shapes.empty();
			return state.getValue(FACING).getAxis() == Direction.Axis.Z ? CLOSED_NS : CLOSED_EW;
		}
	}
}
