package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A ceiling pipe that drips onto the floor below; the puddle under it is part of the floor plan. */
final class LeakingPipeBlock extends ShapedBlock {
	LeakingPipeBlock(Properties properties, VoxelShape shape) {
		super(properties, shape);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			double x = pos.getX() + 0.35 + random.nextDouble() * 0.3;
			double z = pos.getZ() + 0.45 + random.nextDouble() * 0.1;
			level.addParticle(ParticleTypes.DRIPPING_WATER, x, pos.getY() + 0.64, z, 0, 0, 0);
		}
		if (random.nextInt(10) == 0) {
			double floor = Level1ChunkGenerator.FLOOR_Y + 1;
			level.playLocalSound(pos.getX() + 0.5, floor, pos.getZ() + 0.5, Level1Sounds.PIPE_DRIP, SoundSource.AMBIENT,
					0.5F, 0.85F + random.nextFloat() * 0.3F, false);
		}
	}
}
