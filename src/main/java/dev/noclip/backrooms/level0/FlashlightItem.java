package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.BackroomsMod;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

/**
 * Handheld Backrooms flashlight with custom 5-bar battery tracking (no vanilla durability bar),
 * supporting both the starter limited-battery model and Level 3's infinite ambient-energy model.
 * <p>
 * Battery loading supports 3 ergonomic methods:
 * <ol>
 *   <li>Shift + Right-Click while holding the flashlight to quick-load a battery from inventory;</li>
 *   <li>Right-clicking a battery onto the flashlight inside the inventory (bundle-style);</li>
 *   <li>Shapeless 2x2 crafting with {@code battery_cell} (or using {@code flashlight_battery_pack}).</li>
 * </ol>
 */
public final class FlashlightItem extends Item {
	/** 20 minutes of continuous illumination, evenly divided into 5 HUD segments (240s / 4 min per bar). */
	public static final int MAX_BATTERY = 1200;
	public static final int BARS = 5;
	public static final int SECONDS_PER_BAR = MAX_BATTERY / BARS;
	/** One battery_cell restores 2 bars (40% = 480s). */
	public static final int CELL_RECHARGE = SECONDS_PER_BAR * 2;
	public static final String STARTER_TAG = "noclip_backrooms_starter_flashlight";

	private static final String TAG_LIT = "lit";
	private static final String TAG_BATTERY = "battery";

	private final boolean infinite;

	FlashlightItem(Properties properties, boolean infinite) {
		super(properties);
		this.infinite = infinite;
	}

	public boolean isInfinite() {
		return infinite;
	}

	public static boolean isFlashlight(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof FlashlightItem;
	}

	public static boolean isInfinite(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof FlashlightItem fl && fl.infinite;
	}

