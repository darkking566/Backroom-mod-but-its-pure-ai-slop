package dev.noclip.backrooms.level2;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of a pipe bank: pipes bracketed to the wall behind, running across. FACING points away from that
 * wall, into the tunnel. The bank fills its column, so you cannot squeeze past it. Some pipes leak; in the hot
 * stretches some vent steam.
 */
final class Level2BankBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	enum Effect {
		NONE, LEAK, STEAM, FROST, GAS
	}

	private final Effect effect;
	private final Map<Direction, VoxelShape> shapes;

	Level2BankBlock(Properties properties, Effect effect, Map<Direction, VoxelShape> shapes) {
		super(properties);
		this.effect = effect;
		this.shapes = shapes;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
	}

	/** Boxes given for facing SOUTH (wall on north side, pipes jutting toward +Z), turned for all four facings. */
	static Map<Direction, VoxelShape> facing(double[]... boxes) {
		Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			VoxelShape combined = Shapes.empty();
			for (double[] b : boxes) {
				double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
				VoxelShape box = switch (dir) {
					case NORTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
					case EAST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
					case WEST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
					default -> Block.box(x0, y0, z0, x1, y1, z1);
				};
				combined = Shapes.or(combined, box);
			}
			map.put(dir, combined.optimize());
		}
		return map;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state.getValue(FACING));
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		Direction out = state.getValue(FACING);
		double x = pos.getX() + 0.5 + out.getStepX() * 0.45, z = pos.getZ() + 0.5 + out.getStepZ() * 0.45;
		switch (effect) {
			case LEAK -> {
				if (random.nextInt(3) == 0) {
					double along = (random.nextDouble() - 0.5) * 0.8;
					level.addParticle(ParticleTypes.DRIPPING_WATER, x + out.getStepZ() * along, pos.getY() + 0.15, z + out.getStepX() * along, 0, 0, 0);
				}
				if (random.nextInt(60) == 0) {
					level.playLocalSound(x, pos.getY(), z, Level2Sounds.DRIP, SoundSource.BLOCKS, 0.5F, 0.8F + random.nextFloat() * 0.3F, false);
				}
			}
			case STEAM -> {
				for (int i = 0; i < 2; i++) {
					level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 0.4 + random.nextDouble() * 0.3, z,
							out.getStepX() * 0.03, 0.02, out.getStepZ() * 0.03);
				}
				if (random.nextInt(40) == 0) {
					level.playLocalSound(x, pos.getY(), z, Level2Sounds.STEAM, SoundSource.BLOCKS, 0.4F, 0.6F + random.nextFloat() * 0.2F, false);
				}
			}
			case FROST -> {
				if (random.nextInt(3) == 0) {
					double along = (random.nextDouble() - 0.5) * 0.8;
					level.addParticle(ParticleTypes.SNOWFLAKE, x + out.getStepZ() * along, pos.getY() + 0.25 + random.nextDouble() * 0.5,
							z + out.getStepX() * along, out.getStepX() * 0.01, -0.015, out.getStepZ() * 0.01);
				}
			}
			case GAS -> {
				if (random.nextInt(2) == 0) {
					double along = (random.nextDouble() - 0.5) * 0.4;
					level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + out.getStepZ() * along, pos.getY() + 0.35 + random.nextDouble() * 0.2,
							z + out.getStepX() * along, out.getStepX() * 0.04, 0.01, out.getStepZ() * 0.04);
					level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x + out.getStepZ() * along, pos.getY() + 0.3,
							z + out.getStepX() * along, out.getStepX() * 0.02, 0.005, out.getStepZ() * 0.02);
				}
				if (random.nextInt(35) == 0) {
					level.playLocalSound(x, pos.getY(), z, Level2Sounds.STEAM, SoundSource.BLOCKS, 0.45F, 0.35F + random.nextFloat() * 0.15F, false);
				}
			}
			case NONE -> {
			}
		}
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
