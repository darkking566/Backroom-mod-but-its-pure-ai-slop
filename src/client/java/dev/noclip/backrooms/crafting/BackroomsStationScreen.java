package dev.noclip.backrooms.crafting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * True physical-grid Minecraft workstation GUI for all 6 Backrooms crafting tables:
 * - Salvage Bench (1->4 slots)
 * - Workshop Bench (5x5 shaped grid)
 * - Almond Distiller (3x3 shaped grid)
 * - Anomaly Altar (3x3 star-ritual grid)
 * - Primary Spiderweb Crafting Table (7x7 concentric spiderweb grid)
 * - Ultimate Spiderweb Crafting Table (9x9 concentric spiderweb grid)
 *
 * Includes a collapsible Left Categorized Recipe Book with category tabs, a real mouse-draggable
 * scrollbar, ghost-item placement hints inside the crafting grid, one-click auto-fill, and Clear Grid.
 */
public final class BackroomsStationScreen extends AbstractContainerScreen<BackroomsStationMenu> {
	private static final int MC_BLACK = 0xFF000000;
	private static final int MC_HIGHLIGHT = 0xFFFFFFFF;
	private static final int MC_SURFACE = 0xFFC6C6C6;
	private static final int MC_SHADOW = 0xFF555555;
	private static final int MC_SLOT_DARK = 0xFF373737;
	private static final int MC_SLOT_FILL = 0xFF8B8B8B;
	private static final int MC_TEXT = 0xFF404040;

	private static final int BOOK_W = 136;
	private static final int BOOK_GAP = 3;
	private static final int JEI_RESERVE_W = 104;
	private static final int BOOK_ROW_H = 19;
	private static final String[] ALL_CATS = {"all", "parts", "melee", "firearms", "magic", "armor", "gear"};

	private final StationType station;
	private final List<StationRecipe> allStationRecipes;
	private final List<String> availableCats;
	private String activeCategory = "all";
	private List<StationRecipe> filteredRecipes = List.of();

	private boolean initializedOnce = false;
	private boolean bookOpen = true;
	private int scrollOffset = 0;
	private StationRecipe ghostRecipe = null;
	private boolean draggingScrollbar = false;

	public BackroomsStationScreen(BackroomsStationMenu menu, Inventory playerInv, Component title) {
		super(menu, playerInv, title, menu.stationType().guiWidth(), menu.stationType().guiHeight());
		this.station = menu.stationType();
		this.allStationRecipes = BackroomsRecipeRegistry.byStation(station);
		this.availableCats = computeAvailableCategories(allStationRecipes);
		rebuildFiltered();
		this.titleLabelX = 8;
		this.titleLabelY = station == StationType.ULTIMATE_SPIDERWEB ? 4 : 5;
		this.inventoryLabelX = station == StationType.ULTIMATE_SPIDERWEB ? 164 : station.invOriginX();
		this.inventoryLabelY = station == StationType.ULTIMATE_SPIDERWEB
				? (station.invOriginY() - 12)
				: (station.invOriginY() - 10);
	}

	private static List<String> computeAvailableCategories(List<StationRecipe> list) {
		Set<String> present = new LinkedHashSet<>();
		present.add("all");
		for (StationRecipe r : list) {
			present.add(r.category());
		}
		List<String> ordered = new ArrayList<>();
		for (String cat : ALL_CATS) {
			if (present.contains(cat)) ordered.add(cat);
		}
		return ordered;
	}

	private void rebuildFiltered() {
		if ("all".equals(activeCategory)) {
			filteredRecipes = allStationRecipes;
		} else {
			List<StationRecipe> out = new ArrayList<>();
			for (StationRecipe r : allStationRecipes) {
				if (activeCategory.equals(r.category())) out.add(r);
			}
			filteredRecipes = out;
		}
		scrollOffset = 0;
	}

	private boolean canDockBookBeside() {
		return this.width >= BOOK_W + BOOK_GAP + this.imageWidth + 6;
	}

	private boolean isOverlayBook() {
		return bookOpen && !canDockBookBeside();
	}

	private void updateLayout() {
		this.topPos = Math.max(2, (this.height - this.imageHeight) / 2);
		if (bookOpen && canDockBookBeside()) {
			int combinedW = BOOK_W + BOOK_GAP + this.imageWidth;
			int centeredGroupLeft = (this.width - combinedW) / 2;
			int jeiFriendlyLeft = this.width - combinedW - JEI_RESERVE_W;
			int groupLeft = Math.max(4, Math.min(centeredGroupLeft, jeiFriendlyLeft));
			this.leftPos = groupLeft + BOOK_W + BOOK_GAP;
		} else {
			this.leftPos = Math.max(2, (this.width - this.imageWidth) / 2);
		}
	}

