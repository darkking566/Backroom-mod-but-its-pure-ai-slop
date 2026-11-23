package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A patch of Level 0 wall that does not quite exist: walk into it and you fall through to Level 1. */
final class FlickeringWallBlock extends Block {
	FlickeringWallBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (entity instanceof ServerPlayer player && level.dimension() == Level1.LEVEL_0) FlickeringWalls.throughWall(player, pos);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) != 0) return;
		Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		double x = pos.getX() + 0.5 + side.getStepX() * 0.52 + (side.getStepX() == 0 ? random.nextDouble() - 0.5 : 0);
		double z = pos.getZ() + 0.5 + side.getStepZ() * 0.52 + (side.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0);
		level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, pos.getY() + random.nextDouble(), z, 0, 0, 0);
	}
}
