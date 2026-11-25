package dev.noclip.backrooms.core.trade;

import dev.noclip.backrooms.core.economy.Wallet;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A merchant's list: what they sell (price in credits, sometimes a material too) or buy (credits paid), how many of the
 * goods you carry, and your account. Clicking asks the server, which checks everything again; shift-click trades eight.
 */
public final class ShopScreen extends Screen {
	private static final int W = 256, H = 214, ROW = 24, ROWS = 6, LIST_X = 8, LIST_Y = 36, LIST_W = 232;
	private static final int BUTTON_X = 194, BUTTON_W = 36, BUTTON_H = 18;
	private static final int PANEL = 0xFFC6C6C6, DARK = 0xFF373737, LIGHT = 0xFFFFFFFF, SHADE = 0xFF8B8B8B, TEXT = 0xFF404040;
	private final ShopRole role;
	private final List<ShopOffer> offers;
	private int left, top, scroll;

	public ShopScreen(ShopRole role, List<ShopOffer> offers) {
		super(role.title());
		this.role = role;
		this.offers = offers;
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public ShopRole role() {
		return role;
	}

	private Player player() {
		return minecraft == null ? null : minecraft.player;
	}

	private boolean affordable(ShopOffer offer) {
		Player player = player();
		if (player == null) return false;
		if (offer.fromPlayer()) return Shops.count(player.getInventory(), offer.goods()) >= offer.goods().getCount();
		if (Wallet.of(player) < offer.price()) return false;
		return offer.extra().isEmpty() || Shops.count(player.getInventory(), offer.extra()) >= offer.extra().getCount();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		Player player = player();
		bevel(g, left, top, W, H, PANEL, LIGHT, SHADE);
		g.text(font, title, left + 8, top + 8, TEXT, false);
		Component account = Component.translatable("shop.noclip_backrooms.wallet", player == null ? 0 : Wallet.of(player));
		g.text(font, account, left + W - 8 - font.width(account), top + 8, 0xFF7A5C00, false);
		g.text(font, Component.translatable("guild.noclip_backrooms.about." + role.id()), left + 8, top + 20, 0xFF5A5A5A, false);

		int lx = left + LIST_X, ly = top + LIST_Y;
		g.fill(lx - 1, ly - 1, lx + LIST_W + 9, ly + ROW * ROWS + 1, SHADE);
		g.fill(lx, ly, lx + LIST_W + 8, ly + ROW * ROWS, 0xFFB0B0B0);
		if (offers.isEmpty()) g.text(font, Component.translatable("shop.noclip_backrooms.empty"), lx + 6, ly + 8, TEXT, false);
		ItemStack hovered = ItemStack.EMPTY;
		for (int row = 0; row < ROWS && scroll + row < offers.size(); row++) {
			ShopOffer offer = offers.get(scroll + row);
			int ry = ly + row * ROW;
			boolean ok = affordable(offer);
			g.fill(lx + 1, ry + 1, lx + LIST_W - 1, ry + ROW - 1, row % 2 == 0 ? 0xFFC0C0C0 : 0xFFB8B8B8);
			g.item(offer.goods(), lx + 4, ry + 4);
			g.itemDecorations(font, offer.goods(), lx + 4, ry + 4);
			if (inside(mouseX, mouseY, lx + 4, ry + 4, 16, 16)) hovered = offer.goods();
			g.text(font, font.split(offer.goods().getHoverName(), 108).getFirst(), lx + 24, ry + 3, TEXT, false);
			Component detail = offer.fromPlayer()
					? Component.translatable("shop.noclip_backrooms.have", player == null ? 0 : Shops.count(player.getInventory(), offer.goods()))
					: offer.extra().isEmpty() ? Component.empty() : Component.translatable("shop.noclip_backrooms.extra");
			g.text(font, detail, lx + 24, ry + 13, 0xFF606060, false);
			int priceRight = lx + BUTTON_X - 6;
			Component price = Component.translatable(offer.fromPlayer() ? "shop.noclip_backrooms.earn" : "shop.noclip_backrooms.price", offer.price());
			g.text(font, price, priceRight - font.width(price), ry + 8, offer.fromPlayer() ? 0xFF1E6B1E : 0xFF7A5C00, false);
			if (!offer.extra().isEmpty()) {
				int ex = priceRight - font.width(price) - 22;
				g.item(offer.extra(), ex, ry + 4);
				g.itemDecorations(font, offer.extra(), ex, ry + 4);
				if (inside(mouseX, mouseY, ex, ry + 4, 16, 16)) hovered = offer.extra();
			}
			int bx = lx + BUTTON_X, by = ry + 3;
			boolean over = ok && inside(mouseX, mouseY, bx, by, BUTTON_W, BUTTON_H);
			bevel(g, bx, by, BUTTON_W, BUTTON_H, ok ? (over ? 0xFFDADADA : 0xFFC6C6C6) : 0xFF9A9A9A, ok ? LIGHT : 0xFFB0B0B0, ok ? SHADE : 0xFF7A7A7A);
			Component label = Component.translatable(offer.fromPlayer() ? "shop.noclip_backrooms.sell" : "shop.noclip_backrooms.buy");
			g.text(font, label, bx + (BUTTON_W - font.width(label)) / 2, by + 5, ok ? TEXT : 0xFF6A6A6A, false);
		}
		// Scroll bar.
		int max = Math.max(0, offers.size() - ROWS);
		int sx = lx + LIST_W + 1;
		g.fill(sx, ly + 1, sx + 6, ly + ROW * ROWS - 1, 0xFF5A5A5A);
		int thumb = Math.max(12, ROW * ROWS * ROWS / Math.max(ROWS, offers.size()));
		int ty = ly + 1 + (max == 0 ? 0 : (ROW * ROWS - 2 - thumb) * scroll / max);
		g.fill(sx + 1, ty, sx + 5, ty + thumb, max == 0 ? 0xFF8B8B8B : 0xFFE0E0E0);

		g.text(font, Component.translatable("shop.noclip_backrooms.hint"), left + 8, top + H - 28, 0xFF5A5A5A, false);
		g.text(font, Component.translatable("shop.noclip_backrooms.safe"), left + 8, top + H - 16, 0xFF5A5A5A, false);
		if (!hovered.isEmpty()) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, offers.size() - ROWS);
		scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, max);
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int lx = left + LIST_X, ly = top + LIST_Y;
		for (int row = 0; row < ROWS && scroll + row < offers.size(); row++) {
			int index = scroll + row;
			if (!inside(event.x(), event.y(), lx + BUTTON_X, ly + row * ROW + 3, BUTTON_W, BUTTON_H)) continue;
			if (!affordable(offers.get(index))) return true;
			ClientPlayNetworking.send(new ShopTradePayload(role.id(), index, event.hasShiftDown() ? 8 : 1));
			if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Presses offer {@code index} as a click would; for tests. */
	public void press(int index, int times) {
		ClientPlayNetworking.send(new ShopTradePayload(role.id(), index, times));
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int hi, int lo) {
		g.fill(x, y, x + w, y + h, DARK);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
	}
}
