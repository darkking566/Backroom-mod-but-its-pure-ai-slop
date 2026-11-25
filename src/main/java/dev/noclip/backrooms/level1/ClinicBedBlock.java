package dev.noclip.backrooms.level1;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Hippocrates-1 treats the sick and wounded for free: right-click to be mended, once every few minutes. */
final class ClinicBedBlock extends OldComputerBlock {
	private static final int WAIT = 3 * 60 * 20;
	private static final Map<String, Integer> LAST = new HashMap<>();

	ClinicBedBlock(Properties properties, VoxelShape shape) {
		super(properties, shape, 1);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		int now = server.getServer().getTickCount();
		Integer last = LAST.get(player.getUUID().toString());
		if (last != null && now - last < WAIT) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.clinic_wait"));
			return InteractionResult.SUCCESS;
		}
		LAST.put(player.getUUID().toString(), now);
		player.heal(player.getMaxHealth());
		player.getFoodData().eat(6, 0.6F);
		player.removeAllEffects();
		server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.0F);
		serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.clinic_healed"));
		return InteractionResult.SUCCESS;
	}
}
