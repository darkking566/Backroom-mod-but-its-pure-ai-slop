package dev.noclip.backrooms.core.quota;

import dev.noclip.backrooms.BackroomsMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The team's M.E.G. cycle at the top left while on the surface (core.quota.Quota): the mission day, the quota, the
 * required items, the debt. In the Backrooms there is no time, so nothing is shown there.
 */
public final class QuotaClient {
	private static final int DAY = 0xFFFFE066, TEXT = 0xFFE8E2D0, DONE = 0xFF9FD68A, DEBT = 0xFFE06050;
	private static QuotaStatusPayload status = QuotaStatusPayload.NONE;

	private QuotaClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(QuotaStatusPayload.TYPE, (payload, context) -> status = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> status = QuotaStatusPayload.NONE);
		HudElementRegistry.addLast(BackroomsMod.id("quota"), QuotaClient::hud);
	}

	/** The last status the server sent (for tests). */
	public static QuotaStatusPayload status() {
		return status;
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (!status.active() || client.player == null || client.player.level().dimension() != Level.OVERWORLD) return;
		int x = 6, y = 6;
		graphics.text(client.font, Component.translatable("hud.noclip_backrooms.quota.day", status.day(), QuotaMath.CYCLE_DAYS,
				status.daysLeft()), x, y, DAY, true);
		y += 11;
		graphics.text(client.font, Component.translatable("hud.noclip_backrooms.quota.points", status.delivered(), status.required()), x, y,
				status.delivered() >= status.required() ? DONE : TEXT, true);
		for (QuotaStatusPayload.Need need : status.items()) {
			y += 10;
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(need.item())));
			graphics.text(client.font, Component.translatable("hud.noclip_backrooms.quota.need", stack.getHoverName(), need.handed(), need.needed()),
					x, y, need.handed() >= need.needed() ? DONE : TEXT, true);
		}
		if (status.debt() > 0) {
			y += 11;
			graphics.text(client.font, Component.translatable("hud.noclip_backrooms.quota.debt", status.debt()), x, y, DEBT, true);
		}
	}
}
