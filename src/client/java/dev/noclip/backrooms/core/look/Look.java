package dev.noclip.backrooms.core.look;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.mixin.client.GameRendererAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import org.lwjgl.glfw.GLFW;

/**
 * The found-footage look, in two parts that can each be turned off:
 * <ul>
 * <li>a built-in resource pack of photo-based textures ({@code resourcepacks/realistic}, made by
 * {@code tools/make_level0_realistic.py}; the first sample covers Level 0), on by default and listed in the Resource
 * Packs screen, so the drawn textures come back when it is turned off;</li>
 * <li>a camcorder filter while in the Backrooms: the post effect {@code post_effect/camcorder.json} (lens, colour,
 * grain, bloom, a tracking line) and a REC overlay. F7 toggles it.</li>
 * </ul>
 * A post effect cannot read the game time, so the filter gets it from a 2x1 texture written every tick, along with
 * how the current level should look ({@link Mood}). Darkness comes from the game itself, adjusted while the camera is
 * on: its fog turns black and close ({@link #darken}; the post effect runs after the depth buffer is cleared for the
 * hand, so it cannot see how far anything is), and light falls off fast, through a negative brightness setting in the
 * lightmap ({@link #falloff}), so that the lamps make pools of light with dark between them.
 */
public final class Look {
	public static final Identifier PACK = BackroomsMod.id("realistic");
	public static final Identifier CAMCORDER = BackroomsMod.id("camcorder");
	private static final Identifier CLOCK = BackroomsMod.id("textures/effect/camcorder_clock.png");
	private static final RandomSource RANDOM = RandomSource.create();
	private static KeyMapping toggle;
	private static DynamicTexture clock;
	private static boolean enabled = true;
	private static int ticks, recording;

	/**
	 * How dark a level looks through the camera: the dark starts {@code start} blocks out and is complete at
	 * {@code end}; {@code falloff} (0..1) makes dim light sink toward black so that only what is close to a lamp stays
	 * bright. With Level 0's 0.38 and the filter's exposure, light levels 13 to 15 near a lamp stay bright, 11 comes out
	 * about a third, 9 about a seventh, 7 and below black. Level 0 is meant to be the eeriest; the levels with their own lighting
	 * get a lighter touch.
	 */
	record Mood(int start, int end, float falloff) {
		static final Mood LEVEL_0 = new Mood(5, 28, 0.32F), LEVEL_3 = new Mood(6, 30, 0.18F),
				LEVEL_4 = new Mood(10, 48, 0.14F), DEFAULT = new Mood(9, 44, 0.15F);
		// Zenith Station is seen across: the singularity is some 180 blocks from the ring windows, the sea of rooms 60 out.
		static final Mood ZENITH = new Mood(24, 320, 0.15F);
		// Base Gamma's mall and halls are lit like day: light carries, the far end of the atrium shows.
		static final Mood GAMMA = new Mood(20, 90, 0.04F);

		static Mood of(Identifier dimension) {
			return switch (dimension.getPath()) {
				case "level_0" -> LEVEL_0;
				case "level_0.1" -> ZENITH;
				case "level_3" -> LEVEL_3;
				case "level_4" -> LEVEL_4;
				default -> DEFAULT;
			};
		}
	}

	private static boolean inGamma;

	/** The mood here: the level's own, unless the camera is inside Base Gamma. */
	static Mood mood(Minecraft client) {
		Mood mood = Mood.of(client.level.dimension().identifier());
		return mood == Mood.LEVEL_3 && inGamma ? Mood.GAMMA : mood;
	}

	private Look() {
	}

