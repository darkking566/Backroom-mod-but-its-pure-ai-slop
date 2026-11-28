package dev.noclip.backrooms.core.trip;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.field.FieldKit;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0.Level0Space;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * What death leaves behind: M.E.G.'s mapping beacon stays with the investigator (the assignment cannot be done without
 * it, and it is only handed out once), and for what did fall in the Backrooms, {@code /meg relics} tells the way back.
 */
public final class Relics {
	/** Where this trip's things last fell. */
	public static final AttachmentType<GlobalPos> AT = AttachmentRegistry.create(BackroomsMod.id("trip_relics"),
			builder -> builder.persistent(GlobalPos.CODEC).copyOnDeath());

	private Relics() {
	}

	static void register() {
		// The beacons stayed in the dead body's inventory (mixin.InventoryKeepsBeacon): hand them to the new one.
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> {
			if (alive || ((ServerLevel) old.level()).getGameRules().get(GameRules.KEEP_INVENTORY)) return;
			Inventory from = old.getInventory(), to = player.getInventory();
			for (int i = 0; i < from.getContainerSize(); i++) {
				ItemStack stack = from.getItem(i);
				if (!stack.is(FieldKit.BEACON)) continue;
				if (to.getItem(i).isEmpty()) to.setItem(i, stack.copy());
				else to.placeItemBackInInventory(stack.copy());
				from.setItem(i, ItemStack.EMPTY);
			}
		});
	}

	/** Takes the beacons out before the inventory is dropped; {@link #putBack} returns them to the dead body. */
	public static List<ItemStack> setAside(Inventory inventory) {
		List<ItemStack> aside = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			boolean beacon = stack.is(FieldKit.BEACON);
			aside.add(beacon ? stack : ItemStack.EMPTY);
			if (beacon) inventory.setItem(i, ItemStack.EMPTY);
		}
		if (inventory.player instanceof ServerPlayer player && !inventory.isEmpty() && Home.inBackrooms(player.level().dimension())) {
			player.setAttached(AT, GlobalPos.of(player.level().dimension(), player.blockPosition()));
		}
		return aside;
	}

	public static void putBack(Inventory inventory, List<ItemStack> aside) {
		for (int i = 0; i < aside.size(); i++) {
			if (!aside.get(i).isEmpty()) inventory.setItem(i, aside.get(i));
		}
	}

	/** After waking up: offers the way back to what fell. */
	static void offer(ServerPlayer player) {
		if (!player.hasAttached(AT)) return;
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.relics_offer").withStyle(ChatFormatting.GRAY)
				.append(" ")
				.append(Component.translatable("message.noclip_backrooms.trip.relics_button").withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/meg relics")))));
		tell(player);
	}

	/** Which way, from where the player is looking, and how far. */
	public static boolean tell(ServerPlayer player) {
		GlobalPos at = player.getAttached(AT);
		if (at == null) {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.relics_none"));
			return false;
		}
		if (at.dimension() != player.level().dimension()) {
			ServerLevel there = player.level().getServer().getLevel(at.dimension());
			String level = there == null ? null : Floors.key(there);
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.relics_elsewhere",
					level == null ? "?" : level).withStyle(ChatFormatting.YELLOW));
			return true;
		}
		BlockPos pos = at.pos();
		double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
		if (at.dimension() == Level0.LEVEL_0) {
			dx = Level0Space.nearest(dx);
			dz = Level0Space.nearest(dz);
		}
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.trip.relics_way",
				Component.translatable("direction.noclip_backrooms.relative." + octant(dx, dz, player.getYRot())),
				(int) Math.round(Math.sqrt(dx * dx + dz * dz))).withStyle(ChatFormatting.YELLOW));
		return true;
	}

	/** 0 ahead, then clockwise in eighths (2 is to the right, 4 behind, 6 to the left). */
	public static int octant(double dx, double dz, float yaw) {
		double target = Math.toDegrees(Math.atan2(-dx, dz));
		return Math.floorMod((int) Math.round(Mth.wrapDegrees(target - yaw) / 45.0), 8);
	}
}
