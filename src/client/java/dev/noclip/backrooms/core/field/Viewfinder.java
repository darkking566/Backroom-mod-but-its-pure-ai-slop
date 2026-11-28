package dev.noclip.backrooms.core.field;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import dev.noclip.backrooms.BackroomsMod;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The camera held up to the eye (use key): a 4:3 frame that is exactly what the photo will be, with zoom (wheel) and
 * exposure (sneak + wheel); attack is the shutter. The exposure shown here is applied again when the picture is developed.
 */
public final class Viewfinder {
	private static final float ZOOM_STEP = 0.25F, EXPOSURE_STEP = 0.5F;
	/** How long the zoom is held for a shot the server has not asked the picture of, in ticks. */
	private static final int SHOT_TICKS = 20;
	/** An old rangefinder's colours: engraved cream, the orange index, a green focus lamp, black leatherette. */
	private static final int CREAM = 0xFFEDE3C8, ORANGE = 0xFFE8892B, GREEN = 0xFF6BE07A, RED = 0xFFE0483A, GREY = 0xFF9A968A,
			BRIGHT_LINE = 0xA0FFF6DC, SHADE = 0xE00B0A09, PANEL = 0xD8141210, METAL = 0xFFB9B2A2, METAL_DARK = 0xFF5E5950;
	/** Draws colour × screen on top of the screen: the only way to show a picture brighter than it is. */
	private static final RenderPipeline GAIN = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(BackroomsMod.id("pipeline/viewfinder_gain"))
			.withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE)))
			.build();
	private static float zoom = 1, exposure;
	private static int shooting;
	/** Where the dials stand now: they turn towards the setting rather than jump. */
	private static float dialZoom = 1, dialExposure;

	private Viewfinder() {
	}

	static void register() {
		// First, so that the exposure preview colours the world and not the rest of the HUD.
		HudElementRegistry.addFirst(BackroomsMod.id("viewfinder"), Viewfinder::extract);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (shooting > 0) shooting--;
		});
	}

	private static boolean raised(Minecraft client) {
		return client.player != null && client.player.isUsingItem() && client.player.getUseItem().is(FieldKit.CAMERA);
	}

	/** Whether the view is through the camera. */
	public static boolean showing(Minecraft client) {
		return raised(client) && client.options.getCameraType().isFirstPerson();
	}

	/** The magnification of the view now: the viewfinder's, also held until a shot just taken has been captured. */
	public static float fovZoom(Minecraft client) {
		return showing(client) || shooting > 0 ? zoom : 1;
	}

	/** One notch of the wheel: zoom, or with sneak held, exposure. */
	public static void scroll(double wheel, boolean sneaking) {
		float by = (float) Math.signum(wheel);
		if (sneaking) exposure = Mth.clamp(exposure + by * EXPOSURE_STEP, -PhotoImage.MAX_EXPOSURE, PhotoImage.MAX_EXPOSURE);
		else zoom = Mth.clamp(zoom + by * ZOOM_STEP, 1, PhotoImage.MAX_ZOOM);
	}

	public static void shoot(Minecraft client) {
		if (!raised(client) || client.player.getCooldowns().isOnCooldown(client.player.getUseItem())) return;
		boolean through = showing(client);
		if (through) shooting = SHOT_TICKS;
		ClientPlayNetworking.send(new PhotoPayloads.Shoot(through ? zoom : 1));
	}

	public static float zoom() {
		return zoom;
	}

	public static float exposure() {
		return exposure;
	}

	static void captured() {
		shooting = 0;
	}

	static void reset() {
		zoom = 1;
		exposure = 0;
		shooting = 0;
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (!showing(client) || client.gui.hud.isHidden()) return;
		int width = g.guiWidth(), height = g.guiHeight();
		int w = Math.min(width, height * 4 / 3), h = Math.min(height, width * 3 / 4);
		int left = (width - w) / 2, top = (height - h) / 2, right = left + w, bottom = top + h, cx = width / 2, cy = height / 2;
		Font font = client.font;

		gain(g, left, top, right, bottom);
		// Outside the window is the camera's body: not in the photo.
		g.fill(0, 0, left, height, SHADE);
		g.fill(right, 0, width, height, SHADE);
		g.fill(left, 0, right, top, SHADE);
		g.fill(left, bottom, right, height, SHADE);
		rounded(g, left, top, right, bottom, 8, SHADE);

		// The rangefinder's bright frame lines, and faint thirds inside them.
		for (int i = 1; i < 3; i++) {
			g.fill(left + w * i / 3, top + 8, left + w * i / 3 + 1, bottom - 8, 0x30FFF6DC);
			g.fill(left + 8, top + h * i / 3, right - 8, top + h * i / 3 + 1, 0x30FFF6DC);
		}
		corners(g, left + 7, top + 7, right - 7, bottom - 7, 22, BRIGHT_LINE);

		// The focus box goes green on a creature near enough to be worth taking.
		Aim aim = aim(client, delta.getGameTimeDeltaPartialTick(true));
		boolean far = aim != null && aim.distance() > Evidence.CAMERA_RANGE;
		boolean locked = aim != null && aim.creature() && !far;
		corners(g, cx - 18, cy - 14, cx + 18, cy + 14, 6, locked ? GREEN : BRIGHT_LINE);
		g.fill(cx - 1, cy, cx + 2, cy + 1, locked ? GREEN : BRIGHT_LINE);
		g.fill(cx, cy - 1, cx + 1, cy + 2, locked ? GREEN : BRIGHT_LINE);
		if (aim != null) {
			// Only a creature earns by being close (what counts as a scene is the server's to say).
			boolean close = aim.creature() && aim.distance() <= Evidence.CLOSE;
			Component range = Component.translatable("camera.noclip_backrooms." + (far ? "far" : close ? "close" : "distance"),
					String.format(Locale.ROOT, "%.1f", aim.distance()));
			text(g, font, range, cx - font.width(range) / 2, cy + 20, far ? GREY : close ? ORANGE : CREAM);
		}

		topPlate(g, font, client, cx, top + 10);

		// The two dials, in the camera body beside the window (inside it where the screen has no room to spare).
		float turn = Math.min(1, delta.getRealtimeDeltaTicks() * 0.6F);
		dialExposure += (exposure - dialExposure) * turn;
		dialZoom += (zoom - dialZoom) * turn;
		int side = left >= 44 ? left / 2 : left + 24;
		dial(g, font, side, cy, dialExposure, -PhotoImage.MAX_EXPOSURE, PhotoImage.MAX_EXPOSURE, EXPOSURE_STEP, 24, "%+.0f",
				Component.translatable("camera.noclip_backrooms.exposure"), String.format(Locale.ROOT, "%+.1f", exposure));
		dial(g, font, width - side, cy, dialZoom, 1, PhotoImage.MAX_ZOOM, ZOOM_STEP, 28, "\u00d7%.0f",
				Component.translatable("camera.noclip_backrooms.zoom"), String.format(Locale.ROOT, "\u00d7%.2f", zoom));

		// The controls, above the hotbar and its bars.
		Component hint = Component.translatable("camera.noclip_backrooms.hint");
		int hw = font.width(hint);
		plate(g, cx - hw / 2 - 6, height - 66, cx + hw / 2 + 6, height - 51);
		g.text(font, hint, cx - hw / 2, height - 62, CREAM, false);
	}

	/** The plate along the top: the film counter in its little window, the maker's name, and the wind-on lamp. */
	private static void topPlate(GuiGraphicsExtractor g, Font font, Minecraft client, int cx, int y) {
		ItemStack camera = client.player.getUseItem();
		boolean winding = client.player.getCooldowns().isOnCooldown(camera);
		Component film = Component.translatable("camera.noclip_backrooms.film");
		String count = Integer.toString(camera.getMaxDamage() - camera.getDamageValue());
		Component state = Component.translatable("camera.noclip_backrooms." + (winding ? "winding" : "ready"));
		String brand = "M.E.G. 35";
		int counter = 18, half = Math.max(font.width(film) + 4 + counter, font.width(state)) + font.width(brand) / 2 + 14;
		plate(g, cx - half, y, cx + half, y + 17);
		int x = cx - half + 6;
		g.text(font, film, x, y + 5, CREAM, false);
		x += font.width(film) + 4;
		g.fill(x, y + 3, x + counter, y + 14, METAL_DARK);
		g.fill(x + 1, y + 4, x + counter - 1, y + 13, CREAM);
		g.text(font, count, x + (counter - font.width(count)) / 2 + 1, y + 5, 0xFF1A1712, false);
		g.text(font, brand, cx - font.width(brand) / 2, y + 5, METAL, false);
		g.text(font, state, cx + half - 6 - font.width(state), y + 5, winding ? RED : GREEN, false);
	}

	/**
	 * A dial seen edge-on through its window: the numbers roll past a fixed orange index, higher values above, fading
	 * as the drum turns away.
	 */
	private static void dial(GuiGraphicsExtractor g, Font font, int cx, int cy, float at, float min, float max, float step,
			int spacing, String format, Component name, String value) {
		int w = 19, h = 50;
		g.fill(cx - w - 2, cy - h - 2, cx + w + 2, cy + h + 2, METAL_DARK);
		g.fill(cx - w - 1, cy - h - 1, cx + w + 1, cy + h + 1, METAL);
		g.fillGradient(cx - w, cy - h, cx + w, cy, 0xFF0C0B0A, 0xFF2C2822);
		g.fillGradient(cx - w, cy, cx + w, cy + h, 0xFF2C2822, 0xFF0C0B0A);
		g.enableScissor(cx - w, cy - h, cx + w, cy + h);
		for (int n = 0; min + n * step <= max + 0.001F; n++) {
			float v = min + n * step;
			int y = cy - Math.round((v - at) * spacing);
			if (y < cy - h - 8 || y > cy + h + 8) continue;
			boolean whole = Math.abs(v - Math.round(v)) < 0.001F;
			g.fill(cx - w, y, cx - w + (whole ? 7 : 4), y + 1, whole ? CREAM : METAL_DARK);
			if (!whole) continue;
			String label = v == 0 ? "0" : String.format(Locale.ROOT, format, v);
			g.text(font, label, cx - font.width(label) / 2 + 3, y - 3, CREAM, false);
		}
		// The drum turning away at top and bottom.
		g.fillGradient(cx - w, cy - h, cx + w, cy - h / 2, 0xF00C0B0A, 0x000C0B0A);
		g.fillGradient(cx - w, cy + h / 2, cx + w, cy + h, 0x000C0B0A, 0xF00C0B0A);
		g.disableScissor();
		// The index.
		for (int i = 0; i < 4; i++) {
			g.fill(cx - w - 5 + i, cy - 3 + i, cx - w - 4 + i, cy + 4 - i, ORANGE);
			g.fill(cx + w + 4 - i, cy - 3 + i, cx + w + 5 - i, cy + 4 - i, ORANGE);
		}
		g.fill(cx - w, cy - 7, cx + w, cy - 6, 0x60E8892B);
		g.fill(cx - w, cy + 6, cx + w, cy + 7, 0x60E8892B);
		text(g, font, name, cx - font.width(name) / 2, cy - h - 14, CREAM);
		int vw = font.width(value);
		plate(g, cx - vw / 2 - 5, cy + h + 6, cx + vw / 2 + 6, cy + h + 19);
		g.text(font, value, cx - vw / 2, cy + h + 9, ORANGE, false);
	}

	/** A small black name plate with a metal rim. */
	private static void plate(GuiGraphicsExtractor g, int x, int y, int r, int b) {
		g.fill(x, y, r, b, PANEL);
		g.fill(x, y, r, y + 1, METAL_DARK);
		g.fill(x, b - 1, r, b, METAL_DARK);
		g.fill(x, y, x + 1, b, METAL_DARK);
		g.fill(r - 1, y, r, b, METAL_DARK);
	}

	/** Rounds the corners of the window off into the body. */
	private static void rounded(GuiGraphicsExtractor g, int x, int y, int r, int b, int radius, int colour) {
		for (int i = 0; i < radius; i++) {
			int n = radius - (int) Math.round(Math.sqrt(radius * radius - (radius - i - 0.5) * (radius - i - 0.5)));
			g.fill(x, y + i, x + n, y + i + 1, colour);
			g.fill(r - n, y + i, r, y + i + 1, colour);
			g.fill(x, b - i - 1, x + n, b - i, colour);
			g.fill(r - n, b - i - 1, r, b - i, colour);
		}
	}

	/** The exposure as it will be developed: darker by covering, brighter by adding the screen onto itself. */
	private static void gain(GuiGraphicsExtractor g, int left, int top, int right, int bottom) {
		float gain = (float) Math.pow(2, exposure);
		if (gain < 1) {
			g.fill(left, top, right, bottom, (int) ((1 - gain) * 255) << 24);
			return;
		}
		for (; gain >= 2; gain /= 2) g.fill(GAIN, left, top, right, bottom, 0xFFFFFFFF);
		int more = (int) ((gain - 1) * 255);
		if (more > 0) g.fill(GAIN, left, top, right, bottom, 0xFF000000 | more << 16 | more << 8 | more);
	}

	private static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int colour) {
		g.text(font, text, x, y, colour, true);
	}

	private static void corners(GuiGraphicsExtractor g, int x, int y, int r, int b, int n, int colour) {
		g.fill(x, y, x + n, y + 1, colour);
		g.fill(x, y + 1, x + 1, y + n, colour);
		g.fill(r - n, y, r, y + 1, colour);
		g.fill(r - 1, y + 1, r, y + n, colour);
		g.fill(x, b - 1, x + n, b, colour);
		g.fill(x, b - n, x + 1, b - 1, colour);
		g.fill(r - n, b - 1, r, b, colour);
		g.fill(r - 1, b - n, r, b - 1, colour);
	}

	private record Aim(double distance, boolean creature) {
	}

	/** What is in the middle of the view (creature or wall) and how many blocks off, or null beyond 48. */
	private static Aim aim(Minecraft client, float partial) {
		Entity player = client.player;
		Vec3 eye = player.getEyePosition(partial), end = eye.add(player.getViewVector(partial).scale(48));
		HitResult wall = player.pick(48, partial, false);
		Aim best = wall.getType() == HitResult.Type.MISS ? null : new Aim(wall.getLocation().distanceTo(eye), false);
		for (Entity e : client.level.getEntities(player, player.getBoundingBox().inflate(48),
				e -> e instanceof LivingEntity && !(e instanceof Player) && !e.isInvisible())) {
			var hit = e.getBoundingBox().clip(eye, end);
			if (hit.isEmpty()) continue;
			double to = hit.get().distanceTo(eye);
			if (best == null || to < best.distance()) best = new Aim(to, true);
		}
		return best;
	}
}
