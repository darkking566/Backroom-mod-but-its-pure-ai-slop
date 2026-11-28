package dev.noclip.backrooms.core.field;

import dev.noclip.backrooms.BackroomsMod;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The album: an open scrapbook with the prints taped in, eight to a spread; click one to hold it up close. A single
 * photo in hand is shown held up straight away.
 */
public final class PhotoScreen extends Screen {
	private static final Identifier BOOK = BackroomsMod.id("textures/gui/photo_album.png");
	private static final int BOOK_W = 320, BOOK_H = 200, PER_SPREAD = 8;
	/** A print in the book: the picture and its white border (wider at the foot). */
	private static final int THUMB_W = 60, THUMB_H = 45, PRINT_W = THUMB_W + 6, PRINT_H = THUMB_H + 12;
	private static final int[] COLUMNS = {12, 84, 170, 242}, ROWS = {28, 108};
	private static final int BORDER = 8, FOOT = 26;
	private final boolean album;
	private final List<ItemStack> photos;
	/** The photo held up close, or -1 for the open book. */
	private int shown;
	private int spread;

	PhotoScreen(boolean album, List<ItemStack> photos) {
		super(Component.translatable("photo.noclip_backrooms.title"));
		this.album = album;
		this.photos = photos;
		this.shown = album ? -1 : 0;
		this.spread = Math.max(0, photos.size() - 1) / PER_SPREAD;
	}

