package dev.noclip.backrooms.core.field;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** A photo held up (one, or the album page by page): the print with its white border and the orange date stamp. */
public final class PhotoScreen extends Screen {
	private static final int BORDER = 8, FOOT = 26;
	private final boolean album;
	private final List<ItemStack> photos;
	private int page;

	PhotoScreen(boolean album, List<ItemStack> photos) {
		super(Component.translatable("photo.noclip_backrooms.title"));
		this.album = album;
		this.photos = photos;
		this.page = Math.max(0, photos.size() - 1);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		if (!album) return;
		int y = Math.min(height - 24, height / 2 + printHeight() / 2 + 6), x = width / 2;
		addRenderableWidget(Button.builder(Component.translatable("photo.noclip_backrooms.prev"), b -> turn(-1)).bounds(x - 112, y, 70, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("photo.noclip_backrooms.take"), b -> {
			if (!photos.isEmpty()) ClientPlayNetworking.send(new PhotoPayloads.Take(page));
		}).bounds(x - 35, y, 70, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("photo.noclip_backrooms.next"), b -> turn(1)).bounds(x + 42, y, 70, 20).build());
	}

	private void turn(int by) {
		if (!photos.isEmpty()) page = Math.floorMod(page + by, photos.size());
	}

	/** The picture is drawn at a whole multiple of its size, as large as fits. */
	private int scale() {
		return Math.max(1, Math.min((width - 40) / PhotoImage.WIDTH, (height - 90) / PhotoImage.HEIGHT));
	}

	private int printHeight() {
		return PhotoImage.HEIGHT * scale() + BORDER + FOOT;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (photos.isEmpty()) {
			g.centeredText(font, Component.translatable("photo.noclip_backrooms.empty"), width / 2, height / 2, 0xFFD8E0E0);
			return;
		}
		ItemStack photo = photos.get(page);
		int w = PhotoImage.WIDTH * scale(), h = PhotoImage.HEIGHT * scale();
		int x = (width - w) / 2, y = Math.max(BORDER + 4, (height - printHeight()) / 2 + BORDER);
		g.fill(x - BORDER, y - BORDER, x + w + BORDER, y + h + FOOT, 0xFFEDE8DA);
		g.fill(x, y, x + w, y + h, 0xFF14120E);
		UUID id = Photos.id(photo);
		Identifier texture = id == null ? null : PhotoClient.texture(id);
		if (texture != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, w, h);
		} else {
			g.centeredText(font, Component.translatable("photo.noclip_backrooms.no_image"), width / 2, y + h / 2 - 4, 0xFF8A8270);
		}
		CustomData data = photo.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		if (texture != null && tag.getIntOr("day", 0) > 0) {
			String stamp = "DAY " + tag.getIntOr("day", 0) + "  L-" + tag.getStringOr("level", "?");
			g.text(font, stamp, x + w - font.width(stamp) - 4, y + h - 12, 0xFFFF9A2E, true);
		}
		g.centeredText(font, photo.getHoverName().copy().withColor(0x3A342A), width / 2, y + h + 4, 0xFF3A342A);
		if (album) {
			g.centeredText(font, Component.literal(page + 1 + " / " + photos.size()), width / 2, y + h + 15, 0xFF8A8270);
		}
	}

	@Override
	public void added() {
		super.added();
		photos.forEach(photo -> {
			UUID id = Photos.id(photo);
			if (id != null) PhotoClient.request(id);
		});
	}

	@Override
	public void onClose() {
		super.onClose();
		PhotoClient.forgetAll(minecraft);
	}

	/** For tests: whether the page shown has its picture. */
	public boolean showingPicture() {
		return !photos.isEmpty() && Photos.id(photos.get(page)) != null && PhotoClient.texture(Photos.id(photos.get(page))) != null;
	}
}
