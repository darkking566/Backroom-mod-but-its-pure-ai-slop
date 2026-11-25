package dev.noclip.backrooms.core.rpg;

import dev.noclip.backrooms.mixin.client.AbstractContainerScreenAccess;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Attaches a native Minecraft-styled RPG Character Status & Level-Up Stat Allocation panel beside the vanilla
 * {@link InventoryScreen}. Matches vanilla container bevels (#C6C6C6 surface, #FFFFFF/#555555 edges, #8B8B8B slots,
 * #404040 text) and automatically flips to the right side whenever the vanilla Recipe Book opens on the left.
 */
public final class InventoryStatsPanel {
	private static final int PANEL_W = 132;
	private static final int PANEL_H = 166;
	private static final int ROW_START_Y = 74;
	private static final int ROW_STEP_Y = 22;

	// Native Minecraft Inventory GUI palette
	private static final int MC_BLACK = 0xFF000000;
	private static final int MC_HIGHLIGHT = 0xFFFFFFFF;
	private static final int MC_SURFACE = 0xFFC6C6C6;
	private static final int MC_SHADOW = 0xFF555555;
	private static final int MC_SLOT_DARK = 0xFF373737;
	private static final int MC_SLOT_FILL = 0xFF8B8B8B;
	private static final int MC_SLOT_HOVER = 0xFF9C9C9C;
	private static final int MC_TEXT = 0xFF404040;
	private static final int MC_TEXT_MUTED = 0xFF5A5A5A;

	private InventoryStatsPanel() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen inv)) return;

			Map<PlayerStats.StatKind, VanillaPlusButton> plusButtons = new EnumMap<>(PlayerStats.StatKind.class);
			StatsBackdropWidget backdrop = new StatsBackdropWidget(inv, plusButtons);
			Screens.getWidgets(inv).add(backdrop);

			for (PlayerStats.StatKind kind : PlayerStats.StatKind.values()) {
				VanillaPlusButton btn = new VanillaPlusButton(kind);
				plusButtons.put(kind, btn);
				Screens.getWidgets(inv).add(btn);
			}
			backdrop.syncButtonPositions();

			ScreenEvents.afterExtract(inv).register((s, graphics, mouseX, mouseY, delta) ->
					extractTooltips(client, inv, graphics, mouseX, mouseY));
		});
	}

	private static int panelX(InventoryScreen inv) {
		AbstractContainerScreenAccess access = (AbstractContainerScreenAccess) inv;
		int left = access.noclip_backrooms$leftPos();
		int width = access.noclip_backrooms$imageWidth();
		if (left >= PANEL_W + 6) {
			return left - PANEL_W - 4;
		}
		return Math.min(inv.width - PANEL_W - 4, left + width + 4);
	}

	private static int panelY(InventoryScreen inv) {
		return ((AbstractContainerScreenAccess) inv).noclip_backrooms$topPos();
	}

	private static void extractTooltips(Minecraft client, InventoryScreen inv, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (client.player == null) return;
		int px = panelX(inv);
		int py = panelY(inv);
		if (mouseX < px + 6 || mouseX > px + PANEL_W - 22) return;

		PlayerStats stats = PlayerRpg.get(client.player);
		PlayerStats.StatKind[] kinds = PlayerStats.StatKind.values();
		for (int i = 0; i < kinds.length; i++) {
			int ry = py + ROW_START_Y + i * ROW_STEP_Y;
			if (mouseY >= ry && mouseY < ry + ROW_STEP_Y - 2) {
				PlayerStats.StatKind kind = kinds[i];
				List<Component> lines = switch (kind) {
					case STRENGTH -> List.of(
							Component.translatable("ui.noclip_backrooms.rpg.str.title"),
							Component.translatable("ui.noclip_backrooms.rpg.str.desc",
									String.format(Locale.ROOT, "+%.1f", stats.attackDamageBonus()))
					);
					case AGILITY -> List.of(
							Component.translatable("ui.noclip_backrooms.rpg.agi.title"),
							Component.translatable("ui.noclip_backrooms.rpg.agi.desc",
									Math.round(stats.moveSpeedBonus() * 100.0), Math.round(stats.attackSpeedBonus() * 100.0))
					);
					case VITALITY -> List.of(
							Component.translatable("ui.noclip_backrooms.rpg.vit.title"),
							Component.translatable("ui.noclip_backrooms.rpg.vit.desc",
									String.format(Locale.ROOT, "+%.0f", stats.maxHealthBonus())),
							Component.translatable("ui.noclip_backrooms.rpg.vit.regen",
									String.format(Locale.ROOT, "+%.2f", stats.healthRegenPerSec()))
					);
					case SANITY -> List.of(
							Component.translatable("ui.noclip_backrooms.rpg.san.title"),
							Component.translatable("ui.noclip_backrooms.rpg.san.desc",
									Math.round(stats.maxSanity()),
									Math.round(stats.sanityDrainResistance() * 100.0F)),
							Component.translatable("ui.noclip_backrooms.rpg.san.regen",
									Math.round((stats.sanityRegenMultiplier() - 1.0F) * 100.0F),
									String.format(Locale.ROOT, "+%.2f", stats.passiveSanityRegenPerSec())),
							Component.translatable("ui.noclip_backrooms.rpg.san.hallucination_hint")
					);
				};
				graphics.setComponentTooltipForNextFrame(client.font, lines, mouseX, mouseY);
				return;
			}
		}
	}

	/** Draws a pixel-accurate native Minecraft container frame matching inventory.png. */
	private static void drawVanillaWindow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		// Base surface inside outer 1px outline
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, MC_SURFACE);

		// Rounded 1px black outer border (missing the 4 extreme corners, like vanilla inventory)
		g.fill(x + 2, y, x + w - 2, y + 1, MC_BLACK);
		g.fill(x + 2, y + h - 1, x + w - 2, y + h, MC_BLACK);
		g.fill(x, y + 2, x + 1, y + h - 2, MC_BLACK);
		g.fill(x + w - 1, y + 2, x + w, y + h - 2, MC_BLACK);
		g.fill(x + 1, y + 1, x + 2, y + 2, MC_BLACK);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, MC_BLACK);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, MC_BLACK);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, MC_BLACK);

		// Top & Left 2px bright white bevel highlight
		g.fill(x + 2, y + 1, x + w - 2, y + 3, MC_HIGHLIGHT);
		g.fill(x + 1, y + 2, x + 3, y + h - 2, MC_HIGHLIGHT);
		g.fill(x + 2, y + 2, x + 4, y + 4, MC_HIGHLIGHT);

		// Bottom & Right 2px dark shadow bevel
		g.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, MC_SHADOW);
		g.fill(x + w - 3, y + 2, x + w - 1, y + h - 2, MC_SHADOW);
		g.fill(x + w - 4, y + h - 4, x + w - 2, y + h - 2, MC_SHADOW);

		// Top-right & bottom-left neutral transition pixels
		g.fill(x + w - 3, y + 1, x + w - 2, y + 3, MC_SURFACE);
		g.fill(x + w - 2, y + 2, x + w - 1, y + 3, MC_SURFACE);
		g.fill(x + 1, y + h - 3, x + 3, y + h - 2, MC_SURFACE);
		g.fill(x + 2, y + h - 2, x + 3, y + h - 1, MC_SURFACE);
	}

	/** Draws a recessed Minecraft inventory slot/inset box (#373737 top/left, #FFFFFF bottom/right, #8B8B8B fill). */
	private static void drawVanillaSlot(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
		g.fill(x, y, x + w - 1, y + 1, MC_SLOT_DARK);
		g.fill(x, y, x + 1, y + h - 1, MC_SLOT_DARK);
		g.fill(x + 1, y + h - 1, x + w, y + h, MC_HIGHLIGHT);
		g.fill(x + w - 1, y + 1, x + w, y + h, MC_HIGHLIGHT);
		g.fill(x + w - 1, y, x + w, y + 1, MC_SLOT_FILL);
		g.fill(x, y + h - 1, x + 1, y + h, MC_SLOT_FILL);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
	}

	/** Custom Minecraft-beveled '+' stat allocation button (no dark/purple outline). */
	private static final class VanillaPlusButton extends AbstractWidget {
		private final PlayerStats.StatKind kind;

		VanillaPlusButton(PlayerStats.StatKind kind) {
			super(0, 0, 15, 15, Component.literal("+"));
			this.kind = kind;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			if (!active) return;
			ClientPlayNetworking.send(new AllocateStatPayload(kind));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX(), y = getY(), w = getWidth(), h = getHeight();
			g.fill(x, y, x + w, y + h, MC_BLACK);
			if (!active) {
				g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF9A9A9A);
				g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF7D7D7D);
				g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF7D7D7D);
			} else if (isHovered()) {
				g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFE6D48A);
				g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFF5C2);
				g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFF5C2);
				g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF947D32);
				g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF947D32);
			} else {
				g.fill(x + 1, y + 1, x + w - 1, y + h - 1, MC_SURFACE);
				g.fill(x + 1, y + 1, x + w - 1, y + 2, MC_HIGHLIGHT);
				g.fill(x + 1, y + 1, x + 2, y + h - 1, MC_HIGHLIGHT);
				g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, MC_SHADOW);
				g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, MC_SHADOW);
			}
			Font font = Minecraft.getInstance().font;
			int tx = x + (w - font.width("+")) / 2 + 1;
			int ty = y + (h - 7) / 2;
			int color = !active ? 0xFF666666 : (isHovered() ? 0xFF3A2C00 : 0xFF282828);
			g.text(font, "+", tx, ty, color, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}

	/** Passive vanilla-styled backdrop widget drawn before the '+' buttons so buttons always render cleanly on top. */
	private static final class StatsBackdropWidget extends AbstractWidget {
		private final InventoryScreen inv;
		private final Map<PlayerStats.StatKind, VanillaPlusButton> buttons;

		StatsBackdropWidget(InventoryScreen inv, Map<PlayerStats.StatKind, VanillaPlusButton> buttons) {
			super(0, 0, PANEL_W, PANEL_H, Component.empty());
			this.inv = inv;
			this.buttons = buttons;
			this.active = false;
		}

		void syncButtonPositions() {
			Minecraft client = Minecraft.getInstance();
			int px = panelX(inv);
			int py = panelY(inv);
			setPosition(px, py);
			boolean canSpend = client.player != null && PlayerRpg.get(client.player).statPoints() > 0;
			PlayerStats.StatKind[] kinds = PlayerStats.StatKind.values();
			for (int i = 0; i < kinds.length; i++) {
				VanillaPlusButton btn = buttons.get(kinds[i]);
				if (btn == null) continue;
				btn.setPosition(px + PANEL_W - 22, py + ROW_START_Y + i * ROW_STEP_Y + 2);
				btn.active = canSpend;
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			syncButtonPositions();
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) return;

			PlayerStats stats = PlayerRpg.get(client.player);
			Font font = client.font;
			int px = getX();
			int py = getY();

			// Native Minecraft grey beveled container panel
			drawVanillaWindow(g, px, py, PANEL_W, PANEL_H);

			// Header: Title (#404040, no shadow, matching vanilla "合成") + Level badge
			Component title = Component.translatable("ui.noclip_backrooms.rpg.title");
			g.text(font, title, px + 8, py + 7, MC_TEXT, false);
			String lvText = "Lv." + stats.level();
			g.text(font, lvText, px + PANEL_W - 8 - font.width(lvText), py + 7, 0xFF8A5A00, false);

			// Recessed XP bar
			int barX = px + 8;
			int barW = PANEL_W - 16;
			int xpY = py + 20;
			float xpRatio = Mth.clamp((float) stats.xp() / Math.max(1, stats.xpNeeded()), 0.0F, 1.0F);
			drawVanillaSlot(g, barX, xpY, barW, 8, 0xFF3B3B3B);
			int xpFill = Math.round((barW - 2) * xpRatio);
			if (xpFill > 0) {
				g.fill(barX + 1, xpY + 1, barX + 1 + xpFill, xpY + 7, 0xFF55C449);
				g.fill(barX + 1, xpY + 1, barX + 1 + xpFill, xpY + 3, 0xFF8AF07A);
			}
			drawSmallText(g, font, "XP " + stats.xp() + "/" + stats.xpNeeded(), barX + 1, xpY + 10, MC_TEXT_MUTED, 0.72F);

			// Recessed Sanity bar with mini 9x9 pixel-art Mind-Eye icon
			int sanY = py + 38;
			float sanRatio = stats.sanityRatio();
			boolean critical = stats.isHallucinating();
			int iconU = critical ? 45 : (sanRatio < 0.60F ? 27 : 9);
			g.blit(RenderPipelines.GUI_TEXTURED, SanityBarHud.ICONS_TEXTURE, barX, sanY, iconU, 0, 9, 9, SanityBarHud.TEX_W, SanityBarHud.TEX_H);

			int sBarX = barX + 12;
			int sBarW = barW - 12;
			drawVanillaSlot(g, sBarX, sanY + 1, sBarW, 8, 0xFF3B3B3B);
			int sanFill = Math.round((sBarW - 2) * sanRatio);
			int sanColor = critical ? 0xFFD92650 : (sanRatio < 0.60F ? 0xFF884FE3 : 0xFF28B9E0);
			int sanHi = critical ? 0xFFFF7A9A : (sanRatio < 0.60F ? 0xFFBC92FF : 0xFF7BE8FF);
			if (sanFill > 0) {
				g.fill(sBarX + 1, sanY + 2, sBarX + 1 + sanFill, sanY + 8, sanColor);
				g.fill(sBarX + 1, sanY + 2, sBarX + 1 + sanFill, sanY + 4, sanHi);
			}
			String sanLabel = Component.translatable(
					critical ? "ui.noclip_backrooms.rpg.sanity_critical" : "ui.noclip_backrooms.rpg.sanity_ok",
					Math.round(stats.sanity()), Math.round(stats.maxSanity())).getString();
			drawSmallText(g, font, sanLabel, barX + 1, sanY + 11, critical ? 0xFFA61436 : MC_TEXT, 0.72F);

			// Beveled vanilla divider + unspent stat points readout
			g.fill(px + 7, py + 58, px + PANEL_W - 7, py + 59, MC_SHADOW);
			g.fill(px + 7, py + 59, px + PANEL_W - 7, py + 60, MC_HIGHLIGHT);
			Component ptsLabel = Component.translatable("ui.noclip_backrooms.rpg.points", stats.statPoints());
			int ptsColor = stats.statPoints() > 0 ? 0xFF8A5A00 : MC_TEXT_MUTED;
			drawSmallText(g, font, ptsLabel.getString(), px + 8, py + 63, ptsColor, 0.78F);

			// Four recessed vanilla-slot stat rows (with VIT regen & SAN regen bonuses clearly visible)
			drawStatRow(g, font, px, py + ROW_START_Y,
					Component.translatable("ui.noclip_backrooms.rpg.str").getString(),
					stats.strength(),
					String.format(Locale.ROOT, "+%.1f", stats.attackDamageBonus()) + " "
							+ Component.translatable("ui.noclip_backrooms.rpg.unit_atk").getString(),
					0xFFC94434, mouseX, mouseY);

			drawStatRow(g, font, px, py + ROW_START_Y + ROW_STEP_Y,
					Component.translatable("ui.noclip_backrooms.rpg.agi").getString(),
					stats.agility(),
					"+" + Math.round(stats.moveSpeedBonus() * 100.0) + "% "
							+ Component.translatable("ui.noclip_backrooms.rpg.unit_spd").getString(),
					0xFF2D9652, mouseX, mouseY);

			drawStatRow(g, font, px, py + ROW_START_Y + ROW_STEP_Y * 2,
					Component.translatable("ui.noclip_backrooms.rpg.vit").getString(),
					stats.vitality(),
					Component.translatable("ui.noclip_backrooms.rpg.vit_short",
							String.format(Locale.ROOT, "%.0f", stats.maxHealthBonus()),
							String.format(Locale.ROOT, "%.2f", stats.healthRegenPerSec())).getString(),
					0xFFD1345B, mouseX, mouseY);

			drawStatRow(g, font, px, py + ROW_START_Y + ROW_STEP_Y * 3,
					Component.translatable("ui.noclip_backrooms.rpg.san").getString(),
					stats.sanityStat(),
					Component.translatable("ui.noclip_backrooms.rpg.san_short",
							Math.round(stats.maxSanity()),
							Math.round((stats.sanityRegenMultiplier() - 1.0F) * 100.0F)).getString(),
					0xFF1F9AC2, mouseX, mouseY);
		}

		private void drawStatRow(GuiGraphicsExtractor g, Font font, int px, int ry,
				String name, int value, String bonus, int accentColor, int mouseX, int mouseY) {
			int rx = px + 7;
			int rw = PANEL_W - 32;
			int rh = ROW_STEP_Y - 3;
			boolean hovered = mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY < ry + rh;
			drawVanillaSlot(g, rx, ry, rw, rh, hovered ? MC_SLOT_HOVER : MC_SLOT_FILL);
			g.fill(rx + 1, ry + 1, rx + 3, ry + rh - 1, accentColor);
			drawSmallTextShadow(g, font, name + "  " + value, rx + 6, ry + 2, 0xFFFFFFFF, 0.78F);
			drawSmallTextShadow(g, font, bonus, rx + 6, ry + 11, 0xFFE6ECF5, 0.68F);
		}

		private static void drawSmallText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, float scale) {
			g.pose().pushMatrix();
			g.pose().scale(scale, scale);
			g.text(font, text, Math.round(x / scale), Math.round(y / scale), color, false);
			g.pose().popMatrix();
		}

		private static void drawSmallTextShadow(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, float scale) {
			g.pose().pushMatrix();
			g.pose().scale(scale, scale);
			g.text(font, text, Math.round(x / scale), Math.round(y / scale), color, true);
			g.pose().popMatrix();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
