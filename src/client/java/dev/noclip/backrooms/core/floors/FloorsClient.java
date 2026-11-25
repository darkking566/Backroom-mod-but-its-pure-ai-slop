package dev.noclip.backrooms.core.floors;

import dev.noclip.backrooms.BackroomsMod;
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
 * What the player sees of the floor count (docs/FLOORS.md): "Floor 3" at the top left while in the Backrooms, and
 * "Level 0 · The Lobby" above the inventory. The title on arrival comes from the server (Floors.announce).
 */
public final class FloorsClient {
	private static final float SCALE = 1.5F;
	private static final int TEXT = 0xE6F2F0E6;

	private FloorsClient() {
	}

	public static void register() {
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

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		int floor = floor();
		if (floor <= 0 || client.gui.hud.isHidden()) return;
		graphics.pose().pushMatrix();
		graphics.pose().scale(SCALE, SCALE);
		graphics.text(client.font, Component.translatable("hud.noclip_backrooms.floor", floor), 10, 8, TEXT, true);
		graphics.pose().popMatrix();
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
