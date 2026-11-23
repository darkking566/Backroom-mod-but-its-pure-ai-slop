package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.levels.LevelExits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Solid-looking blackness at the end of a stairwell; walking on into it takes you to the next level. */
final class DarknessBlock extends Block {
	private final String level;

	DarknessBlock(Properties properties, String level) {
		super(properties);
		this.level = level;
	}

	@Override
	protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (entity instanceof ServerPlayer player) LevelExits.use(player, level);
	}
}
