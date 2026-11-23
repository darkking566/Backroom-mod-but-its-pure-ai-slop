package dev.noclip.backrooms.level1;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Prying a crate open spills what is inside (its loot table) and leaves nothing behind. Bare-handed there is a
 * chance of cutting yourself on something in it (broken glass, sharp metal); an axe or a sword does the job safely.
 */
final class SupplyCrateBlock extends Block {
	private static final int CUT_PERCENT = 20;

	SupplyCrateBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			ItemStack held = player.getMainHandItem();
			if (!(held.is(ItemTags.AXES) || held.is(ItemTags.SWORDS) || held.is(Level1Items.CROWBAR)) &&server.getRandom().nextInt(100) < CUT_PERCENT) {
				player.hurtServer(server, server.damageSources().generic(), 2.0F);
				if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.crate_cut"));
			}
			server.playSound(null, pos, Level1Sounds.CRATE_OPEN, SoundSource.BLOCKS, 0.9F, 0.9F + server.getRandom().nextFloat() * 0.2F);
			server.destroyBlock(pos, true, player, 512);
		}
		return InteractionResult.SUCCESS;
	}
}
