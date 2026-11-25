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
import net.minecraft.world.level.Level;

/**
 * Client-side atmospheric dread cues for Overworld Wretch Rejection &amp; Backrooms Seepage (core.trip.Pull):
 * <ul>
 *   <li>Stage 1 (25+): faint yellow Level-0 cast and distant 60Hz fluorescent hum.</li>
 *   <li>Stage 2 (50+): deeper yellow tint, occasional failing-tube dark flickers, and outdoor scratching noises.</li>
 *   <li>Stage 3–4 (75+): pulsing vignette, frequent flickers/scratches/whispers, plus client-only Smiler/Hound peeks.</li>
 * </ul>
 */
public final class PullClient {
	private static final SoundEvent HUM = SoundEvent.createVariableRangeEvent(BackroomsMod.id("ambient.fluorescent_hum"));
	private static final SoundEvent SCRATCH = SoundEvent.createVariableRangeEvent(BackroomsMod.id("ambient.wall_scratch"));
	private static final SoundEvent WHISPER = SoundEvent.createVariableRangeEvent(BackroomsMod.id("ambient.distant_whisper"));

	private static int flicker;
	private static int nextHum;
	private static int nextScratch = 240;

	private PullClient() {
	}

	public static void register() {
		HudElementRegistry.addFirst(BackroomsMod.id("backrooms_pull"), PullClient::extract);
		ClientTickEvents.END_CLIENT_TICK.register(PullClient::tick);
	}

	public static PullState state() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? PullState.NONE : client.player.getAttachedOrElse(Pull.STATE, PullState.NONE);
	}

	public static float pull() {
		return state().pull();
	}

	public static int stage() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.level.dimension() != Level.OVERWORLD) return 0;
		return state().stage();
	}

	private static void tick(Minecraft client) {
		if (client.player == null || client.level == null || client.isPaused()) return;
		if (client.level.dimension() != Level.OVERWORLD) {
			flicker = 0;
			return;
		}
		float pull = pull();
		int stage = state().stage();
		if (flicker > 0) flicker--;

		if (stage >= 2 && flicker == 0 && client.player.getRandom().nextFloat() < (0.008F + (pull - Pull.STAGE_2) / 1800.0F)) {
			flicker = 2 + client.player.getRandom().nextInt(stage >= 3 ? 5 : 3);
		}
		if (stage < 1) {
			nextHum = 60;
			nextScratch = 260;
			return;
		}

		// 60Hz fluorescent hum grows louder and more frequent with rejection stage
		if (--nextHum <= 0) {
			float strength = Mth.clamp((pull - Pull.STAGE_1) / (Pull.FALL - Pull.STAGE_1), 0.0F, 1.0F);
			float volume = 0.10F + 0.48F * strength;
			client.getSoundManager().play(SimpleSoundInstance.forUI(HUM, 1.0F, volume));
			nextHum = stage >= 3 ? 85 : (stage == 2 ? 150 : 260);
		}

		// Outdoor Hound scratching / distant whispers at Stage 2+ (especially eerie at night!)
		if (stage >= 2 && --nextScratch <= 0) {
			boolean night = !client.level.isBrightOutside();
			SoundEvent cue = (stage >= 3 && client.player.getRandom().nextBoolean()) ? WHISPER : SCRATCH;
			float vol = (night ? 0.45F : 0.24F) + (stage - 2) * 0.12F;
			client.getSoundManager().play(SimpleSoundInstance.forUI(cue, 0.85F + client.player.getRandom().nextFloat() * 0.25F, vol));
			nextScratch = stage >= 3 ? 220 : 380;
		}
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.level.dimension() != Level.OVERWORLD) return;
		float pull = pull();
		if (pull < Pull.STAGE_1) return;
		float strength = Mth.clamp((pull - Pull.STAGE_1) / (Pull.FALL - Pull.STAGE_1), 0.0F, 1.0F);
		float pulse = 0.84F + 0.16F * Mth.sin(client.player.tickCount * 0.08F);
		int alpha = (int) (255 * 0.36F * strength * pulse);
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		if (alpha > 0) graphics.fill(0, 0, w, h, (alpha << 24) | 0xD9C46A);
		if (flicker > 0) graphics.fill(0, 0, w, h, 0x82000000);
	}
}
