package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A fire burning in an oil drum: light and warmth for the camp, whatever the Flickering does. */
final class BarrelFireBlock extends ShapedBlock {
	BarrelFireBlock(Properties properties, VoxelShape shape) {
		super(properties, shape);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5, 0, 0.03, 0);
		}
		if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.3, z, 0, 0.05, 0);
		if (random.nextInt(12) == 0) {
			level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 0.8F + random.nextFloat() * 0.4F, false);
		}
	}
}
