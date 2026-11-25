package dev.noclip.backrooms.weapons;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Signature weapon growth & visual evolution system (Tier 1 -> Tier 2 -> Tier 3).
 * Persists monster kill count and evolution stage on each Backrooms weapon ItemStack via
 * DataComponents.CUSTOM_DATA so weapons physically transform (e.g., Tier 1/2/3 Blender staffs
 * and animated evolution halos) and scale combat power as the player fights through the Backrooms.
 */
public final class WeaponEvolution {
	public static final String KEY_KILLS = "noclip_kills";
	public static final String KEY_TIER = "noclip_tier";
	public static final int TIER_2_KILLS = 10;
	public static final int TIER_3_KILLS = 30;

	private WeaponEvolution() {
	}

	public static boolean isEvolvable(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		return stack.getItem() instanceof BackroomsFirearmItem
				|| stack.getItem() instanceof BackroomsMagicItem
				|| stack.getItem() instanceof BackroomsMeleeItem;
	}

	public static int kills(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) return 0;
		return Math.max(0, data.copyTag().getIntOr(KEY_KILLS, 0));
	}

	public static int tier(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 1;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) return 1;
		CompoundTag tag = data.copyTag();
		int storedTier = tag.getIntOr(KEY_TIER, 1);
		int derivedTier = tierForKills(tag.getIntOr(KEY_KILLS, 0));
		return Math.max(1, Math.min(3, Math.max(storedTier, derivedTier)));
	}

	public static int tierForKills(int kills) {
		if (kills >= TIER_3_KILLS) return 3;
		if (kills >= TIER_2_KILLS) return 2;
		return 1;
	}

	public static float damageMultiplier(ItemStack stack) {
		return damageMultiplierForTier(tier(stack));
	}

	public static float damageMultiplierForTier(int tier) {
		return switch (tier) {
			case 2 -> 1.18F;
			case 3 -> 1.40F;
			default -> 1.00F;
		};
	}

	public static float sanityCostMultiplier(ItemStack stack) {
		return sanityMultiplierForTier(tier(stack));
	}

	public static float sanityMultiplierForTier(int tier) {
		return switch (tier) {
			case 2 -> 0.85F;
			case 3 -> 0.70F;
			default -> 1.00F;
		};
	}

	/** Records a monster kill with this weapon and triggers evolution fanfare on Tier 2 / Tier 3 promotion. */
	public static int recordKill(ServerPlayer player, ItemStack stack) {
		if (!isEvolvable(stack)) return 1;
		int oldTier = tier(stack);
		int newKills = kills(stack) + 1;
		int newTier = Math.max(oldTier, tierForKills(newKills));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putInt(KEY_KILLS, newKills);
			tag.putInt(KEY_TIER, newTier);
		});
		if (newTier > oldTier && player != null) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.85F, newTier == 3 ? 1.15F : 1.0F);
			player.sendOverlayMessage(Component.translatable(
					newTier == 3 ? "message.noclip_backrooms.weapon.evolved_t3" : "message.noclip_backrooms.weapon.evolved_t2",
					stack.getHoverName()
			).withStyle(newTier == 3 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
		}
		return newTier;
	}

	/** Directly sets a weapon's evolution tier (1..3) for testing or special upgrades. */
	public static void setTier(ItemStack stack, int targetTier) {
		if (!isEvolvable(stack)) return;
		int clamped = Math.max(1, Math.min(3, targetTier));
		int minKills = clamped == 3 ? TIER_3_KILLS : (clamped == 2 ? TIER_2_KILLS : 0);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putInt(KEY_TIER, clamped);
			if (clamped == 1) {
				tag.putInt(KEY_KILLS, 0);
			} else if (tag.getIntOr(KEY_KILLS, 0) < minKills) {
				tag.putInt(KEY_KILLS, minKills);
			}
		});
	}

	public static void appendTooltip(ItemStack stack, Consumer<Component> adder) {
		int t = tier(stack);
		int k = kills(stack);
		if (t >= 3) {
			adder.accept(Component.translatable("tooltip.noclip_backrooms.weapon_evo_t3", k)
					.withStyle(ChatFormatting.GOLD));
		} else if (t == 2) {
			adder.accept(Component.translatable("tooltip.noclip_backrooms.weapon_evo_t2", k, TIER_3_KILLS)
					.withStyle(ChatFormatting.AQUA));
		} else {
			adder.accept(Component.translatable("tooltip.noclip_backrooms.weapon_evo_t1", k, TIER_2_KILLS)
					.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}
}
