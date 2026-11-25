package dev.noclip.backrooms.crafting;

import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Native Minecraft-styled workstation GUI for all 4 Backrooms crafting stations
 * (Salvage Bench, Workshop Bench, Almond Distiller, Anomaly Altar).
 * Left column lists recipes (scrollable); right panel shows required ingredients with live owned counts
 * and Craft x1 / x8 buttons; bottom shows standard 36 player inventory slots.
 */
public final class BackroomsStationScreen extends AbstractContainerScreen<BackroomsStationMenu> {
	private static final int MC_BLACK = 0xFF000000;
	private static final int MC_HIGHLIGHT = 0xFFFFFFFF;
	private static final int MC_SURFACE = 0xFFC6C6C6;
	private static final int MC_SHADOW = 0xFF555555;
	private static final int MC_SLOT_DARK = 0xFF373737;
	private static final int MC_SLOT_FILL = 0xFF8B8B8B;
	private static final int MC_TEXT = 0xFF404040;

	private static final int LIST_X = 8;
	private static final int LIST_Y = 20;
	private static final int LIST_W = 104;
	private static final int LIST_H = 102;
	private static final int ROW_H = 20;
	private static final int VISIBLE_ROWS = LIST_H / ROW_H;

	private static final int DETAIL_X = 118;
	private static final int DETAIL_Y = 20;
	private static final int DETAIL_W = 122;
	private static final int DETAIL_H = 102;

	private final List<StationRecipe> recipes;
	private int scrollOffset = 0;
	private int selectedIndex = 0;

	public BackroomsStationScreen(BackroomsStationMenu menu, Inventory playerInv, Component title) {
		super(menu, playerInv, title, BackroomsStationMenu.GUI_WIDTH, BackroomsStationMenu.GUI_HEIGHT);
		this.recipes = BackroomsRecipeRegistry.byStation(menu.stationType());
		this.titleLabelX = 8;
		this.titleLabelY = 7;
		this.inventoryLabelX = BackroomsStationMenu.INV_X;
		this.inventoryLabelY = BackroomsStationMenu.INV_Y - 11;
	}

