package dev.noclip.backrooms.level2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Heavy overhead pipe bridges spanning the corridor ceiling; leaking ones drip onto wet floors below. */
final class Level2OverheadPipeBlock extends Level2AxisBlock {
	private static final VoxelShape[] SHAPE = turned(0, 6.5, 1.5, 16, 16, 15);
	private final boolean leaking;

	Level2OverheadPipeBlock(Properties properties, boolean leaking) {
		super(properties, SHAPE[0], SHAPE[1]);
		this.leaking = leaking;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!leaking) return;
		if (random.nextInt(3) == 0) {
			double ox = 0.2 + random.nextDouble() * 0.6;
			double oz = 0.2 + random.nextDouble() * 0.6;
			level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + ox, pos.getY() + 0.42, pos.getZ() + oz, 0, 0, 0);
		}
		if (random.nextInt(55) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					Level2Sounds.DRIP, SoundSource.BLOCKS, 0.45F, 0.75F + random.nextFloat() * 0.3F, false);
		}
	}
}
