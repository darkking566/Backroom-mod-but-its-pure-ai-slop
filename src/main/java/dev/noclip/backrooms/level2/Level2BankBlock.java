package dev.noclip.backrooms.level2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

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

	Level2BankBlock(Properties properties, Effect effect) {
		super(properties);
		this.effect = effect;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
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
