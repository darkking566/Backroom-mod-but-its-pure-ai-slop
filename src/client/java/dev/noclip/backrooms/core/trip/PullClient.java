package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.BackroomsMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

/**
 * The warning signs before the Backrooms take someone back (core.trip.Pull): from UNEASY the hum of fluorescent lights
 * now and then and a faint yellow cast over everything; from CLOSE the hum comes often, the cast deepens and the world
 * flickers dark for a moment, like a failing tube.
 */
public final class PullClient {
	private static final SoundEvent HUM = SoundEvent.createVariableRangeEvent(BackroomsMod.id("ambient.fluorescent_hum"));
	private static int flicker;
	private static int nextHum;

	private PullClient() {
	}

	public static void register() {
		HudElementRegistry.addFirst(BackroomsMod.id("backrooms_pull"), PullClient::extract);
		ClientTickEvents.END_CLIENT_TICK.register(PullClient::tick);
	}

	public static float pull() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? 0.0F : client.player.getAttachedOrElse(Pull.STATE, PullState.NONE).pull();
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.isPaused()) return;
		float pull = pull();
		if (flicker > 0) flicker--;
		if (pull >= Pull.CLOSE && flicker == 0 && client.player.getRandom().nextFloat() < 0.015F + (pull - Pull.CLOSE) / 1500.0F) {
			flicker = 2 + client.player.getRandom().nextInt(4);
		}
		if (pull < Pull.UNEASY) {
			nextHum = 40;
			return;
		}
		if (--nextHum <= 0) {
			float volume = 0.12F + 0.5F * (pull - Pull.UNEASY) / (Pull.FALL - Pull.UNEASY);
			client.getSoundManager().play(SimpleSoundInstance.forUI(HUM, 1.0F, volume));
			nextHum = pull >= Pull.CLOSE ? 70 : 200;
		}
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		float pull = pull();
		if (pull < Pull.UNEASY) return;
		float strength = Mth.clamp((pull - Pull.UNEASY) / (Pull.FALL - Pull.UNEASY), 0.0F, 1.0F);
		float pulse = 0.85F + 0.15F * Mth.sin(client.player.tickCount * 0.09F);
		int alpha = (int) (255 * 0.42F * strength * pulse);
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		if (alpha > 0) graphics.fill(0, 0, w, h, (alpha << 24) | 0xD9C46A);
		if (flicker > 0) graphics.fill(0, 0, w, h, 0x8C000000);
	}
}