	private StationRecipe selected() {
		if (recipes.isEmpty()) return null;
		selectedIndex = Mth.clamp(selectedIndex, 0, recipes.size() - 1);
		return recipes.get(selectedIndex);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		int x = leftPos;
		int y = topPos;

		drawVanillaWindow(g, x, y, imageWidth, imageHeight);

		// Player inventory recessed slots
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawVanillaSlot(g, x + BackroomsStationMenu.INV_X + col * 18 - 1,
						y + BackroomsStationMenu.INV_Y + row * 18 - 1, 18, 18, MC_SLOT_FILL);
			}
		}
		for (int col = 0; col < 9; col++) {
			drawVanillaSlot(g, x + BackroomsStationMenu.INV_X + col * 18 - 1,
					y + BackroomsStationMenu.INV_Y + 58 - 1, 18, 18, MC_SLOT_FILL);
		}

		// Left recipe list panel
		drawVanillaSlot(g, x + LIST_X, y + LIST_Y, LIST_W, LIST_H, 0xFF6B6B6B);
		Minecraft mc = Minecraft.getInstance();
		int maxScroll = Math.max(0, recipes.size() - VISIBLE_ROWS);
		scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

		for (int i = 0; i < VISIBLE_ROWS; i++) {
			int idx = scrollOffset + i;
			if (idx >= recipes.size()) break;
			StationRecipe recipe = recipes.get(idx);
			int ry = y + LIST_Y + 1 + i * ROW_H;
			int rx = x + LIST_X + 1;
			int rw = LIST_W - 8;
			boolean isSel = idx == selectedIndex;
			boolean hovered = mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + ROW_H;
			boolean craftable = mc.player != null && recipe.canCraft(mc.player);

			int bg = isSel ? 0xFF3E6C3E : (hovered ? 0xFF8C8C8C : (craftable ? 0xFF667C66 : 0xFF7B7B7B));
			g.fill(rx, ry, rx + rw, ry + ROW_H - 1, bg);
			g.fill(rx, ry + ROW_H - 1, rx + rw, ry + ROW_H, 0xFF4C4C4C);

			ItemStack icon = recipe.primaryOutput();
			if (!icon.isEmpty()) {
				g.item(icon, rx + 2, ry + 2);
				String name = icon.getHoverName().getString();
				String trimmed = font.plainSubstrByWidth(name, rw - 22);
				int textColor = craftable ? 0xFFFFFFFF : 0xFFD0D0D0;
				g.text(font, trimmed, rx + 20, ry + 6, textColor, true);
			}
		}

		// Scrollbar track + thumb
		int sbX = x + LIST_X + LIST_W - 6;
		g.fill(sbX, y + LIST_Y + 1, sbX + 5, y + LIST_Y + LIST_H - 1, 0xFF3A3A3A);
		if (recipes.size() > VISIBLE_ROWS) {
			int thumbH = Math.max(12, (LIST_H - 2) * VISIBLE_ROWS / recipes.size());
			int thumbY = y + LIST_Y + 1 + (LIST_H - 2 - thumbH) * scrollOffset / maxScroll;
			g.fill(sbX + 1, thumbY, sbX + 4, thumbY + thumbH, 0xFFC6C6C6);
		}

		// Right recipe detail panel
		drawVanillaSlot(g, x + DETAIL_X, y + DETAIL_Y, DETAIL_W, DETAIL_H, 0xFF7C7C7C);
		StationRecipe sel = selected();
		if (sel != null) {
			ItemStack out = sel.primaryOutput();
			String title = font.plainSubstrByWidth(out.getHoverName().getString(), DETAIL_W - 8);
			g.text(font, title, x + DETAIL_X + 4, y + DETAIL_Y + 4, 0xFFFFFFA0, true);

			// Input grid (up to 6 slots in 3x2, or up to 9 compact slots in 3x3 for Spiderweb recipes)
			boolean compact = sel.inputs().size() > 6;
			int maxInputs = compact ? 9 : 6;
			int step = compact ? 19 : 22;
			int slotSize = compact ? 18 : 20;
			int itemPad = compact ? 1 : 2;
			for (int i = 0; i < sel.inputs().size() && i < maxInputs; i++) {
				StationRecipe.ItemCount req = sel.inputs().get(i);
				int col = i % 3;
				int row = i / 3;
				int sx = x + DETAIL_X + 5 + col * step;
				int sy = y + DETAIL_Y + 16 + row * step;
				int owned = mc.player != null ? StationRecipe.countInInventory(mc.player.getInventory(), req.resolveItem()) : 0;
				boolean enough = (mc.player != null && mc.player.isCreative()) || owned >= req.count();
				drawVanillaSlot(g, sx, sy, slotSize, slotSize, enough ? 0xFF5A8A5A : 0xFF8A5555);
				ItemStack st = req.toStack();
				if (!st.isEmpty()) {
					g.item(st, sx + itemPad, sy + itemPad);
					g.itemDecorations(font, st, sx + itemPad, sy + itemPad, String.valueOf(req.count()));
				}
			}

			// Arrow + Output slot(s)
			g.text(font, "->", x + DETAIL_X + 74, y + DETAIL_Y + 34, 0xFFFFFFFF, true);
			List<ItemStack> outs = sel.allOutputStacks();
			for (int i = 0; i < outs.size() && i < 2; i++) {
				int ox = x + DETAIL_X + 92;
				int oy = y + DETAIL_Y + 17 + i * 22;
				drawVanillaSlot(g, ox, oy, 22, 22, 0xFF6B7B8C);
				g.item(outs.get(i), ox + 3, oy + 3);
				g.itemDecorations(font, outs.get(i), ox + 3, oy + 3);
			}

			// Craft buttons
			boolean canCraft = mc.player != null && sel.canCraft(mc.player);
			drawButton(g, x + DETAIL_X + 6, y + DETAIL_Y + 76, 62, 18,
					Component.translatable("ui.noclip_backrooms.station.craft").getString(), canCraft, mouseX, mouseY);
			drawButton(g, x + DETAIL_X + 72, y + DETAIL_Y + 76, 44, 18,
					Component.translatable("ui.noclip_backrooms.station.craft_batch").getString(), canCraft, mouseX, mouseY);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		// Render hovered ingredient/output item tooltips
		StationRecipe sel = selected();
		if (sel != null) {
			int x = leftPos;
			int y = topPos;
			boolean compact = sel.inputs().size() > 6;
			int maxInputs = compact ? 9 : 6;
			int step = compact ? 19 : 22;
			int slotSize = compact ? 18 : 20;
			for (int i = 0; i < sel.inputs().size() && i < maxInputs; i++) {
				int sx = x + DETAIL_X + 5 + (i % 3) * step;
				int sy = y + DETAIL_Y + 16 + (i / 3) * step;
				if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
					ItemStack st = sel.inputs().get(i).toStack();
					if (!st.isEmpty()) g.setTooltipForNextFrame(font, st, mouseX, mouseY);
					return;
				}
			}
			List<ItemStack> outs = sel.allOutputStacks();
			for (int i = 0; i < outs.size() && i < 2; i++) {
				int ox = x + DETAIL_X + 92;
				int oy = y + DETAIL_Y + 17 + i * 22;
				if (mouseX >= ox && mouseX < ox + 22 && mouseY >= oy && mouseY < oy + 22) {
					g.setTooltipForNextFrame(font, outs.get(i), mouseX, mouseY);
					return;
				}
			}
		}
		extractTooltip(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int maxScroll = Math.max(0, recipes.size() - VISIBLE_ROWS);
		if (maxScroll > 0 && mouseX >= leftPos + LIST_X && mouseX <= leftPos + LIST_X + LIST_W
				&& mouseY >= topPos + LIST_Y && mouseY <= topPos + LIST_Y + LIST_H) {
			scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int x = leftPos;
		int y = topPos;

		// Click recipe row
		if (mx >= x + LIST_X + 1 && mx < x + LIST_X + LIST_W - 7
				&& my >= y + LIST_Y + 1 && my < y + LIST_Y + LIST_H) {
			int row = (int) ((my - (y + LIST_Y + 1)) / ROW_H);
			int idx = scrollOffset + row;
			if (idx >= 0 && idx < recipes.size()) {
				selectedIndex = idx;
				return true;
			}
		}

		// Click Craft x1 / Craft x8 button
		StationRecipe sel = selected();
		if (sel != null && minecraft != null && minecraft.player != null && sel.canCraft(minecraft.player)) {
			int b1x = x + DETAIL_X + 6;
			int b2x = x + DETAIL_X + 72;
			int by = y + DETAIL_Y + 76;
			if (mx >= b1x && mx < b1x + 62 && my >= by && my < by + 18) {
				ClientPlayNetworking.send(new CraftStationPayload(sel.id(), false));
				return true;
			}
			if (mx >= b2x && mx < b2x + 44 && my >= by && my < by + 18) {
				ClientPlayNetworking.send(new CraftStationPayload(sel.id(), true));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean enabled, int mouseX, int mouseY) {
		boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		g.fill(x, y, x + w, y + h, MC_BLACK);
		int fill = !enabled ? 0xFF888888 : (hovered ? 0xFFE6D48A : MC_SURFACE);
		int hi = !enabled ? 0xFF707070 : (hovered ? 0xFFFFF5C2 : MC_HIGHLIGHT);
		int lo = !enabled ? 0xFF707070 : (hovered ? 0xFF947D32 : MC_SHADOW);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
		int tx = x + (w - font.width(label)) / 2;
		int ty = y + (h - 8) / 2;
		g.text(font, label, tx, ty, enabled ? MC_TEXT : 0xFF555555, false);
	}

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
		g.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, MC_SHADOW);
		g.fill(x + w - 3, y + 2, x + w - 1, y + h - 2, MC_SHADOW);
	}

	private static void drawVanillaSlot(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
		g.fill(x, y, x + w - 1, y + 1, MC_SLOT_DARK);
		g.fill(x, y, x + 1, y + h - 1, MC_SLOT_DARK);
		g.fill(x + 1, y + h - 1, x + w, y + h, MC_HIGHLIGHT);
		g.fill(x + w - 1, y + 1, x + w, y + h, MC_HIGHLIGHT);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
	}
}
