package dev.noclip.backrooms.level2;

import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An anomalous glitching concrete wall segment in Level 2: looks solid from down the hall,
 * has no collision, and attempts to noclip the player toward Level 2.1 when stepped into or touched.
 */
final class Level2NoclipWallBlock extends Block {
	static final String TARGET_LEVEL = "2.1";

	Level2NoclipWallBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (entity instanceof ServerPlayer player) {
			LevelExits.use(player, TARGET_LEVEL);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			LevelExits.use(serverPlayer, TARGET_LEVEL);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.REVERSE_PORTAL,
					pos.getX() + 0.1 + random.nextDouble() * 0.8,
					pos.getY() + 0.1 + random.nextDouble() * 0.8,
					pos.getZ() + 0.1 + random.nextDouble() * 0.8,
					0, 0.01, 0);
		}
	}
}
