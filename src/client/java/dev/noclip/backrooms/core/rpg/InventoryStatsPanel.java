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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Attaches an RPG Character Status & Level-Up Stat Allocation panel beside the vanilla InventoryScreen.
 * Automatically shifts to the right side whenever the vanilla Recipe Book opens on the left.
 */
public final class InventoryStatsPanel {
	private static final int PANEL_W = 122;
	private static final int PANEL_H = 166;
	private static final int ROW_START_Y = 74;
	private static final int ROW_STEP_Y = 22;

	private InventoryStatsPanel() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen inv)) return;

			Map<PlayerStats.StatKind, Button> plusButtons = new EnumMap<>(PlayerStats.StatKind.class);
			StatsBackdropWidget backdrop = new StatsBackdropWidget(inv, plusButtons);
			Screens.getWidgets(inv).add(backdrop);

			for (PlayerStats.StatKind kind : PlayerStats.StatKind.values()) {
				Button btn = Button.builder(Component.literal("+"), b ->
						ClientPlayNetworking.send(new AllocateStatPayload(kind)))
						.bounds(0, 0, 14, 14)
						.build();
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
		// Anchor on the left when clear; flip to the right if the Recipe Book pushes the inventory right.
		if (left >= PANEL_W + 8) {
			return left - PANEL_W - 6;
		}
		return Math.min(inv.width - PANEL_W - 4, left + width + 6);
	}

	private static int panelY(InventoryScreen inv) {
		return ((AbstractContainerScreenAccess) inv).noclip_backrooms$topPos();
	}

	private static void extractTooltips(Minecraft client, InventoryScreen inv, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (client.player == null) return;
		int px = panelX(inv);
		int py = panelY(inv);
		if (mouseX < px || mouseX > px + PANEL_W - 18) return;

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
									String.format(Locale.ROOT, "+%.0f", stats.maxHealthBonus()),
									String.format(Locale.ROOT, "+%.2f", stats.armorToughnessBonus()))
					);
					case SANITY -> List.of(
							Component.translatable("ui.noclip_backrooms.rpg.san.title"),
							Component.translatable("ui.noclip_backrooms.rpg.san.desc",
									Math.round(stats.maxSanity()),
									Math.round(stats.sanityDrainResistance() * 100.0F)),
							Component.translatable("ui.noclip_backrooms.rpg.san.hallucination_hint")
					);
				};
				graphics.setComponentTooltipForNextFrame(client.font, lines, mouseX, mouseY);
				return;
			}
		}
	}

	/** Passive backdrop widget drawn before the '+' buttons so buttons always appear cleanly on top. */
	private static final class StatsBackdropWidget extends AbstractWidget {
		private final InventoryScreen inv;
		private final Map<PlayerStats.StatKind, Button> buttons;

		StatsBackdropWidget(InventoryScreen inv, Map<PlayerStats.StatKind, Button> buttons) {
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
				Button btn = buttons.get(kinds[i]);
				if (btn == null) continue;
				btn.setPosition(px + PANEL_W - 19, py + ROW_START_Y + i * ROW_STEP_Y + 2);
				btn.active = canSpend;
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			syncButtonPositions();
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) return;

			PlayerStats stats = PlayerRpg.get(client.player);
			Font font = client.font;
			int px = getX();
			int py = getY();

			// Outer dossier panel background & trim
			graphics.fill(px, py, px + PANEL_W, py + PANEL_H, 0xEA141720);
			graphics.fill(px + 1, py + 1, px + PANEL_W - 1, py + 18, 0xFF1D2230);
			graphics.outline(px, py, PANEL_W, PANEL_H, stats.isHallucinating() ? 0xFFA82B4D : 0xFF3A445C);

			// Header: Title + Level badge
			Component title = Component.translatable("ui.noclip_backrooms.rpg.title");
			graphics.text(font, title, px + 6, py + 6, 0xFFE8EEF7, true);
			String lvText = "Lv." + stats.level();
			graphics.text(font, lvText, px + PANEL_W - 6 - font.width(lvText), py + 6, 0xFFFFD66B, true);

			// RPG XP bar
			int barX = px + 6;
			int barW = PANEL_W - 12;
			int xpY = py + 23;
			float xpRatio = Mth.clamp((float) stats.xp() / Math.max(1, stats.xpNeeded()), 0.0F, 1.0F);
			graphics.fill(barX, xpY, barX + barW, xpY + 6, 0xFF0C0E14);
			graphics.fill(barX + 1, xpY + 1, barX + 1 + Math.round((barW - 2) * xpRatio), xpY + 5, 0xFF56C96B);
			drawSmallText(graphics, font, "XP " + stats.xp() + "/" + stats.xpNeeded(), barX, xpY + 8, 0xFFB5C4D8, 0.72F);

			// Sanity readout bar
			int sanY = py + 41;
			float sanRatio = stats.sanityRatio();
			boolean critical = stats.isHallucinating();
			graphics.fill(barX, sanY, barX + barW, sanY + 6, 0xFF0C0E14);
			int sanColor = critical ? 0xFFE63964 : (sanRatio < 0.60F ? 0xFF9664F2 : 0xFF42CFF2);
			graphics.fill(barX + 1, sanY + 1, barX + 1 + Math.round((barW - 2) * sanRatio), sanY + 5, sanColor);
			String sanLabel = Component.translatable(
					critical ? "ui.noclip_backrooms.rpg.sanity_critical" : "ui.noclip_backrooms.rpg.sanity_ok",
					Math.round(stats.sanity()), Math.round(stats.maxSanity())).getString();
			drawSmallText(graphics, font, sanLabel, barX, sanY + 8, critical ? 0xFFFF7A9E : 0xFF9DE6FA, 0.72F);

			// Unspent stat points divider
			graphics.horizontalLine(px + 5, px + PANEL_W - 6, py + 58, 0xFF2D364A);
			Component ptsLabel = Component.translatable("ui.noclip_backrooms.rpg.points", stats.statPoints());
			int ptsColor = stats.statPoints() > 0 ? 0xFFFFD66B : 0xFF8B97AB;
			drawSmallText(graphics, font, ptsLabel.getString(), px + 6, py + 62, ptsColor, 0.80F);

			// Four core stat rows
			drawStatRow(graphics, font, px, py + ROW_START_Y,
					Component.translatable("ui.noclip_backrooms.rpg.str").getString(),
					stats.strength(),
					String.format(Locale.ROOT, "+%.1f", stats.attackDamageBonus()) + " "
							+ Component.translatable("ui.noclip_backrooms.rpg.unit_atk").getString(),
					0xFFFF8F7A, mouseX, mouseY);

			drawStatRow(graphics, font, px, py + ROW_START_Y + ROW_STEP_Y,
					Component.translatable("ui.noclip_backrooms.rpg.agi").getString(),
					stats.agility(),
					"+" + Math.round(stats.moveSpeedBonus() * 100.0) + "% "
							+ Component.translatable("ui.noclip_backrooms.rpg.unit_spd").getString(),
					0xFF7CE8A8, mouseX, mouseY);

			drawStatRow(graphics, font, px, py + ROW_START_Y + ROW_STEP_Y * 2,
					Component.translatable("ui.noclip_backrooms.rpg.vit").getString(),
					stats.vitality(),
					String.format(Locale.ROOT, "+%.0f", stats.maxHealthBonus()) + " "
							+ Component.translatable("ui.noclip_backrooms.rpg.unit_hp").getString(),
					0xFFFF789B, mouseX, mouseY);

			drawStatRow(graphics, font, px, py + ROW_START_Y + ROW_STEP_Y * 3,
					Component.translatable("ui.noclip_backrooms.rpg.san").getString(),
					stats.sanityStat(),
					Component.translatable("ui.noclip_backrooms.rpg.unit_cap", Math.round(stats.maxSanity())).getString(),
					0xFF7BE4FF, mouseX, mouseY);
		}

		private void drawStatRow(GuiGraphicsExtractor graphics, Font font, int px, int ry,
				String name, int value, String bonus, int accentColor, int mouseX, int mouseY) {
			boolean hovered = mouseX >= px + 4 && mouseX <= px + PANEL_W - 22 && mouseY >= ry && mouseY < ry + ROW_STEP_Y - 2;
			graphics.fill(px + 4, ry, px + PANEL_W - 22, ry + ROW_STEP_Y - 3, hovered ? 0xFF252D3F : 0xFF1A1F2B);
			graphics.fill(px + 4, ry, px + 6, ry + ROW_STEP_Y - 3, accentColor);
			drawSmallText(graphics, font, name + "  " + value, px + 9, ry + 2, 0xFFF0F4FA, 0.80F);
			drawSmallText(graphics, font, bonus, px + 9, ry + 11, 0xFF9BA8BE, 0.70F);
		}

		private static void drawSmallText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, float scale) {
			graphics.pose().pushMatrix();
			graphics.pose().scale(scale, scale);
			graphics.text(font, text, Math.round(x / scale), Math.round(y / scale), color, false);
			graphics.pose().popMatrix();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
