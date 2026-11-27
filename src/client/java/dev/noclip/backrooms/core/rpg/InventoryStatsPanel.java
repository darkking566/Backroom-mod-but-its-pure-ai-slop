package dev.noclip.backrooms.core.rpg;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.mixin.client.AbstractContainerScreenAccess;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
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
 * Attaches a native Minecraft-styled two-tab RPG Character Status panel beside {@link InventoryScreen}:
 * <ul>
 * <li>Tab 0 (Stats): Level, XP bar, Sanity bar, and the 4 core stats (STR/AGI/VIT/SAN);</li>
 * <li>Tab 1 (Adv.): Hidden stats unlocked by Forbidden Scrolls (LUK/PSY/ENG) and active Specialization Badge switcher.</li>
 * </ul>
 */
public final class InventoryStatsPanel {
	private static final int PANEL_W = 132;
	private static final int PANEL_H = 166;
	private static final int TAB0_ROW_Y = 80;
	private static final int TAB0_STEP_Y = 21;
	private static final int TAB1_ROW_Y = 45;
	private static final int TAB1_STEP_Y = 22;
	private static final int BADGE_BOX_Y = 124;

	private static int activeTab = 0;

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

	/** Selects the active RPG panel tab (0 = base stats, 1 = hidden stats & badge). */
	public static void selectTab(int tab) {
		activeTab = tab == 1 ? 1 : 0;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof InventoryScreen inv)) return;

			Map<PlayerStats.StatKind, VanillaPlusButton> plusButtons = new EnumMap<>(PlayerStats.StatKind.class);
			VanillaTabButton tab0Btn = new VanillaTabButton(0, "ui.noclip_backrooms.rpg.tab_stats");
			VanillaTabButton tab1Btn = new VanillaTabButton(1, "ui.noclip_backrooms.rpg.tab_adv");
			BadgeActionButton switchBtn = new BadgeActionButton(true);
			BadgeActionButton clearBtn = new BadgeActionButton(false);

			StatsBackdropWidget backdrop = new StatsBackdropWidget(
					inv, plusButtons, tab0Btn, tab1Btn, switchBtn, clearBtn);
			Screens.getWidgets(inv).add(backdrop);
			Screens.getWidgets(inv).add(tab0Btn);
			Screens.getWidgets(inv).add(tab1Btn);
			Screens.getWidgets(inv).add(switchBtn);
			Screens.getWidgets(inv).add(clearBtn);

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
		if (mouseX < px + 6 || mouseX > px + PANEL_W - 6) return;

		PlayerStats stats = PlayerRpg.get(client.player);
		if (activeTab == 0) {
			if (mouseX > px + PANEL_W - 22) return;
			PlayerStats.StatKind[] base = {
					PlayerStats.StatKind.STRENGTH, PlayerStats.StatKind.AGILITY,
					PlayerStats.StatKind.VITALITY, PlayerStats.StatKind.SANITY
			};
			for (int i = 0; i < base.length; i++) {
				int ry = py + TAB0_ROW_Y + i * TAB0_STEP_Y;
				if (mouseY >= ry && mouseY < ry + TAB0_STEP_Y - 3) {
					PlayerStats.StatKind kind = base[i];
					List<Component> lines = switch (kind) {
						case STRENGTH -> List.of(
								Component.translatable("ui.noclip_backrooms.rpg.str.title"),
								Component.translatable("ui.noclip_backrooms.rpg.str.desc",
										String.format(Locale.ROOT, "+%.1f", stats.attackDamageBonus())),
								Component.translatable("ui.noclip_backrooms.rpg.str.adv",
										String.format(Locale.ROOT, "+%.2f", stats.knockbackBonus()),
										Math.round(stats.heavySwingSpeedBonus() * 100.0))
						);
						case AGILITY -> List.of(
								Component.translatable("ui.noclip_backrooms.rpg.agi.title"),
								Component.translatable("ui.noclip_backrooms.rpg.agi.desc",
										Math.round(stats.moveSpeedBonus() * 100.0), Math.round(stats.attackSpeedBonus() * 100.0)),
								Component.translatable("ui.noclip_backrooms.rpg.agi.adv",
										Math.round((1.0F - stats.firearmCooldownScale()) * 100.0F),
										Math.round((1.0F - stats.firearmSpreadScale()) * 100.0F),
										Math.round(stats.critChance() * 100.0F))
						);
						case VITALITY -> List.of(
								Component.translatable("ui.noclip_backrooms.rpg.vit.title"),
								Component.translatable("ui.noclip_backrooms.rpg.vit.desc",
										String.format(Locale.ROOT, "+%.0f", stats.maxHealthBonus())),
								Component.translatable("ui.noclip_backrooms.rpg.vit.regen",
										String.format(Locale.ROOT, "+%.2f", stats.healthRegenPerSec())),
								Component.translatable("ui.noclip_backrooms.rpg.vit.adv",
										Math.round(stats.harmfulDurationReduction() * 100.0F))
						);
						case SANITY -> List.of(
								Component.translatable("ui.noclip_backrooms.rpg.san.title"),
								Component.translatable("ui.noclip_backrooms.rpg.san.desc",
										Math.round(stats.maxSanity()),
										Math.round(stats.sanityDrainResistance() * 100.0F)),
								Component.translatable("ui.noclip_backrooms.rpg.san.regen",
										Math.round((stats.sanityRegenMultiplier() - 1.0F) * 100.0F),
										String.format(Locale.ROOT, "+%.2f", stats.passiveSanityRegenPerSec())),
								Component.translatable("ui.noclip_backrooms.rpg.san.adv",
										Math.round((1.0F - stats.magicSanityCostScale()) * 100.0F)),
								Component.translatable("ui.noclip_backrooms.rpg.san.hallucination_hint")
						);
						default -> List.of();
					};
					graphics.setComponentTooltipForNextFrame(client.font, lines, mouseX, mouseY);
					return;
				}
			}
		} else {
			PlayerStats.StatKind[] hidden = {
					PlayerStats.StatKind.LUCK, PlayerStats.StatKind.PSI, PlayerStats.StatKind.ENGINEERING
			};
			if (mouseX <= px + PANEL_W - 22) {
				for (int i = 0; i < hidden.length; i++) {
					int ry = py + TAB1_ROW_Y + i * TAB1_STEP_Y;
					if (mouseY >= ry && mouseY < ry + TAB1_STEP_Y - 3) {
						PlayerStats.StatKind kind = hidden[i];
						boolean unlocked = stats.isUnlocked(kind);
						List<Component> lines = switch (kind) {
							case LUCK -> unlocked
									? List.of(
											Component.translatable("ui.noclip_backrooms.rpg.luk.title"),
											Component.translatable("ui.noclip_backrooms.rpg.luk.desc",
													Math.round(stats.luckLootChance() * 100.0),
													Math.round(stats.luckContractBonus() * 100.0),
													stats.luckFateRollBonus()))
									: List.of(
											Component.translatable("ui.noclip_backrooms.rpg.luk.title"),
											Component.translatable("ui.noclip_backrooms.rpg.locked_hint").withStyle(ChatFormatting.GRAY));
							case PSI -> unlocked
									? List.of(
											Component.translatable("ui.noclip_backrooms.rpg.psy.title"),
											Component.translatable("ui.noclip_backrooms.rpg.psy.desc",
													Math.round(stats.psiSanityResistance() * 100.0F),
													Math.round((stats.psiSpellRadiusScale() - 1.0F) * 100.0F)))
									: List.of(
											Component.translatable("ui.noclip_backrooms.rpg.psy.title"),
											Component.translatable("ui.noclip_backrooms.rpg.locked_hint").withStyle(ChatFormatting.GRAY));
							case ENGINEERING -> unlocked
									? List.of(
											Component.translatable("ui.noclip_backrooms.rpg.eng.title"),
											Component.translatable("ui.noclip_backrooms.rpg.eng.desc",
													Math.round(stats.engAmmoSaveChance() * 100.0F),
													Math.round(stats.engSalvageBonusChance() * 100.0)))
									: List.of(
											Component.translatable("ui.noclip_backrooms.rpg.eng.title"),
											Component.translatable("ui.noclip_backrooms.rpg.locked_hint").withStyle(ChatFormatting.GRAY));
							default -> List.of();
						};
						graphics.setComponentTooltipForNextFrame(client.font, lines, mouseX, mouseY);
						return;
					}
				}
			}
			int boxY = py + BADGE_BOX_Y;
			if (mouseY >= boxY && mouseY <= boxY + 19) {
				Badges.BadgeData bData = Badges.get(client.player);
				Badges.Badge active = bData.activeBadge();
				List<Component> lines;
				if (active != null) {
					lines = List.of(
							active.title().copy().withStyle(ChatFormatting.GOLD),
							active.perkText().copy().withStyle(ChatFormatting.GREEN),
							active.drawbackText().copy().withStyle(ChatFormatting.RED),
							Component.translatable("ui.noclip_backrooms.badge.switch_hint").withStyle(ChatFormatting.GRAY)
					);
				} else {
					lines = List.of(
							Component.translatable("ui.noclip_backrooms.badge.none").withStyle(ChatFormatting.WHITE),
							Component.translatable("ui.noclip_backrooms.badge.vendor_hint").withStyle(ChatFormatting.GRAY),
							Component.translatable("ui.noclip_backrooms.badge.switch_hint").withStyle(ChatFormatting.GRAY)
					);
				}
				graphics.setComponentTooltipForNextFrame(client.font, lines, mouseX, mouseY);
			}
		}
	}

	/** Draws a pixel-accurate native Minecraft container frame matching inventory.png. */
	private static void drawVanillaWindow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, MC_SURFACE);

		g.fill(x + 2, y, x + w - 2, y + 1, MC_BLACK);
		g.fill(x + 2, y + h - 1, x + w - 2, y + h, MC_BLACK);
		g.fill(x, y + 2, x + 1, y + h - 2, MC_BLACK);
		g.fill(x + w - 1, y + 2, x + w, y + h - 2, MC_BLACK);
		g.fill(x + 1, y + 1, x + 2, y + 2, MC_BLACK);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, MC_BLACK);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, MC_BLACK);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, MC_BLACK);

		g.fill(x + 2, y + 1, x + w - 2, y + 3, MC_HIGHLIGHT);
		g.fill(x + 1, y + 2, x + 3, y + h - 2, MC_HIGHLIGHT);
		g.fill(x + 2, y + 2, x + 4, y + 4, MC_HIGHLIGHT);

		g.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, MC_SHADOW);
		g.fill(x + w - 3, y + 2, x + w - 1, y + h - 2, MC_SHADOW);
		g.fill(x + w - 4, y + h - 4, x + w - 2, y + h - 2, MC_SHADOW);

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

	private static void drawBeveledButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean enabled, boolean selected, boolean hovered) {
		g.fill(x, y, x + w, y + h, MC_BLACK);
		if (!enabled) {
			g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF9A9A9A);
			g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF7D7D7D);
			g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF7D7D7D);
		} else if (selected || hovered) {
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
	}

	private static final class VanillaTabButton extends AbstractWidget {
		private final int tab;
		private final String labelKey;

		VanillaTabButton(int tab, String labelKey) {
			super(0, 0, 57, 12, Component.translatable(labelKey));
			this.tab = tab;
			this.labelKey = labelKey;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			activeTab = tab;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			boolean selected = activeTab == tab;
			drawBeveledButton(g, getX(), getY(), getWidth(), getHeight(), true, selected, isHovered());
			Font font = Minecraft.getInstance().font;
			int color = selected ? 0xFF3A2C00 : 0xFF282828;
			String text = Component.translatable(labelKey).getString();
			float scale = 0.78F;
			int tw = Math.round(font.width(text) * scale);
			int tx = getX() + (getWidth() - tw) / 2;
			int ty = getY() + 3;
			g.pose().pushMatrix();
			g.pose().scale(scale, scale);
			g.text(font, text, Math.round(tx / scale), Math.round(ty / scale), color, false);
			g.pose().popMatrix();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}

	private static final class BadgeActionButton extends AbstractWidget {
		private final boolean cycleNext;

		BadgeActionButton(boolean cycleNext) {
			super(0, 0, cycleNext ? 66 : 49, 15, Component.empty());
			this.cycleNext = cycleNext;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			if (!active) return;
			Minecraft client = Minecraft.getInstance();
			if (client.player == null) return;
			if (!cycleNext) {
				ClientPlayNetworking.send(new BadgeActionPayload("equip", ""));
				return;
			}
			Badges.BadgeData data = Badges.get(client.player);
			if (data.unlocked().isEmpty()) return;
			String cur = data.activeId();
			int idx = cur.isEmpty() ? -1 : data.unlocked().indexOf(cur);
			String nextId = data.unlocked().get((idx + 1) % data.unlocked().size());
			ClientPlayNetworking.send(new BadgeActionPayload("equip", nextId));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			drawBeveledButton(g, getX(), getY(), getWidth(), getHeight(), active, false, isHovered());
			Font font = Minecraft.getInstance().font;
			String text = Component.translatable(
					cycleNext ? "ui.noclip_backrooms.badge.switch_btn" : "ui.noclip_backrooms.badge.clear_btn").getString();
			int color = !active ? 0xFF666666 : (isHovered() ? 0xFF3A2C00 : 0xFF282828);
			float scale = 0.72F;
			int tw = Math.round(font.width(text) * scale);
			int tx = getX() + (getWidth() - tw) / 2;
			int ty = getY() + 4;
			g.pose().pushMatrix();
			g.pose().scale(scale, scale);
			g.text(font, text, Math.round(tx / scale), Math.round(ty / scale), color, false);
			g.pose().popMatrix();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
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
			drawBeveledButton(g, x, y, w, h, active, false, isHovered());
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

	/** Passive vanilla-styled backdrop widget drawn behind the panel buttons. */
	private static final class StatsBackdropWidget extends AbstractWidget {
		private final InventoryScreen inv;
		private final Map<PlayerStats.StatKind, VanillaPlusButton> buttons;
		private final VanillaTabButton tab0Btn;
		private final VanillaTabButton tab1Btn;
		private final BadgeActionButton switchBtn;
		private final BadgeActionButton clearBtn;

		StatsBackdropWidget(InventoryScreen inv, Map<PlayerStats.StatKind, VanillaPlusButton> buttons,
				VanillaTabButton tab0Btn, VanillaTabButton tab1Btn,
				BadgeActionButton switchBtn, BadgeActionButton clearBtn) {
			super(0, 0, PANEL_W, PANEL_H, Component.empty());
			this.inv = inv;
			this.buttons = buttons;
			this.tab0Btn = tab0Btn;
			this.tab1Btn = tab1Btn;
			this.switchBtn = switchBtn;
			this.clearBtn = clearBtn;
			this.active = false;
		}

		void syncButtonPositions() {
			Minecraft client = Minecraft.getInstance();
			int px = panelX(inv);
			int py = panelY(inv);
			setPosition(px, py);

			tab0Btn.setPosition(px + 8, py + 17);
			tab1Btn.setPosition(px + 67, py + 17);

			PlayerStats stats = client.player != null ? PlayerRpg.get(client.player) : PlayerStats.initial();
			PlayerStats.StatKind[] tab0Kinds = {
					PlayerStats.StatKind.STRENGTH, PlayerStats.StatKind.AGILITY,
					PlayerStats.StatKind.VITALITY, PlayerStats.StatKind.SANITY
			};
			for (int i = 0; i < tab0Kinds.length; i++) {
				VanillaPlusButton btn = buttons.get(tab0Kinds[i]);
				if (btn == null) continue;
				btn.visible = activeTab == 0;
				btn.active = activeTab == 0 && stats.canAllocate(tab0Kinds[i]);
				btn.setPosition(px + PANEL_W - 22, py + TAB0_ROW_Y + i * TAB0_STEP_Y + 1);
			}

			PlayerStats.StatKind[] tab1Kinds = {
					PlayerStats.StatKind.LUCK, PlayerStats.StatKind.PSI, PlayerStats.StatKind.ENGINEERING
			};
			for (int i = 0; i < tab1Kinds.length; i++) {
				VanillaPlusButton btn = buttons.get(tab1Kinds[i]);
				if (btn == null) continue;
				boolean unlocked = stats.isUnlocked(tab1Kinds[i]);
				btn.visible = activeTab == 1 && unlocked;
				btn.active = activeTab == 1 && stats.canAllocate(tab1Kinds[i]);
				btn.setPosition(px + PANEL_W - 22, py + TAB1_ROW_Y + i * TAB1_STEP_Y + 2);
			}

			Badges.BadgeData bData = client.player != null ? Badges.get(client.player) : Badges.BadgeData.initial();
			switchBtn.visible = activeTab == 1;
			switchBtn.active = activeTab == 1 && !bData.unlocked().isEmpty();
			switchBtn.setPosition(px + 7, py + 145);

			clearBtn.visible = activeTab == 1;
			clearBtn.active = activeTab == 1 && bData.activeBadge() != null;
			clearBtn.setPosition(px + 76, py + 145);
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

			drawVanillaWindow(g, px, py, PANEL_W, PANEL_H);

			// Header
			Component title = Component.translatable("ui.noclip_backrooms.rpg.title");
			g.text(font, title, px + 8, py + 6, MC_TEXT, false);
			String lvText = "Lv." + stats.level();
			g.text(font, lvText, px + PANEL_W - 8 - font.width(lvText), py + 6, 0xFF8A5A00, false);

			if (activeTab == 0) {
				extractTab0(g, font, stats, px, py, mouseX, mouseY);
			} else {
				extractTab1(g, client, font, stats, px, py, mouseX, mouseY);
			}
		}

		private void extractTab0(GuiGraphicsExtractor g, Font font, PlayerStats stats, int px, int py, int mouseX, int mouseY) {
			int barX = px + 8;
			int barW = PANEL_W - 16;
			int xpY = py + 32;
			float xpRatio = Mth.clamp((float) stats.xp() / Math.max(1, stats.xpNeeded()), 0.0F, 1.0F);
			drawVanillaSlot(g, barX, xpY, barW, 6, 0xFF3B3B3B);
			int xpFill = Math.round((barW - 2) * xpRatio);
			if (xpFill > 0) {
				g.fill(barX + 1, xpY + 1, barX + 1 + xpFill, xpY + 5, 0xFF55C449);
				g.fill(barX + 1, xpY + 1, barX + 1 + xpFill, xpY + 2, 0xFF8AF07A);
			}
			drawSmallText(g, font, "XP " + stats.xp() + "/" + stats.xpNeeded(), barX + 1, py + 39, MC_TEXT_MUTED, 0.70F);

			int sanY = py + 49;
			float sanRatio = stats.sanityRatio();
			boolean critical = stats.isHallucinating();
			int iconU = critical ? 45 : (sanRatio < 0.60F ? 27 : 9);
			g.blit(RenderPipelines.GUI_TEXTURED, SanityBarHud.ICONS_TEXTURE, barX, sanY, iconU, 0, 9, 9, SanityBarHud.TEX_W, SanityBarHud.TEX_H);

			int sBarX = barX + 12;
			int sBarW = barW - 12;
			drawVanillaSlot(g, sBarX, sanY + 1, sBarW, 7, 0xFF3B3B3B);
			int sanFill = Math.round((sBarW - 2) * sanRatio);
			int sanColor = critical ? 0xFFD92650 : (sanRatio < 0.60F ? 0xFF884FE3 : 0xFF28B9E0);
			int sanHi = critical ? 0xFFFF7A9A : (sanRatio < 0.60F ? 0xFFBC92FF : 0xFF7BE8FF);
			if (sanFill > 0) {
				g.fill(sBarX + 1, sanY + 2, sBarX + 1 + sanFill, sanY + 7, sanColor);
				g.fill(sBarX + 1, sanY + 2, sBarX + 1 + sanFill, sanY + 3, sanHi);
			}
			String sanLabel = Component.translatable(
					critical ? "ui.noclip_backrooms.rpg.sanity_critical" : "ui.noclip_backrooms.rpg.sanity_ok",
					Math.round(stats.sanity()), Math.round(stats.maxSanity())).getString();
			drawSmallText(g, font, sanLabel, barX + 1, py + 60, critical ? 0xFFA61436 : MC_TEXT, 0.70F);

			g.fill(px + 7, py + 68, px + PANEL_W - 7, py + 69, MC_SHADOW);
			g.fill(px + 7, py + 69, px + PANEL_W - 7, py + 70, MC_HIGHLIGHT);
			Component ptsLabel = Component.translatable("ui.noclip_backrooms.rpg.points", stats.statPoints());
			int ptsColor = stats.statPoints() > 0 ? 0xFF8A5A00 : MC_TEXT_MUTED;
			drawSmallText(g, font, ptsLabel.getString(), px + 8, py + 72, ptsColor, 0.74F);

			drawStatRow(g, font, px, py + TAB0_ROW_Y, 18,
					Component.translatable("ui.noclip_backrooms.rpg.str").getString(),
					stats.strength(),
					String.format(Locale.ROOT, "+%.1f", stats.attackDamageBonus()) + " "
							+ Component.translatable("ui.noclip_backrooms.rpg.unit_atk").getString(),
					0xFFC94434, true, mouseX, mouseY);

			drawStatRow(g, font, px, py + TAB0_ROW_Y + TAB0_STEP_Y, 18,
					Component.translatable("ui.noclip_backrooms.rpg.agi").getString(),
					stats.agility(),
					"+" + Math.round(stats.moveSpeedBonus() * 100.0) + "% "
							+ Component.translatable("ui.noclip_backrooms.rpg.unit_spd").getString(),
					0xFF2D9652, true, mouseX, mouseY);

			drawStatRow(g, font, px, py + TAB0_ROW_Y + TAB0_STEP_Y * 2, 18,
					Component.translatable("ui.noclip_backrooms.rpg.vit").getString(),
					stats.vitality(),
					Component.translatable("ui.noclip_backrooms.rpg.vit_short",
							String.format(Locale.ROOT, "%.0f", stats.maxHealthBonus()),
							String.format(Locale.ROOT, "%.2f", stats.healthRegenPerSec())).getString(),
					0xFFD1345B, true, mouseX, mouseY);

			drawStatRow(g, font, px, py + TAB0_ROW_Y + TAB0_STEP_Y * 3, 18,
					Component.translatable("ui.noclip_backrooms.rpg.san").getString(),
					stats.sanityStat(),
					Component.translatable("ui.noclip_backrooms.rpg.san_short",
							Math.round(stats.maxSanity()),
							Math.round((stats.sanityRegenMultiplier() - 1.0F) * 100.0F)).getString(),
					0xFF1F9AC2, true, mouseX, mouseY);
		}

		private void extractTab1(GuiGraphicsExtractor g, Minecraft client, Font font, PlayerStats stats, int px, int py, int mouseX, int mouseY) {
			Component ptsLabel = Component.translatable("ui.noclip_backrooms.rpg.points", stats.statPoints());
			int ptsColor = stats.statPoints() > 0 ? 0xFF8A5A00 : MC_TEXT_MUTED;
			drawSmallText(g, font, ptsLabel.getString(), px + 8, py + 32, ptsColor, 0.74F);

			g.fill(px + 7, py + 41, px + PANEL_W - 7, py + 42, MC_SHADOW);
			g.fill(px + 7, py + 42, px + PANEL_W - 7, py + 43, MC_HIGHLIGHT);

			String lockedShort = Component.translatable("ui.noclip_backrooms.rpg.locked_short").getString();
			drawStatRow(g, font, px, py + TAB1_ROW_Y, 19,
					Component.translatable("ui.noclip_backrooms.rpg.luk").getString(),
					stats.luck(),
					stats.luckUnlocked()
							? Component.translatable("ui.noclip_backrooms.rpg.luk_short",
									Math.round(stats.luckLootChance() * 100.0),
									Math.round(stats.luckContractBonus() * 100.0)).getString()
							: lockedShort,
					0xFFE2AE34, stats.luckUnlocked(), mouseX, mouseY);

			drawStatRow(g, font, px, py + TAB1_ROW_Y + TAB1_STEP_Y, 19,
					Component.translatable("ui.noclip_backrooms.rpg.psy").getString(),
					stats.psi(),
					stats.psiUnlocked()
							? Component.translatable("ui.noclip_backrooms.rpg.psy_short",
									Math.round(stats.psiSanityResistance() * 100.0F),
									Math.round((stats.psiSpellRadiusScale() - 1.0F) * 100.0F)).getString()
							: lockedShort,
					0xFFA158E6, stats.psiUnlocked(), mouseX, mouseY);

			drawStatRow(g, font, px, py + TAB1_ROW_Y + TAB1_STEP_Y * 2, 19,
					Component.translatable("ui.noclip_backrooms.rpg.eng").getString(),
					stats.eng(),
					stats.engUnlocked()
							? Component.translatable("ui.noclip_backrooms.rpg.eng_short",
									Math.round(stats.engAmmoSaveChance() * 100.0F),
									Math.round(stats.engSalvageBonusChance() * 100.0)).getString()
							: lockedShort,
					0xFF3CB39B, stats.engUnlocked(), mouseX, mouseY);

			g.fill(px + 7, py + 111, px + PANEL_W - 7, py + 112, MC_SHADOW);
			g.fill(px + 7, py + 112, px + PANEL_W - 7, py + 113, MC_HIGHLIGHT);

			Badges.BadgeData bData = Badges.get(client.player);
			Badges.Badge active = bData.activeBadge();
			drawSmallText(g, font, Component.translatable("ui.noclip_backrooms.badge.header").getString(),
					px + 8, py + 115, MC_TEXT, 0.72F);
			String countTxt = bData.unlocked().size() + "/" + Badges.Badge.values().length;
			drawSmallText(g, font, countTxt, px + PANEL_W - 24, py + 115, MC_TEXT_MUTED, 0.72F);

			int bx = px + 7;
			int bw = PANEL_W - 14;
			int by = py + BADGE_BOX_Y;
			boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY < by + 19;
			drawVanillaSlot(g, bx, by, bw, 19, hovered ? MC_SLOT_HOVER : MC_SLOT_FILL);
			g.fill(bx + 1, by + 1, bx + 3, by + 18, active != null ? 0xFFE6B840 : 0xFF606060);
			String badgeName = active != null
					? active.title().getString()
					: Component.translatable("ui.noclip_backrooms.badge.none").getString();
			drawSmallTextShadow(g, font, badgeName, bx + 6, by + 2, active != null ? 0xFFFFEB99 : 0xFFD0D0D0, 0.74F);

			boolean atHome = client.level != null && Home.isHome(client.level.dimension());
			String safeHint = Component.translatable(
					atHome ? "ui.noclip_backrooms.badge.safe_ready" : "ui.noclip_backrooms.badge.safe_note").getString();
			drawSmallTextShadow(g, font, safeHint, bx + 6, by + 11, atHome ? 0xFF9DF2A8 : 0xFFD2C8B8, 0.62F);
		}

		private void drawStatRow(GuiGraphicsExtractor g, Font font, int px, int ry, int rh,
				String name, int value, String bonus, int accentColor, boolean unlocked, int mouseX, int mouseY) {
			int rx = px + 7;
			int rw = unlocked ? PANEL_W - 32 : PANEL_W - 14;
			boolean hovered = mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY < ry + rh;
			int fill = unlocked ? (hovered ? MC_SLOT_HOVER : MC_SLOT_FILL) : 0xFF6C6C6C;
			drawVanillaSlot(g, rx, ry, rw, rh, fill);
			g.fill(rx + 1, ry + 1, rx + 3, ry + rh - 1, unlocked ? accentColor : 0xFF4A4A4A);
			String title = unlocked ? (name + "  " + value) : name;
			drawSmallTextShadow(g, font, title, rx + 6, ry + 2, unlocked ? 0xFFFFFFFF : 0xFFC0C0C0, 0.76F);
			drawSmallTextShadow(g, font, bonus, rx + 6, ry + 11, unlocked ? 0xFFE6ECF5 : 0xFFA6A6A6, 0.65F);
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
