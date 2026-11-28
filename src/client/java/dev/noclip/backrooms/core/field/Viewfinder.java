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
	private static final int INK = 0xE6F2F0E6, DIM = 0x70F2F0E6, GOLD = 0xFFFFC23C, GREY = 0xFF9A968A, SHADE = 0xB0000000;
	/** Draws colour × screen on top of the screen: the only way to show a picture brighter than it is. */
	private static final RenderPipeline GAIN = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
			.withLocation(BackroomsMod.id("pipeline/viewfinder_gain"))
			.withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE)))
			.build();
	private static float zoom = 1, exposure;
	private static int shooting;

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
		// Outside the frame is not in the photo.
		g.fill(0, 0, left, height, SHADE);
		g.fill(right, 0, width, height, SHADE);
		g.fill(left, 0, right, top, SHADE);
		g.fill(left, bottom, right, height, SHADE);

		for (int i = 1; i < 3; i++) {
			g.fill(left + w * i / 3, top, left + w * i / 3 + 1, bottom, 0x50FFFFFF);
			g.fill(left, top + h * i / 3, right, top + h * i / 3 + 1, 0x50FFFFFF);
		}
		corners(g, left + 4, top + 4, right - 4, bottom - 4, 14, INK);
		corners(g, cx - 18, cy - 14, cx + 18, cy + 14, 5, INK);

		// Top: film left and whether the camera has wound on.
		ItemStack camera = client.player.getUseItem();
		boolean winding = client.player.getCooldowns().isOnCooldown(camera);
		text(g, font, Component.translatable("camera.noclip_backrooms.film", camera.getMaxDamage() - camera.getDamageValue()), cx - 60, top + 22, INK);
		Component state = Component.translatable(winding ? "camera.noclip_backrooms.winding" : "camera.noclip_backrooms.ready");
		text(g, font, state, cx + 60 - font.width(state), top + 22, winding ? GOLD : INK);

		// Under the brackets: how far what is in the middle is (the buyer pays by distance).
		double distance = distance(client, delta.getGameTimeDeltaPartialTick(true));
		if (distance >= 0) {
			String key = distance <= Evidence.CLOSE ? "close" : distance <= Evidence.CAMERA_RANGE ? "distance" : "far";
			Component range = Component.translatable("camera.noclip_backrooms." + key, String.format(Locale.ROOT, "%.1f", distance));
			text(g, font, range, cx - font.width(range) / 2, cy + 20, distance <= Evidence.CLOSE ? GOLD : distance <= Evidence.CAMERA_RANGE ? INK : GREY);
		}

		// Exposure on the left, zoom on the right.
		scale(g, font, left + 16, cy, (exposure + PhotoImage.MAX_EXPOSURE) / (2 * PhotoImage.MAX_EXPOSURE), 8, "+2", "-2",
				Component.translatable("camera.noclip_backrooms.exposure"), String.format(Locale.ROOT, "%+.1f", exposure), false);
		scale(g, font, right - 17, cy, (zoom - 1) / (PhotoImage.MAX_ZOOM - 1), 6, "×4", "×1",
				Component.translatable("camera.noclip_backrooms.zoom"), String.format(Locale.ROOT, "×%.2f", zoom), true);

		// The controls, above the hotbar and its bars.
		Component hint = Component.translatable("camera.noclip_backrooms.hint");
		int hw = font.width(hint);
		g.fill(cx - hw / 2 - 4, height - 74, cx + hw / 2 + 4, height - 62, 0x80000000);
		g.text(font, hint, cx - hw / 2, height - 72, INK, false);
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

	/** A vertical scale {@code at} 0..1 from the bottom, its name above and its value beside the marker. */
	private static void scale(GuiGraphicsExtractor g, Font font, int x, int cy, float at, int steps, String high, String low,
			Component name, String value, boolean valueLeft) {
		int half = 44, top = cy - half, bottom = cy + half;
		g.fill(x, top, x + 1, bottom + 1, DIM);
		for (int i = 0; i <= steps; i++) {
			int y = bottom - Math.round(i * 2F * half / steps);
			g.fill(x - 2, y, x + 3, y + 1, DIM);
		}
		int y = bottom - Math.round(at * 2 * half);
		g.fill(x - 4, y - 1, x + 5, y + 2, GOLD);
		text(g, font, name, x - font.width(name) / 2, top - 24, INK);
		text(g, font, Component.literal(high), x - font.width(high) / 2, top - 12, DIM | 0xFF000000);
		text(g, font, Component.literal(low), x - font.width(low) / 2, bottom + 5, DIM | 0xFF000000);
		text(g, font, Component.literal(value), valueLeft ? x - 8 - font.width(value) : x + 8, y - 4, GOLD);
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

	/** Blocks to whatever is in the middle of the view (creature or wall), or -1 beyond 48. */
	private static double distance(Minecraft client, float partial) {
		Entity player = client.player;
		Vec3 eye = player.getEyePosition(partial), end = eye.add(player.getViewVector(partial).scale(48));
		HitResult wall = player.pick(48, partial, false);
		double best = wall.getType() == HitResult.Type.MISS ? -1 : wall.getLocation().distanceTo(eye);
		for (Entity e : client.level.getEntities(player, player.getBoundingBox().inflate(48), e -> e instanceof LivingEntity && !e.isInvisible())) {
			var hit = e.getBoundingBox().clip(eye, end);
			if (hit.isEmpty()) continue;
			double to = hit.get().distanceTo(eye);
			if (best < 0 || to < best) best = to;
		}
		return best;
	}
}