	/** After a photo is taken out the book stays open where it was. */
	void openAt(PhotoScreen before) {
		spread = Math.min(before.spread, spread);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int bookLeft() {
		return (width - BOOK_W) / 2;
	}

	private int bookTop() {
		return Math.max(2, (height - BOOK_H - 24) / 2);
	}

	@Override
	protected void init() {
		if (!album) return;
		int x = width / 2;
		if (shown < 0) {
			int y = bookTop() + BOOK_H + 3;
			if (spreads() > 1) {
				button("photo.noclip_backrooms.prev_page", x - 112, y, () -> spread = Math.floorMod(spread - 1, spreads()));
				button("photo.noclip_backrooms.next_page", x + 42, y, () -> spread = Math.floorMod(spread + 1, spreads()));
			}
		} else {
			int y = Math.min(height - 22, height / 2 + printHeight() / 2 + 4);
			button("photo.noclip_backrooms.prev", x - 150, y, () -> shown = Math.floorMod(shown - 1, photos.size()));
			button("photo.noclip_backrooms.take", x - 74, y, () -> ClientPlayNetworking.send(new PhotoPayloads.Take(shown)));
			button("photo.noclip_backrooms.back", x + 4, y, this::onClose);
			button("photo.noclip_backrooms.next", x + 80, y, () -> shown = Math.floorMod(shown + 1, photos.size()));
		}
	}

	private void button(String key, int x, int y, Runnable press) {
		addRenderableWidget(Button.builder(Component.translatable(key), b -> press.run()).bounds(x, y, 70, 20).build());
	}

	private int spreads() {
		return Math.max(1, (photos.size() + PER_SPREAD - 1) / PER_SPREAD);
	}

	/** Where the print in slot {@code slot} of the spread sits: left, top. */
	private int[] slot(int slot) {
		// Two across and two down on the left page, then the right page.
		int page = slot / 4, column = page * 2 + slot % 2, row = slot % 4 / 2;
		return new int[] {bookLeft() + COLUMNS[column], bookTop() + ROWS[row]};
	}

	/** The photo under the mouse in the open book, or -1. */
	private int at(double x, double y) {
		if (shown >= 0) return -1;
		for (int i = spread * PER_SPREAD; i < Math.min(photos.size(), (spread + 1) * PER_SPREAD); i++) {
			int[] s = slot(i % PER_SPREAD);
			if (x >= s[0] && x < s[0] + PRINT_W && y >= s[1] && y < s[1] + PRINT_H) return i;
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int clicked = at(event.x(), event.y());
		if (clicked >= 0) {
			shown = clicked;
			rebuildWidgets();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Closing a photo held up from the album goes back to the book. */
	@Override
	public void onClose() {
		if (album && shown >= 0) {
			spread = shown / PER_SPREAD;
			shown = -1;
			rebuildWidgets();
			return;
		}
		super.onClose();
		PhotoClient.forgetAll(minecraft);
	}

	/** The picture held up is drawn at a whole multiple of its size, as large as fits. */
	private int scale() {
		return Math.max(1, Math.min((width - 40) / PhotoImage.WIDTH, (height - 70) / PhotoImage.HEIGHT));
	}

	private int printHeight() {
		return PhotoImage.HEIGHT * scale() + BORDER + FOOT;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (shown < 0) book(g, mouseX, mouseY);
		else if (shown < photos.size()) heldUp(g);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	private void book(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int left = bookLeft(), top = bookTop();
		g.blit(RenderPipelines.GUI_TEXTURED, BOOK, left, top, 0, 0, BOOK_W, BOOK_H, BOOK_W, BOOK_H);
		g.text(font, Component.translatable("item.noclip_backrooms.photo_album"), left + 14, top + 12, 0xFF4A3420, false);
		String count = photos.size() + " / " + Photos.ALBUM_SIZE + (spreads() > 1 ? "   P." + (spread + 1) + "/" + spreads() : "");
		g.text(font, count, left + BOOK_W - 14 - font.width(count), top + 12, 0xFF4A3420, false);
		if (photos.isEmpty()) {
			g.centeredText(font, Component.translatable("photo.noclip_backrooms.empty"), left + BOOK_W / 4, top + 96, 0xFF6A5438);
		}
		int hovered = at(mouseX, mouseY);
		for (int n = 0; n < PER_SPREAD; n++) {
			int i = spread * PER_SPREAD + n;
			int[] s = slot(n);
			if (i >= photos.size()) {
				corners(g, s[0], s[1]);
				continue;
			}
			// Stuck in by hand: each a little askew, the one under the mouse lifted straight.
			float tilt = i == hovered ? 0 : ((i * 37 % 9) - 4) * 0.012F;
			g.pose().pushMatrix();
			g.pose().translate(s[0] + PRINT_W / 2F, s[1] + PRINT_H / 2F - (i == hovered ? 2 : 0));
			g.pose().rotate(tilt);
			int x = -PRINT_W / 2, y = -PRINT_H / 2;
			g.fill(x + 2, y + 2, x + PRINT_W + 2, y + PRINT_H + 2, 0x50201408);
			g.fill(x, y, x + PRINT_W, y + PRINT_H, i == hovered ? 0xFFFFFBF0 : 0xFFEDE8DA);
			picture(g, photos.get(i), x + 3, y + 3, THUMB_W, THUMB_H);
			g.fill(-11, y - 3, 11, y + 4, 0xA8D9C58C); // tape
			g.pose().popMatrix();
		}
		if (hovered >= 0) g.setTooltipForNextFrame(font, photos.get(hovered).getHoverName(), mouseX, mouseY);
	}

	/** The paper corners of an empty place. */
	private static void corners(GuiGraphicsExtractor g, int x, int y) {
		int colour = 0x607A6038, r = x + PRINT_W, b = y + PRINT_H, n = 7;
		g.fill(x, y, x + n, y + 1, colour);
		g.fill(x, y, x + 1, y + n, colour);
		g.fill(r - n, y, r, y + 1, colour);
		g.fill(r - 1, y, r, y + n, colour);
		g.fill(x, b - 1, x + n, b, colour);
		g.fill(x, b - n, x + 1, b, colour);
		g.fill(r - n, b - 1, r, b, colour);
		g.fill(r - 1, b - n, r, b, colour);
	}

	private boolean picture(GuiGraphicsExtractor g, ItemStack photo, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFF14120E);
		UUID id = Photos.id(photo);
		Identifier texture = id == null ? null : PhotoClient.texture(id);
		if (texture == null) return false;
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, w, h);
		return true;
	}

	private void heldUp(GuiGraphicsExtractor g) {
		ItemStack photo = photos.get(shown);
		int w = PhotoImage.WIDTH * scale(), h = PhotoImage.HEIGHT * scale();
		int x = (width - w) / 2, y = Math.max(BORDER + 2, (height - printHeight()) / 2 + BORDER - (album ? 8 : 0));
		g.fill(x - BORDER, y - BORDER, x + w + BORDER, y + h + FOOT, 0xFFEDE8DA);
		boolean developed = picture(g, photo, x, y, w, h);
		CustomData data = photo.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		int day = tag.getIntOr("day", 0);
		if (!developed) {
			g.centeredText(font, Component.translatable("photo.noclip_backrooms.no_image"), width / 2, y + h / 2 - 4, 0xFF8A8270);
		} else if (day > 0) {
			String stamp = "DAY " + day + "  L-" + tag.getStringOr("level", "?");
			g.text(font, stamp, x + w - font.width(stamp) - 4, y + h - 12, 0xFFFF9A2E, true);
		}
		Component name = photo.getHoverName();
		g.text(font, name, (width - font.width(name)) / 2, y + h + 4, 0xFF3A342A, false);
		Component detail = day > 0 ? Component.translatable("photo.noclip_backrooms.taken_day", day) : Component.empty();
		if (album) detail = detail.copy().append("  " + (shown + 1) + " / " + photos.size());
		g.text(font, detail, (width - font.width(detail)) / 2, y + h + 15, 0xFF8A8270, false);
	}

	@Override
	public void added() {
		super.added();
		photos.forEach(photo -> {
			UUID id = Photos.id(photo);
			if (id != null) PhotoClient.request(id);
		});
	}

	/** For tests: whether the picture held up, or every one on the open spread, has arrived. */
	public boolean showingPicture() {
		if (photos.isEmpty()) return false;
		int from = shown >= 0 ? shown : spread * PER_SPREAD, to = shown >= 0 ? shown + 1 : Math.min(photos.size(), from + PER_SPREAD);
		for (int i = from; i < to; i++) {
			UUID id = Photos.id(photos.get(i));
			if (id == null || PhotoClient.texture(id) == null) return false;
		}
		return true;
	}

	/** For tests: the middle of the first print on the open spread, in screen coordinates. */
	public int[] firstPrint() {
		int[] s = slot(0);
		return new int[] {s[0] + PRINT_W / 2, s[1] + PRINT_H / 2};
	}

	/** For tests: whether a photo is held up (rather than the book lying open). */
	public boolean holdingUp() {
		return shown >= 0;
	}
}
