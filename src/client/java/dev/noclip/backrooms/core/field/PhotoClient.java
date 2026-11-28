package dev.noclip.backrooms.core.field;

import com.mojang.blaze3d.platform.NativeImage;
import dev.noclip.backrooms.BackroomsMod;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The camera's client side: takes the picture (the HUD is put away for the shutter, so only the world is in it), and
 * keeps the textures of the pictures being looked at.
 */
public final class PhotoClient {
	/** Ticks with the HUD away before the screen is read, so that a frame without it has been drawn. */
	private static final int SHUTTER_TICKS = 3;
	private static final Deque<UUID> SHOTS = new ArrayDeque<>();
	private static int waiting;
	private static boolean hidHud;
	private static final Map<UUID, Identifier> TEXTURES = new HashMap<>();

	private PhotoClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PhotoPayloads.Capture.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			if (SHOTS.isEmpty()) {
				waiting = SHUTTER_TICKS;
				// Only put back what was put away here: someone with the HUD off already keeps it off.
				hidHud = !client.gui.hud.isHidden();
				if (hidHud) client.gui.hud.toggle();
			}
			SHOTS.add(payload.photo());
		});
		ClientPlayNetworking.registerGlobalReceiver(PhotoPayloads.Image.TYPE,
				(payload, context) -> keep(payload.photo(), payload.pixels()));
		ClientPlayNetworking.registerGlobalReceiver(PhotoPayloads.View.TYPE,
				(payload, context) -> {
			PhotoScreen screen = new PhotoScreen(payload.album(), payload.photos());
			if (context.client().gui.screen() instanceof PhotoScreen before) screen.openAt(before);
			context.client().gui.setScreen(screen);
		});
		ClientTickEvents.END_CLIENT_TICK.register(PhotoClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			shutterDone(client);
			forgetAll(client);
		});
	}

	private static void tick(Minecraft client) {
		if (SHOTS.isEmpty() || --waiting > 0) return;
		UUID id = SHOTS.peek();
		shutterDone(client);
		Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), screen -> {
			try (screen) {
				byte[] pixels = PhotoImage.develop(screen::getPixel, screen.getWidth(), screen.getHeight(), id.getLeastSignificantBits());
				if (ClientPlayNetworking.canSend(PhotoPayloads.Upload.TYPE)) ClientPlayNetworking.send(new PhotoPayloads.Upload(id, pixels));
			}
		});
	}

	/** The shutter closes: the HUD comes back. Shots asked for meanwhile are of the same moment and are let go. */
	private static void shutterDone(Minecraft client) {
		if (hidHud && client.gui.hud.isHidden()) client.gui.hud.toggle();
		hidHud = false;
		SHOTS.clear();
	}

	/** The texture of a photo's picture, or null until it has come (ask once with {@link #request}). */
	static Identifier texture(UUID photo) {
		return TEXTURES.get(photo);
	}

	static void request(UUID photo) {
		if (!TEXTURES.containsKey(photo)) ClientPlayNetworking.send(new PhotoPayloads.Request(photo));
	}

	private static void keep(UUID photo, byte[] pixels) {
		if (pixels.length != PhotoImage.BYTES || TEXTURES.containsKey(photo)) return;
		NativeImage image = new NativeImage(PhotoImage.WIDTH, PhotoImage.HEIGHT, false);
		for (int y = 0; y < PhotoImage.HEIGHT; y++) {
			for (int x = 0; x < PhotoImage.WIDTH; x++) image.setPixel(x, y, PhotoImage.argb(pixels, x, y));
		}
		Identifier name = BackroomsMod.id("photo/" + photo);
		DynamicTexture texture = new DynamicTexture(name::toString, image);
		Minecraft.getInstance().getTextureManager().register(name, texture);
		TEXTURES.put(photo, name);
	}

	/** Lets the textures go (the viewer closed, or the world was left). */
	static void forgetAll(Minecraft client) {
		TEXTURES.values().forEach(client.getTextureManager()::release);
		TEXTURES.clear();
	}
}
