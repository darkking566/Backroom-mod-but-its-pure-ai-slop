package dev.noclip.backrooms.level3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The things in Level 3 that hurt: fallen live wires, puddles of black sludge, sparking outlets, conveyor belts. */
final class Level3HazardBlocks {
	private Level3HazardBlocks() {
	}

	static boolean exposed(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive() && !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
	}

	static void shock(ServerLevel level, LivingEntity entity, BlockPos at, float damage) {
		if (entity.hurtServer(level, level.damageSources().lightningBolt(), damage)) {
			level.playSound(null, at, Level3Sounds.SHOCK, SoundSource.BLOCKS, 0.6F, 1.6F + level.getRandom().nextFloat() * 0.3F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + 0.4, entity.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
		}
	}

	/** A fallen live wire across the floor: it sparks, and it shocks whatever steps on it. */
	static final class LiveWire extends Block {
		private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1.5, 16);

		LiveWire(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
			if (level instanceof ServerLevel server && exposed(entity)) shock(server, (LivingEntity) entity, pos, 3.0F);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(3) != 0) return;
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + 0.1, pos.getZ() + random.nextDouble(),
					(random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
			if (random.nextInt(10) == 0) {
				level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Level3Sounds.SPARK, SoundSource.BLOCKS, 0.4F, 1.4F, false);
			}
		}
	}

	/** Black sludge, scalding and toxic: it burns, it sickens, it slows you down. */
	static final class Sludge extends Block {
		private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

		Sludge(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
			if (!(level instanceof ServerLevel server) || !exposed(entity)) return;
			LivingEntity living = (LivingEntity) entity;
			entity.makeStuckInBlock(state, new Vec3(0.6, 1.0, 0.6));
			if (living.hurtServer(server, server.damageSources().hotFloor(), 2.0F)) {
				living.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, true, false, true));
				server.playSound(null, pos, Level3Sounds.SLUDGE, SoundSource.BLOCKS, 0.6F, 0.7F);
			}
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(6) == 0) {
				level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.1, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
			}
		}
	}

	/** An outlet in the wall, warm and sparking. Touch it and it bites. */
	static final class Outlet extends Block {
		Outlet(Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (level instanceof ServerLevel server && exposed(player)) shock(server, player, pos, 2.0F);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(5) != 0) return;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos out = pos.relative(side);
				if (!level.getBlockState(out).isAir()) continue;
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + side.getStepX() * 0.55, pos.getY() + 0.35,
						pos.getZ() + 0.5 + side.getStepZ() * 0.55, side.getStepX() * 0.1, 0.05, side.getStepZ() * 0.1);
			}
			if (random.nextInt(8) == 0) {
				level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, Level3Sounds.SPARK, SoundSource.BLOCKS, 0.3F, 1.6F, false);
			}
		}
	}

	/** An assembly-line belt: it carries whatever stands on it along. */
	static final class Conveyor extends Level3FacingBlock {
		Conveyor(Properties properties) {
			super(properties, Level3Shapes.facing(0, 0, 0, 16, 10, 16), Effect.NONE);
		}

		@Override
		public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
			Direction way = state.getValue(FACING);
			if (!entity.isShiftKeyDown()) entity.setDeltaMovement(entity.getDeltaMovement().add(way.getStepX() * 0.05, 0, way.getStepZ() * 0.05));
		}
	}
}