	public static void register() {
		ResourceLoader.registerBuiltinPack(PACK, FabricLoader.getInstance().getModContainer(BackroomsMod.MOD_ID).orElseThrow(),
				Component.translatable("pack.noclip_backrooms.realistic"), PackActivationType.DEFAULT_ENABLED);
		KeyMapping.Category category = KeyMapping.Category.register(BackroomsMod.id("backrooms"));
		toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.noclip_backrooms.camcorder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, category));
		// The clock has to exist before the post effect is first built, or the effect would load a missing texture.
		ClientLifecycleEvents.CLIENT_STARTED.register(Look::clock);
		ClientTickEvents.END_CLIENT_TICK.register(Look::tick);
		HudElementRegistry.addLast(BackroomsMod.id("camcorder"), CamcorderOverlay::extract);
	}

	/** Turns the camcorder filter on or off (the F7 key does the same). */
	public static void setEnabled(boolean on) {
		enabled = on;
	}

	/** Whether the filter shows now: on, in a Backrooms level, and looking through the player's own eyes. */
	public static boolean active(Minecraft client) {
		return enabled && client.level != null && client.player != null && client.getCameraEntity() == client.player
				&& dev.noclip.backrooms.core.Home.inBackrooms(client.level.dimension());
	}

	/** Called with the fog the game worked out for this frame: in the Backrooms, through the camera, it turns black and close. */
	public static void darken(Camera camera, FogData fog) {
		Minecraft client = Minecraft.getInstance();
		if (fog == null || !active(client) || camera.getFluidInCamera() != FogType.NONE) return;
		Mood mood = mood(client);
		fog.environmentalStart = Math.min(fog.environmentalStart, mood.start());
		fog.environmentalEnd = Math.min(fog.environmentalEnd, mood.end());
		fog.color.set(0.018F, 0.016F, 0.011F, 1.0F);
	}

	/** Called before the lightmap is drawn: in the Backrooms, through the camera, light falls off fast. */
	public static void falloff(LightmapRenderState state) {
		Minecraft client = Minecraft.getInstance();
		if (!active(client)) return;
		// The brightness setting mixes toward a brightened curve; mixing away from it darkens dim light much more than bright.
		state.brightness = -mood(client).falloff();
	}

	/** Ticks of footage since the player last came into the Backrooms with the filter on. */
	static int recordingTicks() {
		return recording;
	}

	private static void tick(Minecraft client) {
		while (toggle.consumeClick()) {
			enabled = !enabled;
			if (client.player != null) client.player.sendOverlayMessage(Component.translatable(enabled ? "message.noclip_backrooms.camcorder_on" : "message.noclip_backrooms.camcorder_off"));
		}
		boolean want = active(client);
		inGamma = want && client.level.dimension() == dev.noclip.backrooms.level3.Level3.LEVEL_3
				&& dev.noclip.backrooms.level3.Level3.insideGamma(client.level, client.player.getBlockX(), client.player.getBlockZ());
		recording = want ? recording + 1 : 0;
		Identifier current = client.gameRenderer.currentPostEffect();
		if (want) {
			writeClock(client);
			if (!CAMCORDER.equals(current)) ((GameRendererAccess) client.gameRenderer).noclip_backrooms$setPostEffect(CAMCORDER);
		} else if (CAMCORDER.equals(current)) {
			client.gameRenderer.clearPostEffect();
		}
	}

	private static void clock(Minecraft client) {
		if (clock != null) return;
		clock = new DynamicTexture(() -> "noclip_backrooms camcorder clock", 2, 1, false);
		client.getTextureManager().register(CLOCK, clock);
	}

	/**
	 * Pixel 0: red and green hold the tick count (high and low byte), blue a fresh random number for the grain.
	 * Pixel 1: the level's mood, red the start of the dark, green its end, blue the falloff.
	 */
	private static void writeClock(Minecraft client) {
		clock(client);
		ticks = (ticks + 1) & 0xFFFF;
		clock.getPixels().setPixel(0, 0, 0xFF000000 | (ticks >> 8) << 16 | (ticks & 0xFF) << 8 | RANDOM.nextInt(256));
		Mood mood = mood(client);
		clock.getPixels().setPixel(1, 0, 0xFF000000 | Math.min(mood.start(), 255) << 16 | Math.min(mood.end(), 255) << 8 | Math.round(mood.falloff() * 255));
		clock.upload();
	}
}