	public static int battery(ItemStack stack) {
		if (!isFlashlight(stack)) return 0;
		if (isInfinite(stack)) return MAX_BATTERY;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			CompoundTag tag = data.copyTag();
			if (tag.contains(TAG_BATTERY)) {
				return Math.max(0, Math.min(MAX_BATTERY, tag.getIntOr(TAG_BATTERY, MAX_BATTERY)));
			}
		}
		// Graceful migration for legacy pre-overhaul stacks that still carry vanilla damage
		if (stack.isDamaged() && stack.getMaxDamage() > 0) {
			float ratio = 1.0F - (float) stack.getDamageValue() / stack.getMaxDamage();
			return Math.max(0, Math.min(MAX_BATTERY, Math.round(ratio * MAX_BATTERY)));
		}
		return MAX_BATTERY;
	}

	public static float batteryRatio(ItemStack stack) {
		if (!isFlashlight(stack)) return 0.0F;
		if (isInfinite(stack)) return 1.0F;
		return (float) battery(stack) / MAX_BATTERY;
	}

	/** Discrete 0..5 segmented bars for HUD and tooltips. */
	public static int bars(ItemStack stack) {
		if (!isFlashlight(stack)) return 0;
		if (isInfinite(stack)) return BARS;
		int b = battery(stack);
		if (b <= 0) return 0;
		return Math.min(BARS, (b + SECONDS_PER_BAR - 1) / SECONDS_PER_BAR);
	}

	public static boolean hasBattery(ItemStack stack) {
		return isInfinite(stack) || battery(stack) > 0;
	}

	public static boolean needsCharge(ItemStack stack) {
		return isFlashlight(stack) && !isInfinite(stack) && battery(stack) < MAX_BATTERY;
	}

	public static boolean isLit(ItemStack stack) {
		if (!isFlashlight(stack) || !hasBattery(stack)) return false;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(TAG_LIT, false);
	}

	public static void setBattery(ItemStack stack, int value) {
		if (!isFlashlight(stack) || isInfinite(stack)) return;
		int clamped = Math.max(0, Math.min(MAX_BATTERY, value));
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_BATTERY, clamped));
		if (stack.getDamageValue() != 0) stack.setDamageValue(0);
		if (clamped <= 0 && isLit(stack)) {
			setLit(stack, false);
		}
	}

	public static boolean recharge(ItemStack stack, int amount) {
		if (!needsCharge(stack) || amount <= 0) return false;
		setBattery(stack, battery(stack) + amount);
		return true;
	}

	/** Drains battery seconds; returns true if the flashlight just ran completely empty. */
	public static boolean drain(ItemStack stack, int amount) {
		if (!isFlashlight(stack) || isInfinite(stack) || amount <= 0) return false;
		int before = battery(stack);
		int next = Math.max(0, before - amount);
		setBattery(stack, next);
		return before > 0 && next == 0;
	}

	public static void setLit(ItemStack stack, boolean lit) {
		if (!isFlashlight(stack)) return;
		boolean active = lit && hasBattery(stack);
		int currentBattery = battery(stack);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putBoolean(TAG_LIT, active);
			if (!isInfinite(stack) && !tag.contains(TAG_BATTERY)) {
				tag.putInt(TAG_BATTERY, currentBattery);
			}
		});
		if (stack.getDamageValue() != 0) stack.setDamageValue(0);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(active), List.of(), List.of()));
	}

	public static String barGlyphs(ItemStack stack) {
		if (isInfinite(stack)) return "∞";
		int filled = bars(stack);
		StringBuilder sb = new StringBuilder(BARS);
		for (int i = 0; i < BARS; i++) {
			sb.append(i < filled ? '■' : '□');
		}
		return sb.toString();
	}

	private static boolean isBatteryCell(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(BackroomsMod.id("battery_cell"));
	}

	private static boolean isBatteryPack(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(BackroomsMod.id("flashlight_battery_pack"));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		// Method A: Shift + Right-Click while holding standard flashlight quick-loads a battery from inventory.
		if (!infinite && player.isShiftKeyDown()) {
			if (!level.isClientSide()) {
				quickReloadFromInventory(level, player, stack);
			}
			return InteractionResult.SUCCESS;
		}

		if (!hasBattery(stack)) {
			if (!level.isClientSide()) {
				setLit(stack, false);
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.45F, 0.65F);
				if (player instanceof ServerPlayer sp) {
					sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.flashlight.empty")
							.withStyle(ChatFormatting.RED));
				}
			}
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide()) {
			boolean next = !isLit(stack);
			setLit(stack, next);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					next ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
					SoundSource.PLAYERS, 0.45F, infinite ? (next ? 1.55F : 1.15F) : (next ? 1.35F : 0.95F));
		}
		return InteractionResult.SUCCESS;
	}

	private static void quickReloadFromInventory(Level level, Player player, ItemStack flashlight) {
		if (!needsCharge(flashlight)) {
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.flashlight.full",
						barGlyphs(flashlight)).withStyle(ChatFormatting.GREEN));
			}
			return;
		}
		int cellSlot = -1;
		int packSlot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack candidate = player.getInventory().getItem(i);
			if (cellSlot < 0 && isBatteryCell(candidate)) cellSlot = i;
			if (packSlot < 0 && isBatteryPack(candidate)) packSlot = i;
		}
		int chosenSlot = cellSlot >= 0 ? cellSlot : packSlot;
		if (chosenSlot < 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.45F, 0.6F);
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.flashlight.no_battery")
						.withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		ItemStack batteryStack = player.getInventory().getItem(chosenSlot);
		int amount = isBatteryPack(batteryStack) ? MAX_BATTERY : CELL_RECHARGE;
		recharge(flashlight, amount);
		if (!player.isCreative()) {
			batteryStack.shrink(1);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.45F, 1.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
		if (player instanceof ServerPlayer sp) {
			int pct = Math.round(batteryRatio(flashlight) * 100.0F);
			sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.flashlight.reloaded",
					barGlyphs(flashlight), pct).withStyle(ChatFormatting.AQUA));
		}
	}

	/** Method B: Right-clicking a battery onto the flashlight inside the inventory loads it directly. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action,
			Player player, SlotAccess carriedAccess) {
		if (action != ClickAction.SECONDARY || infinite || !needsCharge(self)) return false;
		if (!isBatteryCell(other) && !isBatteryPack(other)) return false;
		int amount = isBatteryPack(other) ? MAX_BATTERY : CELL_RECHARGE;
		if (recharge(self, amount)) {
			if (!player.isCreative()) {
				other.shrink(1);
			}
			player.playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.45F, 1.7F);
			player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35F, 1.4F);
			return true;
		}
		return false;
	}
}
