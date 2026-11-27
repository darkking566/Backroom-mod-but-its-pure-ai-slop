package dev.noclip.backrooms.core.look;

import com.mojang.blaze3d.platform.InputConstants;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.level0.FlashlightItem;
import dev.noclip.backrooms.mixin.client.GameRendererAccess;
import java.lang.reflect.Method;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * The Backrooms visual atmosphere, decoupled cleanly into independent layers:
 * <ul>
 *   <li><b>Backrooms darkness &amp; fog</b> ({@link #darken}, {@link #falloff}): active throughout Backrooms levels
 *       whenever Iris shaders are OFF, independent of F7 — turning off the VHS filter never turns the world bright and foggy.</li>
 *   <li><b>Non-shader lamp bloom &amp; real-time flashlight spotlight</b> ({@code shaders/post/camcorder.fsh}):
 *       keeps glowing lamps luminous and projects a smooth, zero-stutter directional flashlight beam cone.</li>
 *   <li><b>VHS camcorder filter</b> (toggled instantaneously via F7): flips a single pixel flag in the 4x1
 *       {@code ClockSampler} texture with 0ms latency (zero GPU pipeline reloads). Automatically steps back when
 *       Iris shaders are active so shader packs and labPBR never collide with double post-processing.</li>
 * </ul>
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

	// Smoothed client-side flashlight projection state
	private static float smoothStrength = 0.0F;
	private static float smoothCenterX = 0.5F;
	private static float smoothCenterY = 0.5F;
	private static float smoothDist = 0.45F;
	private static float smoothInfinite = 0.0F;

	// Cached optional Iris reflection hook (zero hard dependency on Iris)
	private static boolean irisChecked;
	private static Object irisApi;
	private static Method irisIsShaderInUse;

	/**
	 * Per-level fog distances and non-shader lightmap falloff, calibrated for 5-block ceiling headroom so unlit areas
	 * stay deeply dark while lit 5-block lamp pools keep warm contrast and luminous fixtures.
	 */
	record Mood(int start, int end, float falloff) {
		static final Mood LEVEL_0 = new Mood(6, 30, 0.16F), LEVEL_3 = new Mood(6, 32, 0.12F),
				LEVEL_4 = new Mood(16, 62, 0.05F), DEFAULT = new Mood(9, 44, 0.11F);
		static final Mood ZENITH = new Mood(24, 320, 0.11F);
		static final Mood GAMMA = new Mood(20, 90, 0.03F);
		/** A night of Level 398 in the Frontrooms (frontrooms.Night398Client): close fog, dark red. */
		static final Mood NIGHT_398 = new Mood(4, 34, 0.14F);

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

	static Mood mood(Minecraft client) {
		if (night398(client)) return Mood.NIGHT_398;
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
		ClientLifecycleEvents.CLIENT_STARTED.register(Look::clock);
		ClientTickEvents.END_CLIENT_TICK.register(Look::tick);
		HudElementRegistry.addLast(BackroomsMod.id("camcorder"), CamcorderOverlay::extract);
		HudElementRegistry.addLast(BackroomsMod.id("flashlight_hud"), FlashlightHud::extract);
	}

	/** Turns the VHS camcorder overlay/filter on or off (F7 toggles this instantaneously). */
	public static void setEnabled(boolean on) {
		enabled = on;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** Whether the player is viewing a Backrooms dimension from their own camera. */
	public static boolean inBackroomsView(Minecraft client) {
		return client.level != null && client.player != null && client.getCameraEntity() == client.player
				&& dev.noclip.backrooms.core.Home.inBackrooms(client.level.dimension());
	}

	/** Whether the player is in the Frontrooms on a night of Level 398, from their own camera. */
	static boolean night398(Minecraft client) {
		return client.player != null && client.getCameraEntity() == client.player && dev.noclip.backrooms.frontrooms.Night398Client.active(client);
	}

	/** Whether the VHS camcorder overlay/filter is active (used by CamcorderOverlay and LookClientTest). */
	public static boolean active(Minecraft client) {
		return enabled && inBackroomsView(client);
	}

	/** True when Iris has an active shader pack loaded (safe reflection with zero compile dependency). */
	public static boolean shadersActive() {
		if (!irisChecked) {
			irisChecked = true;
			try {
				Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				irisApi = apiClass.getMethod("getInstance").invoke(null);
				irisIsShaderInUse = apiClass.getMethod("isShaderPackInUse");
			} catch (Throwable ignored) {
				irisApi = null;
				irisIsShaderInUse = null;
			}
		}
		if (irisApi == null || irisIsShaderInUse == null) return false;
		try {
			return Boolean.TRUE.equals(irisIsShaderInUse.invoke(irisApi));
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * Applies Backrooms dark fog whenever inside a Backrooms level (non-shader mode), regardless of whether the
	 * F7 VHS camcorder filter is on or off so toggling F7 never snaps back to white/washed fog.
	 */
	public static void darken(Camera camera, FogData fog) {
		Minecraft client = Minecraft.getInstance();
		if (fog == null || !(inBackroomsView(client) || night398(client)) || shadersActive() || camera.getFluidInCamera() != FogType.NONE) return;
		Mood mood = mood(client);
		fog.environmentalStart = Math.min(fog.environmentalStart, mood.start());
		fog.environmentalEnd = Math.min(fog.environmentalEnd, mood.end());
		if (mood == Mood.NIGHT_398) {
			fog.color.set(0.045F, 0.010F, 0.008F, 1.0F);
		} else if (mood == Mood.LEVEL_4) {
			fog.color.set(0.80F, 0.84F, 0.87F, 1.0F);
		} else {
			fog.color.set(0.018F, 0.016F, 0.012F, 1.0F);
		}
	}

	/**
	 * Applies calibrated lightmap falloff whenever inside a Backrooms level without Iris shaders, independent of F7.
	 * Skipped when Iris shaders are active so shader packs control cave lighting without double darkening.
	 */
	public static void falloff(LightmapRenderState state) {
		Minecraft client = Minecraft.getInstance();
		if (!(inBackroomsView(client) || night398(client)) || shadersActive()) return;
		state.brightness = -mood(client).falloff();
	}

	static int recordingTicks() {
		return recording;
	}

	private static void tick(Minecraft client) {
		// Single-flip consume so a lag spike can never double-toggle F7 back to its old state.
		if (toggle.consumeClick()) {
			while (toggle.consumeClick()) {
				// Drain duplicate queued clicks from the same tick.
			}
			enabled = !enabled;
			if (client.player != null) {
				String key = shadersActive()
						? (enabled ? "message.noclip_backrooms.camcorder_hud_on" : "message.noclip_backrooms.camcorder_hud_off")
						: (enabled ? "message.noclip_backrooms.camcorder_on" : "message.noclip_backrooms.camcorder_off");
				client.player.sendOverlayMessage(Component.translatable(key));
			}
		}

		boolean inBackrooms = inBackroomsView(client);
		boolean shaders = shadersActive();
		inGamma = inBackrooms && client.level.dimension() == dev.noclip.backrooms.level3.Level3.LEVEL_3
				&& dev.noclip.backrooms.level3.Level3.insideGamma(client.level, client.player.getBlockX(), client.player.getBlockZ());
		recording = (enabled && inBackrooms) ? recording + 1 : 0;

		updateFlashlightBeamState(client);

		// Keep our lightweight post-effect mounted once while inside Backrooms without shaders;
		// F7 flips a 1-byte uniform in ClockSampler (0ms instant switch, zero GPU pipeline reload!).
		boolean wantPost = inBackrooms && !shaders;
		Identifier current = client.gameRenderer.currentPostEffect();
		if (wantPost) {
			writeClock(client);
			if (!CAMCORDER.equals(current)) {
				((GameRendererAccess) client.gameRenderer).noclip_backrooms$setPostEffect(CAMCORDER);
			}
		} else if (CAMCORDER.equals(current)) {
			client.gameRenderer.clearPostEffect();
		}
	}

	private static void updateFlashlightBeamState(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || player.isSpectator() || !player.isAlive()) {
			smoothStrength = Mth.lerp(0.35F, smoothStrength, 0.0F);
			return;
		}
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		ItemStack litStack = ItemStack.EMPTY;
		InteractionHand hand = null;
		if (FlashlightItem.isLit(main)) {
			litStack = main;
			hand = InteractionHand.MAIN_HAND;
		} else if (FlashlightItem.isLit(off)) {
			litStack = off;
			hand = InteractionHand.OFF_HAND;
		}

		if (litStack.isEmpty()) {
			smoothStrength = Mth.lerp(0.42F, smoothStrength, 0.0F);
			if (smoothStrength < 0.004F) smoothStrength = 0.0F;
			return;
		}

		boolean infinite = FlashlightItem.isInfinite(litStack);
		float targetStrength = infinite ? 1.0F : 0.86F;
		if (!infinite && FlashlightItem.battery(litStack) <= FlashlightItem.SECONDS_PER_BAR / 2 && RANDOM.nextInt(18) == 0) {
			targetStrength *= 0.35F;
		}

		double maxRange = infinite ? 24.0 : 18.0;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = client.level.clip(new ClipContext(eye, eye.add(look.scale(maxRange)),
				ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		double hitDist = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(eye) : maxRange;
		float targetDist = Mth.clamp((float) (hitDist / 24.0), 0.0F, 1.0F);

		// Subtle physical hand-side offset + gentle walking bob
		float sideOffset = (hand == InteractionHand.MAIN_HAND ? 0.022F : -0.022F);
		float speed = (float) player.getDeltaMovement().horizontalDistance();
		float bobX = Mth.sin(player.tickCount * 0.42F) * Math.min(0.014F, speed * 0.055F);
		float bobY = Mth.cos(player.tickCount * 0.84F) * Math.min(0.012F, speed * 0.045F);

		smoothStrength = Mth.lerp(0.45F, smoothStrength, targetStrength);
		smoothInfinite = Mth.lerp(0.35F, smoothInfinite, infinite ? 1.0F : 0.0F);
		smoothCenterX = Mth.lerp(0.38F, smoothCenterX, 0.5F + sideOffset + bobX);
		smoothCenterY = Mth.lerp(0.38F, smoothCenterY, 0.485F + bobY);
		smoothDist = Mth.lerp(0.35F, smoothDist, targetDist);
	}

	private static void clock(Minecraft client) {
		if (clock != null) return;
		clock = new DynamicTexture(() -> "noclip_backrooms camcorder clock", 4, 1, false);
		client.getTextureManager().register(CLOCK, clock);
	}

	/**
	 * 4x1 control texture read by {@code shaders/post/camcorder.fsh}:
	 * <ul>
	 *   <li>Pixel 0: tick hi/lo byte + random grain seed</li>
	 *   <li>Pixel 1: fog start, fog end, lightmap falloff</li>
	 *   <li>Pixel 2: VHS filter toggle (0/255), flashlight beam strength (0..255), flashlight style (0 warm / 255 arc)</li>
	 *   <li>Pixel 3: flashlight center X offset, center Y offset, normalized hit distance</li>
	 * </ul>
	 */
	private static void writeClock(Minecraft client) {
		clock(client);
		ticks = (ticks + 1) & 0xFFFF;
		Mood mood = mood(client);
		int camByte = enabled ? 255 : 0;
		int flStrByte = Mth.clamp(Math.round(smoothStrength * 255.0F), 0, 255);
		int flStyleByte = Mth.clamp(Math.round(smoothInfinite * 255.0F), 0, 255);
		int flCxByte = Mth.clamp(Math.round(((smoothCenterX - 0.5F) / 0.24F + 0.5F) * 255.0F), 0, 255);
		int flCyByte = Mth.clamp(Math.round(((smoothCenterY - 0.5F) / 0.24F + 0.5F) * 255.0F), 0, 255);
		int flDistByte = Mth.clamp(Math.round(smoothDist * 255.0F), 0, 255);

		clock.getPixels().setPixel(0, 0, 0xFF000000 | (ticks >> 8) << 16 | (ticks & 0xFF) << 8 | RANDOM.nextInt(256));
		clock.getPixels().setPixel(1, 0, 0xFF000000 | Math.min(mood.start(), 255) << 16 | Math.min(mood.end(), 255) << 8 | Math.round(mood.falloff() * 255));
		clock.getPixels().setPixel(2, 0, 0xFF000000 | camByte << 16 | flStrByte << 8 | flStyleByte);
		clock.getPixels().setPixel(3, 0, 0xFF000000 | flCxByte << 16 | flCyByte << 8 | flDistByte);
		clock.upload();
	}
}
