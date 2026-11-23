package dev.noclip.backrooms.level0;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

/**
 * Handheld flashlight that toggles on/off with right click and projects dynamic light along the player's gaze.
 */
final class FlashlightItem extends Item {
	static final int MAX_BATTERY = 600;
	private static final String TAG_LIT = "lit";

	FlashlightItem(Properties properties) {
		super(properties);
	}

	static boolean isLit(ItemStack stack) {
		if (!stack.is(ModItems.FLASHLIGHT) || !hasBattery(stack)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(TAG_LIT, false);
	}

	static boolean hasBattery(ItemStack stack) {
		return stack.getDamageValue() < stack.getMaxDamage() - 1;
	}

	static void setLit(ItemStack stack, boolean lit) {
		boolean active = lit && hasBattery(stack);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TAG_LIT, active));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(active), List.of(), List.of()));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!hasBattery(stack)) {
			if (!level.isClientSide()) {
				setLit(stack, false);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.45F, 0.65F);
			}
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide()) {
			boolean next = !isLit(stack);
			setLit(stack, next);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					next ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
					SoundSource.PLAYERS, 0.45F, next ? 1.35F : 0.95F);
		}
		return InteractionResult.SUCCESS;
	}
}
