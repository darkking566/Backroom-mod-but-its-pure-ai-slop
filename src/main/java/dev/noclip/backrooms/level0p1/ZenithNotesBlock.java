package dev.noclip.backrooms.level0p1;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Rhea Valpri's research notes: right-click to read a page (which one depends on where they lie). */
final class ZenithNotesBlock extends ZenithShapedBlock {
	static final int PAGES = 4;

	ZenithNotesBlock(Properties properties, VoxelShape shape) {
		super(properties, shape, false);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			int page = Math.floorMod(pos.getX() * 31 + pos.getZ() * 17 + pos.getY(), PAGES);
			serverPlayer.sendSystemMessage(Component.translatable("message.noclip_backrooms.level0p1.rhea." + page));
		}
		return InteractionResult.SUCCESS;
	}
}
