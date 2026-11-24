package dev.noclip.backrooms.core.rpg;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Renders the player's Sanity bar directly in the bottom survival HUD (paired with health and hunger),
 * plus a pulsing crimson-violet edge vignette whenever sanity drops below 30% (hallucination threshold).
 */
public final class SanityBarHud {
	private static final int BAR_WIDTH = 81;
	private static final int BAR_HEIGHT = 9;

	private SanityBarHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gui.hud.isHidden() || player.isSpectator()) return;

		PlayerStats stats = PlayerRpg.get(player);
		float ratio = stats.sanityRatio();
		boolean critical = stats.isHallucinating();
		boolean creative = player.isCreative();

		// Draw low-sanity edge vignette & hallucination panic flash even in creative when testing < 30%.
		if (critical || SanityHallucinations.scareFlashStrength() > 0.01F) {
			extractLowSanityVignette(graphics, ratio, player.tickCount);
		}

		// In Creative, hide the sanity bar unless sanity is below max (so /backrooms sanity set works smoothly).
		if (creative && ratio >= 0.999F) return;

		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		int x = sw / 2 + 10;
		int y;
		if (creative) {
			y = sh - 34;
		} else {
			boolean airVisible = player.getAirSupply() < player.getMaxAirSupply() || player.isUnderWater();
			boolean mountHealth = player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity;
			y = sh - 50 - (airVisible ? 10 : 0) - (mountHealth ? 10 : 0);
		}

		// Subtle horizontal tremor when sanity drops below 30%.
		if (critical) {
			long jitterSeed = (System.currentTimeMillis() / 90L) ^ player.tickCount;
			x += (int) ((jitterSeed % 3L) - 1L);
		}

		int borderColor = critical
				? ((System.currentTimeMillis() / 220L) % 2L == 0L ? 0xFFE63964 : 0xFF7A1E3D)
				: 0xFF2B3548;
		int fillColor = critical
				? 0xFFD9325C
				: (ratio < 0.60F ? 0xFF9664F2 : 0xFF42CFF2);
		int highlightColor = critical
				? 0xFFFF7A9E
				: (ratio < 0.60F ? 0xFFC8A6FF : 0xFF9BEFFF);

		// Frame + dark backdrop
		graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xCC0D1018);
		graphics.outline(x, y, BAR_WIDTH, BAR_HEIGHT, borderColor);

		// Segmented inner bar
		int innerW = BAR_WIDTH - 2;
		int filledW = Mth.clamp(Math.round(innerW * ratio), 0, innerW);
		if (filledW > 0) {
			graphics.fill(x + 1, y + 1, x + 1 + filledW, y + BAR_HEIGHT - 1, fillColor);
			graphics.fill(x + 1, y + 1, x + 1 + filledW, y + 3, highlightColor);
		}
		// Subtle 10-segment tick marks
		for (int seg = 1; seg < 10; seg++) {
			int sx = x + 1 + (innerW * seg) / 10;
			graphics.fill(sx, y + 1, sx + 1, y + BAR_HEIGHT - 1, 0x55080A10);
		}

		// Compact centered label inside/above bar
		Font font = client.font;
		String label = Component.translatable("hud.noclip_backrooms.sanity",
				Math.round(stats.sanity()), Math.round(stats.maxSanity())).getString();
		int textColor = critical ? 0xFFFFB5C5 : 0xFFEBF5FF;
		graphics.pose().pushMatrix();
		float scale = 0.72F;
		graphics.pose().scale(scale, scale);
		int tx = Math.round((x + BAR_WIDTH / 2.0F) / scale) - font.width(label) / 2;
		int ty = Math.round((y + 1.8F) / scale);
		graphics.text(font, label, tx, ty, textColor, true);
		graphics.pose().popMatrix();
	}

	private static void extractLowSanityVignette(GuiGraphicsExtractor graphics, float ratio, int tickCount) {
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		float severity = Mth.clamp((PlayerStats.HALLUCINATION_THRESHOLD - ratio) / PlayerStats.HALLUCINATION_THRESHOLD, 0.0F, 1.0F);
		float pulse = 0.75F + 0.25F * Mth.sin(tickCount * 0.16F);
		float scare = SanityHallucinations.scareFlashStrength();
		float alphaF = Mth.clamp((severity * 0.42F * pulse) + scare * 0.48F, 0.0F, 0.78F);
		int alpha = (int) (alphaF * 255.0F);
		if (alpha <= 4) return;

		int edgeColor = (alpha << 24) | (scare > 0.1F ? 0x5A0A1E : 0x2B0928);
		int transparent = 0x002B0928;
		int bandH = Math.max(24, (int) (h * (0.14F + severity * 0.10F + scare * 0.08F)));
		int bandW = Math.max(20, (int) (w * (0.08F + severity * 0.06F)));

		// Top and bottom gradient bands
		graphics.fillGradient(0, 0, w, bandH, edgeColor, transparent);
		graphics.fillGradient(0, h - bandH, w, h, transparent, edgeColor);
		// Left and right subtle panic borders
		int sideAlpha = (int) (alpha * 0.65F);
		int sideColor = (sideAlpha << 24) | (scare > 0.1F ? 0x5A0A1E : 0x2B0928);
		graphics.fill(0, 0, bandW / 2, h, sideColor);
		graphics.fill(w - bandW / 2, 0, w, h, sideColor);
	}
}
