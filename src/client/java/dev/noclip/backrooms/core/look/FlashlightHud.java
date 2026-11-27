package dev.noclip.backrooms.core.look;

import dev.noclip.backrooms.level0.FlashlightItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a crisp 5-segment battery bar HUD (no vanilla durability bar) whenever the player holds either
 * the standard flashlight or Level 3's infinite ambient-energy flashlight in main hand or off-hand.
 */
final class FlashlightHud {
	private static final int BARS = FlashlightItem.BARS; // 5 segments

	private FlashlightHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gui.hud.isHidden() || player.isSpectator()) return;

		ItemStack stack = findHeldFlashlight(player);
		if (stack.isEmpty()) return;

		boolean lit = FlashlightItem.isLit(stack);
		boolean infinite = FlashlightItem.isInfinite(stack);
		int filled = FlashlightItem.bars(stack);
		long ms = System.currentTimeMillis();
		boolean blink = (ms / 450L) % 2L == 0L;

		Font font = client.font;
		int sh = graphics.guiHeight();
		// Anchor bottom-left safely above CamcorderOverlay's "SP 0:00:00" when camcorder overlay is active.
		int x = 10;
		int y = Look.active(client) ? sh - 46 : sh - 26;
		int panelW = infinite ? 66 : 64;
		int panelH = 16;

		// Subtle translucent pill background + crisp border
		int borderColor = infinite
				? (lit ? 0xDD6CE8F5 : 0xAA3B7C88)
				: (filled <= 1 && blink ? 0xDDE04848 : (lit ? 0xDD9CA48A : 0xAA4D525B));
		graphics.fill(x, y, x + panelW, y + panelH, 0xB80E1014);
		graphics.fill(x, y, x + panelW, y + 1, borderColor);
		graphics.fill(x, y + panelH - 1, x + panelW, y + panelH, borderColor);
		graphics.fill(x, y, x + 1, y + panelH, borderColor);
		graphics.fill(x + panelW - 1, y, x + panelW, y + panelH, borderColor);

		// Battery icon shell: 31x10 frame + 2x4 positive terminal nub
		int bx = x + 4;
		int by = y + 3;
		int bw = 31;
		int bh = 10;
		int shellColor = infinite ? 0xFFE0FAFF : (filled == 0 && blink ? 0xFFFF5555 : 0xFFE6E8EC);
		graphics.fill(bx, by, bx + bw, by + 1, shellColor);
		graphics.fill(bx, by + bh - 1, bx + bw, by + bh, shellColor);
		graphics.fill(bx, by, bx + 1, by + bh, shellColor);
		graphics.fill(bx + bw - 1, by, bx + bw, by + bh, shellColor);
		graphics.fill(bx + bw, by + 3, bx + bw + 2, by + bh - 3, shellColor);

		// 5 discrete segmented battery bars (each 4px wide with 2px pitch gap)
		int segColor;
		if (infinite) {
			// Gentle cyan-gold energy pulse for Level 3 infinite flashlight
			int pulse = (int) (Math.sin(ms * 0.005) * 18.0);
			int g = Math.max(0, Math.min(255, 228 + pulse));
			segColor = (0xFF << 24) | (0x72 << 16) | (g << 8) | 0xFF;
		} else if (filled >= 3) {
			segColor = lit ? 0xFF9DF08A : 0xFFDCE6D5;
		} else if (filled == 2) {
			segColor = 0xFFFFC947;
		} else {
			segColor = blink ? 0xFFFF4646 : 0xFFB82828;
		}

		for (int i = 0; i < BARS; i++) {
			int sx = bx + 2 + i * 5 + (i > 0 ? i : 0); // 4px bar + 2px spacing -> 2 + 4*6 + 4 = 30 <= bw-1
			int sy = by + 2;
			if (i < filled) {
				graphics.fill(sx, sy, sx + 4, sy + 6, segColor);
			} else {
				graphics.fill(sx, sy, sx + 4, sy + 6, 0x3AFFFFFF);
			}
		}

		// Right-side status label: "∞ ON" / "ON" / "OFF"
		int tx = bx + bw + 5;
		int ty = y + 4;
		if (infinite) {
			String label = lit ? "∞ ON" : "∞";
			graphics.text(font, label, tx, ty, lit ? 0xFF8CF6FF : 0xFF8CAAB0, true);
		} else {
			String label = filled == 0 ? "0%" : (lit ? "ON" : "OFF");
			int textColor = filled == 0 ? (blink ? 0xFFFF5555 : 0xFFAA4444) : (lit ? 0xFFFFE27A : 0xFF9AA0AA);
			graphics.text(font, label, tx, ty, textColor, true);
		}
	}

	private static ItemStack findHeldFlashlight(LocalPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (FlashlightItem.isFlashlight(main)) return main;
		ItemStack off = player.getOffhandItem();
		if (FlashlightItem.isFlashlight(off)) return off;
		return ItemStack.EMPTY;
	}
}
