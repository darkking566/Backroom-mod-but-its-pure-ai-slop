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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A settlement's supply locker. Each player can take a share (its loot table) from each locker,
 * then must wait a while before it has been restocked.
 */
final class LockerBlock extends ShapedBlock {
	static final int RESTOCK = 5 * 60 * 20;
	private static final Map<String, Integer> TAKEN = new HashMap<>();

	LockerBlock(Properties properties, VoxelShape shape) {
		super(properties, shape);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		String key = player.getUUID() + "@" + pos.asLong();
		int now = server.getServer().getTickCount();
		Integer last = TAKEN.get(key);
		if (last != null && now - last < RESTOCK) {
			// Alloy buys another share straight away.
			ItemStack held = player.getMainHandItem();
			if (!held.is(Level1Items.ALLOY)) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.locker_empty"));
				return InteractionResult.SUCCESS;
			}
			if (!player.getAbilities().instabuild) held.shrink(1);
			server.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
			Block.dropResources(state, server, pos, null, player, ItemStack.EMPTY);
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.locker_traded"));
			return InteractionResult.SUCCESS;
		}
		TAKEN.put(key, now);
		server.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.7F, 1.2F);
		Block.dropResources(state, server, pos, null, player, ItemStack.EMPTY);
		serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.locker_taken"));
		return InteractionResult.SUCCESS;
	}
}
