package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Prying a crate open spills what is inside (its loot table) and leaves nothing behind. */
final class SupplyCrateBlock extends Block {
	SupplyCrateBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			server.playSound(null, pos, Level1Sounds.CRATE_OPEN, SoundSource.BLOCKS, 0.9F, 0.9F + server.getRandom().nextFloat() * 0.2F);
			server.destroyBlock(pos, true, player, 512);
		}
		return InteractionResult.SUCCESS;
	}
}
