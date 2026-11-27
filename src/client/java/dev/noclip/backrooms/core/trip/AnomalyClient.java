package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.BackroomsMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * This trip's anomaly on screen (core.trip.Anomalies): its name under the floor count, and for the red mist a red haze
 * over everything, thicker at the edges.
 */
public final class AnomalyClient {
	private static String affix = "";
	private static boolean inZone = false;

	private AnomalyClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AnomalyPayload.TYPE, (payload, context) -> {
			affix = payload.affix();
			inZone = payload.inZone();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			affix = "";
			inZone = false;
		});
		HudElementRegistry.addLast(BackroomsMod.id("anomaly"), AnomalyClient::hud);
	}

	public static String affix() {
		return affix;
	}

	public static boolean inZone() {
		return inZone;
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (affix.isEmpty() || client.player == null || client.gui.hud.isHidden()) return;
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		if (affix.equals("red_mist") && inZone) {
			graphics.fill(0, 0, w, h, 0x22A01010);
			for (int i = 0; i < 12; i++) {
				int alpha = (12 - i) * 6;
				int edge = i * 4;
				graphics.fill(0, edge, w, edge + 4, alpha << 24 | 0x5A0000);
				graphics.fill(0, h - edge - 4, w, h - edge, alpha << 24 | 0x5A0000);
			}
		}
		Component label = Component.translatable("hud.noclip_backrooms.anomaly",
				Component.translatable("anomaly.noclip_backrooms." + affix));
		if (affix.equals("red_mist") && inZone) {
			label = label.copy().append(Component.translatable("hud.noclip_backrooms.anomaly.in_zone"));
		}
		graphics.text(client.font, label, 15, 28, (affix.equals("red_mist") && inZone) ? 0xFFFF6868 : 0xFFC890E8, true);
	}
}
