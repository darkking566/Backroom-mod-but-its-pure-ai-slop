package dev.noclip.backrooms.core.economy;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** The account's balance under the inventory, wherever the player is. */
public final class WalletClient {
	private WalletClient() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> balance(s, graphics));
			}
		});
	}

	private static void balance(Screen screen, GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		int imageHeight = screen instanceof CreativeModeInventoryScreen ? 136 : 166;
		Component text = Component.translatable("shop.noclip_backrooms.wallet", Wallet.of(client.player));
		graphics.centeredText(client.font, text, screen.width / 2, (screen.height + imageHeight) / 2 + 4, 0xFFFFE27A);
	}
}
