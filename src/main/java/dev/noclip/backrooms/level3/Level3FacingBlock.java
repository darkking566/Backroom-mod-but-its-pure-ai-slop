package dev.noclip.backrooms.level3;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Something with a front: a conduit or sludge pipe on a wall, an electrical box, a cot. FACING points out from
 * the wall it is mounted on. Some leak black sludge; some machines whir.
 */
class Level3FacingBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** The black sludge, as a falling dust. */
	static final DustParticleOptions SLUDGE = new DustParticleOptions(0x0a0806, 1.2F);

	enum Effect {
		NONE, DRIP, WHIR
	}

	private final Map<Direction, VoxelShape> shapes;
	private final Effect effect;

	Level3FacingBlock(Properties properties, Map<Direction, VoxelShape> shapes, Effect effect) {
		super(properties);
		this.shapes = shapes;
		this.effect = effect;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state.getValue(FACING));
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		switch (effect) {
			case DRIP -> {
				if (random.nextInt(4) == 0) {
					Direction out = state.getValue(FACING);
					level.addParticle(SLUDGE, pos.getX() + 0.5 - out.getStepX() * 0.2 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.3,
							pos.getZ() + 0.5 - out.getStepZ() * 0.2, 0, -0.05, 0);
				}
				if (random.nextInt(80) == 0) {
					level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Level3Sounds.SLUDGE, SoundSource.BLOCKS, 0.4F, 0.6F, false);
				}
			}
			case WHIR -> {
				if (random.nextInt(120) == 0) {
					level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Level3Sounds.HUM, SoundSource.BLOCKS,
							0.35F, 0.6F + random.nextFloat() * 0.2F, false);
				}
			}
			case NONE -> {
			}
		}
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		return defaultBlockState().setValue(FACING, clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}
}