	private int bookW() {
		return isOverlayBook() ? Math.min(BOOK_W, Math.max(108, this.width - 8)) : BOOK_W;
	}

	private int bookX() {
		if (isOverlayBook()) {
			return Math.max(2, Math.min(this.leftPos + 4, this.width - bookW() - 2));
		}
		return this.leftPos - BOOK_W - BOOK_GAP;
	}

	private int bookY() {
		return isOverlayBook() ? Math.max(2, this.topPos + 16) : this.topPos;
	}

	private int bookH() {
		if (isOverlayBook()) {
			return Math.max(96, Math.min(this.imageHeight - 18, this.height - bookY() - 2));
		}
		return this.imageHeight;
	}

	private int bookListY() {
		return bookY() + 36;
	}

	private int bookListH() {
		return Math.max(38, bookH() - 62);
	}

	private int visibleRows() {
		return Math.max(2, bookListH() / BOOK_ROW_H);
	}

	private boolean isPointInBook(double mx, double my) {
		return bookOpen && mx >= bookX() && mx < bookX() + bookW()
				&& my >= bookY() && my < bookY() + bookH();
	}

	/** Reported to JEI so the docked Left Categorized Recipe Book never overlaps JEI panels. */
	public List<Rect2i> extraGuiAreas() {
		if (!bookOpen || isOverlayBook()) return List.of();
		return List.of(new Rect2i(bookX(), bookY(), bookW(), bookH()));
	}

