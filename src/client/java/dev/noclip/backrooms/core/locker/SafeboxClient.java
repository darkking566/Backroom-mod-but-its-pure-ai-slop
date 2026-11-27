package dev.noclip.backrooms.core.locker;

import dev.noclip.backrooms.mixin.client.AbstractContainerScreenAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Adds a native-styled Safebox quick-open button below the player's inventory & wallet line. */
public final class SafeboxClient {
	private static final int BTN_W = 92;
	private static final int BTN_H = 16;

	private SafeboxClient() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen inv)) return;
			SafeboxButton btn = new SafeboxButton(inv);
			btn.syncPosition();
			Screens.getWidgets(inv).add(btn);
		});
	}

	private static final class SafeboxButton extends AbstractWidget {
		private final InventoryScreen inv;

		SafeboxButton(InventoryScreen inv) {
			super(0, 0, BTN_W, BTN_H, Component.empty());
			this.inv = inv;
		}

		void syncPosition() {
			AbstractContainerScreenAccess access = (AbstractContainerScreenAccess) inv;
			int bx = inv.width / 2 - BTN_W / 2;
			int by = Math.min(inv.height - BTN_H - 2, access.noclip_backrooms$topPos() + 166 + 16);
			setPosition(bx, by);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			ClientPlayNetworking.send(SafeboxOpenPayload.INSTANCE);
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			syncPosition();
			Minecraft client = Minecraft.getInstance();
			int cap = client.player != null ? Safebox.capacity(client.player) : Safebox.INITIAL_SLOTS;
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			g.fill(x, y, x + w, y + h, 0xFF000000);
			if (isHovered()) {
				g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFE6D48A);
				g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFF5C2);
				g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFF5C2);
				g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF947D32);
				g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF947D32);
			} else {
				g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
				g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
				g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
				g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
				g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF555555);
			}
			Font font = client.font;
			Component label = Component.translatable("ui.noclip_backrooms.safebox_btn", cap, Safebox.MAX_SLOTS);
			int color = isHovered() ? 0xFF3A2C00 : 0xFF282828;
			g.centeredText(font, label, x + w / 2, y + (h - 7) / 2, color);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
