package dev.noclip.backrooms.core.rpg;

import dev.noclip.backrooms.BackroomsMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/** Registers all client-side RPG HUD, inventory stat-allocation panel, and sanity hallucination hooks. */
public final class RpgClient {
	private RpgClient() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, BackroomsMod.id("sanity_bar"), SanityBarHud::extract);
		InventoryStatsPanel.register();
		ClientTickEvents.END_CLIENT_TICK.register(SanityHallucinations::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SanityHallucinations.reset(client.level));
	}
}