	@Override
	protected void init() {
		super.init();
		if (!initializedOnce) {
			bookOpen = canDockBookBeside();
			initializedOnce = true;
		}
		updateLayout();
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		if (isPointInBook(mouseX, mouseY)) {
			return false;
		}
		return super.hasClickedOutside(mouseX, mouseY, left, top);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		int x = leftPos;
		int y = topPos;

		// 1. Main Workstation Window
		drawVanillaWindow(g, x, y, imageWidth, imageHeight);

		// 2. Decorative Spiderweb / Altar / Distiller Grid Backdrop + Physical Input Slots
		int gw = station.gridWidth();
		int gh = station.gridHeight();
		int gx0 = x + station.gridOriginX();
		int gy0 = y + station.gridOriginY();
		int step = station.cellStep();

		if (station.isSpiderweb()) {
			// Concentric web lines radiating from center core across 7x7 / 9x9 grid
			int gridPxW = gw * step;
			int gridPxH = gh * step;
			int cx = gx0 + gridPxW / 2;
			int cy = gy0 + gridPxH / 2;
			g.fill(gx0 - 2, gy0 - 2, gx0 + gridPxW + 2, gy0 + gridPxH + 2, 0xFF282032);
			g.fill(gx0 - 1, cy - 1, gx0 + gridPxW + 1, cy + 1, 0xFF5D3A8A);
			g.fill(cx - 1, gy0 - 1, cx + 1, gy0 + gridPxH + 1, 0xFF5D3A8A);
		}

		int halfW = gw / 2;
		int halfH = gh / 2;
		for (int row = 0; row < gh; row++) {
			for (int col = 0; col < gw; col++) {
				int sx = gx0 + col * step - 1;
				int sy = gy0 + row * step - 1;
				int fill = slotFillForCell(col, row, halfW, halfH);
				int sSize = step == 16 ? 16 : 18;
				drawVanillaSlot(g, sx, sy, sSize, sSize, fill);
			}
		}

		// 3. Arrow + Output Slot(s)
		int ox = x + station.outputOriginX();
		int oy = y + station.outputOriginY();
		int arrowX = (gx0 + gw * step + ox) / 2 - 7;
		int arrowY = oy + (station.outputSlotCount() == 1 ? 4 : 14);
		g.text(font, "->", arrowX, arrowY, MC_TEXT, false);

		int outCount = station.outputSlotCount();
		for (int i = 0; i < outCount; i++) {
			int sx = outCount == 1 ? (ox - 4) : (ox + (i % 2) * 20 - 1);
			int sy = outCount == 1 ? (oy - 4) : (oy + (i / 2) * 20 - 1);
			int sz = outCount == 1 ? 24 : 18;
			drawVanillaSlot(g, sx, sy, sz, sz, outCount == 1 ? 0xFF7B8594 : MC_SLOT_FILL);
		}

		// 4. Top-Right Buttons: [配方書] toggle & [清空] clear grid
		int bookBtnX = x + imageWidth - 44;
		int bookBtnY = y + 3;
		drawButton(g, bookBtnX, bookBtnY, 38, 12,
				Component.translatable("ui.noclip_backrooms.station.book").getString(), true, mouseX, mouseY);

		int clearBtnX = Math.max(gx0 + gw * step + 4, ox - 6);
		int clearBtnY = oy + (outCount == 1 ? 24 : 44);
		if (clearBtnY + 13 < y + station.invOriginY() - 10) {
			drawButton(g, clearBtnX, clearBtnY, 30, 13,
					Component.translatable("ui.noclip_backrooms.station.clear").getString(), true, mouseX, mouseY);
		}

		// 5. Player Inventory Recessed Slots
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawVanillaSlot(g, x + station.invOriginX() + col * 18 - 1,
						y + station.invOriginY() + row * 18 - 1, 18, 18, MC_SLOT_FILL);
			}
		}
		for (int col = 0; col < 9; col++) {
			drawVanillaSlot(g, x + station.invOriginX() + col * 18 - 1,
					y + station.invOriginY() + 58 - 1, 18, 18, MC_SLOT_FILL);
		}

		// 6. Ghost Recipe Hints inside empty physical grid slots!
		drawGhostOverlay(g);

		// 7. Collapsible Left Categorized Recipe Book (docked beside main window when space allows)
		if (bookOpen && !isOverlayBook()) {
			drawRecipeBook(g, mouseX, mouseY);
		}
	}

	private int slotFillForCell(int col, int row, int halfW, int halfH) {
		if (station == StationType.ALTAR && col == 1 && row == 1) {
			return 0xFF7D5C99;
		}
		if (station == StationType.SPIDERWEB) {
			int dist = Math.max(Math.abs(col - halfW), Math.abs(row - halfH));
			if (dist == 0) return 0xFF8E63B8; // Center Rare Core
			if (dist == 1) return 0xFF667F96; // Inner Ring Parts
			if (dist == 2) return 0xFF778590; // Mid Ring
			return MC_SLOT_FILL;              // Outer Ring Vanilla Materials
		}
		if (station == StationType.ULTIMATE_SPIDERWEB) {
			int dist = Math.max(Math.abs(col - halfW), Math.abs(row - halfH));
			if (dist == 0) return 0xFFA264CC; // Center Boss Core
			if (dist <= 2) return 0xFF637E9C; // Dual Inner Rings
			if (dist == 3) return 0xFF78828E;
			return MC_SLOT_FILL;              // Outer 9x9 Anchor Ring
		}
		return MC_SLOT_FILL;
	}

	private void drawGhostOverlay(GuiGraphicsExtractor g) {
		if (ghostRecipe == null || ghostRecipe.station() != station) return;
		Minecraft mc = Minecraft.getInstance();
		int ox = ghostRecipe.centeredOffsetX();
		int oy = ghostRecipe.centeredOffsetY();
		int gx0 = leftPos + station.gridOriginX();
		int gy0 = topPos + station.gridOriginY();
		int step = station.cellStep();
		int gw = station.gridWidth();

		for (int py = 0; py < ghostRecipe.patternHeight(); py++) {
			for (int px = 0; px < ghostRecipe.patternWidth(); px++) {
				StationRecipe.ItemCount req = ghostRecipe.cellAt(px, py);
				if (req == null) continue;
				int slotIdx = (oy + py) * gw + (ox + px);
				if (!menu.inputGrid().getItem(slotIdx).isEmpty()) continue;
				ItemStack st = req.toStack();
				if (st.isEmpty()) continue;

				int sx = gx0 + (ox + px) * step;
				int sy = gy0 + (oy + py) * step;
				boolean owned = mc.player != null && (mc.player.isCreative()
						|| StationRecipe.countInInventory(mc.player.getInventory(), req.resolveItem()) >= req.count());
				g.fill(sx, sy, sx + 16, sy + 16, owned ? 0x5544CC66 : 0x55CC4444);
				g.item(st, sx, sy);
				// Translucent veil over ghost item so player clearly distinguishes real vs ghost
				g.fill(sx, sy, sx + 16, sy + 16, 0x66C6C6C6);
			}
		}
	}

	private void drawRecipeBook(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int bx = bookX();
		int by = bookY();
		int bw = bookW();
		int bh = bookH();
		drawVanillaWindow(g, bx, by, bw, bh);
		g.text(font, Component.translatable("ui.noclip_backrooms.station.book_title"), bx + 7, by + 6, MC_TEXT, false);
		if (isOverlayBook()) {
			drawButton(g, bx + bw - 18, by + 4, 12, 11, "x", true, mouseX, mouseY);
		}

		// Category Tabs row
		int tabY = by + 18;
		int tabW = Math.max(14, (bw - 14) / Math.max(1, availableCats.size()));
		for (int i = 0; i < availableCats.size(); i++) {
			String cat = availableCats.get(i);
			int tx = bx + 7 + i * tabW;
			boolean active = cat.equals(activeCategory);
			boolean hovered = mouseX >= tx && mouseX < tx + tabW - 1 && mouseY >= tabY && mouseY < tabY + 15;
			int bg = active ? 0xFF4A784A : (hovered ? 0xFFA6A6A6 : 0xFF888888);
			drawVanillaSlot(g, tx, tabY, tabW - 1, 15, bg);
			String shortLbl = Component.translatable("ui.noclip_backrooms.cat." + cat).getString();
			String trimmed = font.plainSubstrByWidth(shortLbl, tabW - 3);
			int lx = tx + (tabW - 1 - font.width(trimmed)) / 2;
			g.text(font, trimmed, lx, tabY + 4, active ? 0xFFFFFF88 : 0xFFFFFFFF, true);
		}

		// Scrollable Recipe List
		int listX = bx + 7;
		int listY = bookListY();
		int listW = bw - 14;
		int listH = bookListH();
		int vis = visibleRows();
		int maxScroll = Math.max(0, filteredRecipes.size() - vis);
		scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

		drawVanillaSlot(g, listX, listY, listW, listH, 0xFF5D5D5D);
		Minecraft mc = Minecraft.getInstance();
		int rowW = listW - 10;
		for (int i = 0; i < vis; i++) {
			int idx = scrollOffset + i;
			if (idx >= filteredRecipes.size()) break;
			StationRecipe r = filteredRecipes.get(idx);
			int ry = listY + 1 + i * BOOK_ROW_H;
			int rx = listX + 1;
			boolean sel = r == ghostRecipe;
			boolean hov = mouseX >= rx && mouseX < rx + rowW && mouseY >= ry && mouseY < ry + BOOK_ROW_H;
			boolean craftable = mc.player != null && r.canCraftFromInventory(mc.player);

			int bg = sel ? 0xFF3E6C3E : (hov ? 0xFF8E8E8E : (craftable ? 0xFF5A745A : 0xFF727272));
			g.fill(rx, ry, rx + rowW, ry + BOOK_ROW_H - 1, bg);
			g.fill(rx, ry + BOOK_ROW_H - 1, rx + rowW, ry + BOOK_ROW_H, 0xFF464646);

			ItemStack icon = r.primaryOutput();
			if (!icon.isEmpty()) {
				g.item(icon, rx + 1, ry + 1);
				String name = icon.getHoverName().getString();
				String trimmed = font.plainSubstrByWidth(name, rowW - 21);
				g.text(font, trimmed, rx + 19, ry + 5, craftable ? 0xFFFFFFFF : 0xFFD2D2D2, true);
			}
		}

		// Real Mouse-Draggable Scrollbar Track & Thumb!
		int sbX = listX + listW - 8;
		int sbY = listY + 1;
		int sbH = listH - 2;
		g.fill(sbX, sbY, sbX + 7, sbY + sbH, 0xFF303030);
		if (filteredRecipes.size() > vis) {
			int thumbH = Math.max(14, sbH * vis / filteredRecipes.size());
			int thumbY = sbY + (sbH - thumbH) * scrollOffset / maxScroll;
			boolean sbHover = draggingScrollbar || (mouseX >= sbX && mouseX <= sbX + 7 && mouseY >= sbY && mouseY <= sbY + sbH);
			int thumbCol = sbHover ? 0xFFE8E8E8 : 0xFFB8B8B8;
			g.fill(sbX + 1, thumbY, sbX + 6, thumbY + thumbH, thumbCol);
			g.fill(sbX + 1, thumbY, sbX + 6, thumbY + 1, 0xFFFFFFFF);
			g.fill(sbX + 1, thumbY + thumbH - 1, sbX + 6, thumbY + thumbH, 0xFF666666);
		} else {
			g.fill(sbX + 1, sbY + 1, sbX + 6, sbY + sbH - 1, 0xFF707070);
		}

		// Bottom Auto-Fill Buttons for selected ghost recipe
		int footY = by + bh - 22;
		int innerW = bw - 14;
		int fillW = (innerW - 3) * 60 / 100;
		int maxW = innerW - 3 - fillW;
		boolean canFill = ghostRecipe != null && mc.player != null && ghostRecipe.canCraftFromInventory(mc.player);
		drawButton(g, bx + 7, footY, fillW, 15,
				Component.translatable("ui.noclip_backrooms.station.fill_grid").getString(), canFill, mouseX, mouseY);
		drawButton(g, bx + 7 + fillW + 3, footY, maxW, 15,
				Component.translatable("ui.noclip_backrooms.station.fill_max").getString(), canFill, mouseX, mouseY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);

		// Narrow-screen fallback: draw recipe book as an interactive top drawer above the crafting slots
		if (isOverlayBook()) {
			g.fill(leftPos, topPos + 15, leftPos + imageWidth, topPos + imageHeight, 0x77000000);
			drawRecipeBook(g, mouseX, mouseY);
		}

		// Hover tooltip on Category Tabs (both docked and overlay drawer modes)
		if (bookOpen) {
			int bx = bookX();
			int bw = bookW();
			int tabY = bookY() + 18;
			int tabW = Math.max(14, (bw - 14) / Math.max(1, availableCats.size()));
			if (mouseY >= tabY && mouseY < tabY + 15) {
				for (int i = 0; i < availableCats.size(); i++) {
					int tx = bx + 7 + i * tabW;
					if (mouseX >= tx && mouseX < tx + tabW - 1) {
						g.setTooltipForNextFrame(font, Component.translatable("ui.noclip_backrooms.cat_full." + availableCats.get(i)), mouseX, mouseY);
						return;
					}
				}
			}
			// Suppress underlying slot tooltips while cursor is over the overlay drawer
			if (isOverlayBook() && isPointInBook(mouseX, mouseY)) {
				return;
			}
		}

		// Hover tooltip for ghost items in empty grid cells
		if (ghostRecipe != null && ghostRecipe.station() == station && (hoveredSlot == null || !hoveredSlot.hasItem())) {
			int ox = ghostRecipe.centeredOffsetX();
			int oy = ghostRecipe.centeredOffsetY();
			int gx0 = leftPos + station.gridOriginX();
			int gy0 = topPos + station.gridOriginY();
			int step = station.cellStep();
			int gw = station.gridWidth();
			for (int py = 0; py < ghostRecipe.patternHeight(); py++) {
				for (int px = 0; px < ghostRecipe.patternWidth(); px++) {
					StationRecipe.ItemCount req = ghostRecipe.cellAt(px, py);
					if (req == null) continue;
					if (!menu.inputGrid().getItem((oy + py) * gw + (ox + px)).isEmpty()) continue;
					int sx = gx0 + (ox + px) * step;
					int sy = gy0 + (oy + py) * step;
					if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
						ItemStack st = req.toStack();
						if (!st.isEmpty()) {
							g.setTooltipForNextFrame(font, st, mouseX, mouseY);
							return;
						}
					}
				}
			}
		}

		extractTooltip(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (isPointInBook(mouseX, mouseY)) {
			int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows());
			if (maxScroll > 0) {
				scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int x = leftPos;
		int y = topPos;

		// 1. Toggle Recipe Book button (shifts main grid right when docked, or opens overlay drawer on narrow screens)
		int bookBtnX = x + imageWidth - 44;
		int bookBtnY = y + 3;
		if (mx >= bookBtnX && mx < bookBtnX + 38 && my >= bookBtnY && my < bookBtnY + 12) {
			bookOpen = !bookOpen;
			updateLayout();
			return true;
		}

		// 2. Categorized Recipe Book interactions (checked BEFORE main window so overlay drawer intercepts cleanly)
		if (isPointInBook(mx, my)) {
			int bx = bookX();
			int by = bookY();
			int bw = bookW();
			int bh = bookH();

			// Overlay drawer [x] close button
			if (isOverlayBook() && mx >= bx + bw - 18 && mx < bx + bw - 6 && my >= by + 4 && my < by + 15) {
				bookOpen = false;
				updateLayout();
				return true;
			}

			// Category Tabs click
			int tabY = by + 18;
			int tabW = Math.max(14, (bw - 14) / Math.max(1, availableCats.size()));
			if (my >= tabY && my < tabY + 15 && mx >= bx + 7 && mx < bx + bw - 7) {
				int idx = (int) ((mx - (bx + 7)) / tabW);
				if (idx >= 0 && idx < availableCats.size()) {
					activeCategory = availableCats.get(idx);
					rebuildFiltered();
					return true;
				}
			}

			int listX = bx + 7;
			int listY = bookListY();
			int listW = bw - 14;
			int listH = bookListH();
			int sbX = listX + listW - 8;

			// Draggable Scrollbar track / thumb click
			if (mx >= sbX - 1 && mx <= sbX + 8 && my >= listY && my <= listY + listH) {
				draggingScrollbar = true;
				updateScrollFromMouseY(my);
				return true;
			}

			// Recipe list row click (click = show ghost shape; double-click = auto-fill immediately!)
			if (mx >= listX + 1 && mx < sbX && my >= listY + 1 && my < listY + listH) {
				int row = (int) ((my - (listY + 1)) / BOOK_ROW_H);
				int idx = scrollOffset + row;
				if (idx >= 0 && idx < filteredRecipes.size()) {
					ghostRecipe = filteredRecipes.get(idx);
					if (doubleClick || hasShiftDownCompat()) {
						ClientPlayNetworking.send(new CraftStationPayload(ghostRecipe.id(), hasShiftDownCompat()));
						if (isOverlayBook()) {
							bookOpen = false;
							updateLayout();
						}
					}
					return true;
				}
			}

			// Bottom [自動填入] & [填滿] buttons
			int footY = by + bh - 22;
			int innerW = bw - 14;
			int fillW = (innerW - 3) * 60 / 100;
			if (ghostRecipe != null && my >= footY && my < footY + 15) {
				if (mx >= bx + 7 && mx < bx + 7 + fillW) {
					ClientPlayNetworking.send(new CraftStationPayload(ghostRecipe.id(), false));
					if (isOverlayBook()) {
						bookOpen = false;
						updateLayout();
					}
					return true;
				}
				if (mx >= bx + 7 + fillW + 3 && mx < bx + bw - 7) {
					ClientPlayNetworking.send(new CraftStationPayload(ghostRecipe.id(), true));
					if (isOverlayBook()) {
						bookOpen = false;
						updateLayout();
					}
					return true;
				}
			}
			return true;
		}

		// 3. Clear Grid button
		int gx0 = x + station.gridOriginX();
		int ox = x + station.outputOriginX();
		int oy = y + station.outputOriginY();
		int clearBtnX = Math.max(gx0 + station.gridWidth() * station.cellStep() + 4, ox - 6);
		int clearBtnY = oy + (station.outputSlotCount() == 1 ? 24 : 44);
		if (clearBtnY + 13 < y + station.invOriginY() - 10
				&& mx >= clearBtnX && mx < clearBtnX + 30 && my >= clearBtnY && my < clearBtnY + 13) {
			ClientPlayNetworking.send(new CraftStationPayload(BackroomsCrafting.CLEAR_GRID_ID, false));
			ghostRecipe = null;
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (draggingScrollbar) {
			updateScrollFromMouseY(event.y());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingScrollbar) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	private void updateScrollFromMouseY(double mouseY) {
		int maxScroll = Math.max(0, filteredRecipes.size() - visibleRows());
		if (maxScroll <= 0) {
			scrollOffset = 0;
			return;
		}
		int sbY = bookListY() + 1;
		int sbH = Math.max(1, bookListH() - 2);
		float ratio = (float) Mth.clamp((mouseY - sbY) / sbH, 0.0, 1.0);
		scrollOffset = Math.round(ratio * maxScroll);
	}

	private static boolean hasShiftDownCompat() {
		Minecraft mc = Minecraft.getInstance();
		return mc.hasShiftDown();
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
		String trimmed = font.plainSubstrByWidth(label, w - 4);
		int tx = x + (w - font.width(trimmed)) / 2;
		int ty = y + (h - 8) / 2;
		g.text(font, trimmed, tx, ty, enabled ? MC_TEXT : 0xFF555555, false);
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
