package dev.noclip.backrooms.level2;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/** The steel doors in the tunnel walls: they swing in towards the room behind. Many are locked. */
final class Level2DoorBlock extends DoorBlock {
	private final boolean locked;

	Level2DoorBlock(BlockSetType type, Properties properties, boolean locked) {
		super(type, properties);
		this.locked = locked;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!locked) return super.useWithoutItem(state, level, pos, player, hit);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level2.locked"));
			level.playSound(null, pos, Level2Sounds.LOCKED, SoundSource.BLOCKS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		}
		return InteractionResult.SUCCESS;
	}
}
