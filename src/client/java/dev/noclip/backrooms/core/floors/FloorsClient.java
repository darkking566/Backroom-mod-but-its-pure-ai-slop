package dev.noclip.backrooms.core.floors;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.trip.AnomalyClient;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * What the player sees of the floor count & dual-axis difficulty (docs/企劃書/1-遊戲玩法/README.md):
 * "Floor 3" and active Band / Distance / Threat multiplier at the top left while in the Backrooms,
 * plus "Level 0 · The Lobby" above the inventory.
 */
public final class FloorsClient {
	private static final float SCALE = 1.5F;
	private static final int TEXT = 0xE6F2F0E6;
	private static final String[] BAND_ROMAN = {"I", "II", "III", "IV"};
	private static final int[] BAND_COLORS = {0xFFB8C4CE, 0xFFF2D16B, 0xFFF28E42, 0xFFFF5A5A};

	private static DifficultyPayload difficulty = DifficultyPayload.NONE;

	private FloorsClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DifficultyPayload.TYPE, (payload, context) -> difficulty = payload);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> difficulty = DifficultyPayload.NONE);
		HudElementRegistry.addLast(BackroomsMod.id("floor"), FloorsClient::hud);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> header(s, graphics));
			}
		});
	}

	/** The player's floor this trip, from the synced attachment; 0 when not in the Backrooms. */
	public static int floor() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || Floors.key(client.level) == null) return 0;
		return client.player.getAttachedOrElse(Floors.RUN, FloorRun.NONE).floor();
	}

	public static DifficultyPayload difficulty() {
		return difficulty;
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		int floor = floor();
		if (floor <= 0 || client.gui.hud.isHidden()) return;
		graphics.pose().pushMatrix();
		graphics.pose().scale(SCALE, SCALE);
		graphics.text(client.font, Component.translatable("hud.noclip_backrooms.floor", floor), 10, 8, TEXT, true);
		graphics.pose().popMatrix();

		int nextY = AnomalyClient.affix().isEmpty() ? 28 : 40;
		if (difficulty.active()) {
			int bandIdx = Math.clamp(difficulty.band(), 0, BAND_ROMAN.length - 1);
			Component label = Component.translatable("hud.noclip_backrooms.difficulty",
					BAND_ROMAN[bandIdx],
					difficulty.distance(),
					String.format(Locale.ROOT, "%.1f", difficulty.threatMult()));
			if (difficulty.farmPenalty() > 0) {
				label = label.copy().append(Component.translatable("hud.noclip_backrooms.difficulty.anti_farm", difficulty.farmPenalty()));
			}
			int color = difficulty.farmPenalty() > 0 ? 0xFFFF5555 : BAND_COLORS[bandIdx];
			graphics.text(client.font, label, 15, nextY, color, true);
			nextY += 11;
		}
		dev.noclip.backrooms.core.rpg.Badges.Badge badge = dev.noclip.backrooms.core.rpg.Badges.active(client.player);
		if (badge != null) {
			graphics.text(client.font, Component.translatable("hud.noclip_backrooms.badge", badge.title()),
					15, nextY, 0xFFE8C868, true);
		}
	}

	private static void header(Screen screen, GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;
		String level = Floors.key(client.level);
		if (level == null) return;
		Component text = Component.literal("Level " + level);
		Component name = Floors.name(level);
		if (!name.getString().isEmpty()) text = Component.translatable("hud.noclip_backrooms.level_in_inventory", level, name);
		int imageHeight = screen instanceof CreativeModeInventoryScreen ? 136 : 166;
		graphics.centeredText(client.font, text, screen.width / 2, (screen.height - imageHeight) / 2 - 13, 0xFFFFFFFF);
	}
}
