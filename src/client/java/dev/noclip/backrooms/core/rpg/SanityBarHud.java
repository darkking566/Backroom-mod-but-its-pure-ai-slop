package dev.noclip.backrooms.core.rpg;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Renders the player's Sanity as Minecraft-style 9x9 pixel-art Mind-Eye icons directly above the hunger bar.
 * <p>
 * Multi-row safety:
 * <ul>
 *   <li>1 icon = 10 Sanity (half icon = 5 Sanity), 10 icons per row = 100 Sanity per row.</li>
 *   <li>Row 0 anchors cleanly above the Food bar ({@code sh - 49}), shifting further upward whenever underwater
 *       Air bubbles or Mount Health rows are visible.</li>
 *   <li>Additional rows from higher SAN stats strictly stack <b>UPWARD</b> ({@code baseY - row * rowStep}),
 *       matching vanilla Minecraft's multi-row Health compression so Sanity NEVER overlaps Food, Air, or Mount bars.</li>
 * </ul>
 */
public final class SanityBarHud {
	static final Identifier ICONS_TEXTURE = BackroomsMod.id("textures/gui/sanity_icons.png");
	static final int TEX_W = 64;
	static final int TEX_H = 16;
	private static final int ICON_SIZE = 9;
	private static final int ICON_STEP = 8;
	private static final int ICONS_PER_ROW = 10;

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

		// In Creative, hide the sanity icons unless sanity is below max (so /backrooms sanity set shows immediately).
		if (creative && ratio >= 0.999F) return;

		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		int rightEdge = sw / 2 + 91;

		int baseY;
		if (creative) {
			baseY = sh - 34;
		} else {
			int occupiedRightRows = 1; // Row 0 (sh - 39) is Food bar (or row 0 of Mount Health).
			if (player.getVehicle() instanceof LivingEntity mount && mount.showVehicleHealth()) {
				int mountRows = Mth.clamp(Mth.ceil(mount.getMaxHealth() / 20.0F), 1, 3);
				occupiedRightRows = Math.max(occupiedRightRows, mountRows);
			}
			boolean airVisible = player.getAirSupply() < player.getMaxAirSupply() || player.isUnderWater();
			if (airVisible) {
				occupiedRightRows += 1;
			}
			// Anchor Row 0 cleanly above every occupied right-side vanilla row (Food / Mount / Air).
			baseY = sh - 39 - occupiedRightRows * 10;
		}

		// One icon per 10 sanity: 5 icons at the starting 50, one more every five levels (no empty slots for sanity you lack).
		int totalIcons = Math.max(1, Mth.ceil(stats.maxSanity() / 10.0F));
		int totalRows = Mth.ceil(totalIcons / (float) ICONS_PER_ROW);
		// Compress multi-row vertical spacing upward just like vanilla Minecraft's multi-row hearts.
		int rowStep = totalRows <= 1 ? 10 : Math.max(3, 10 - (totalRows - 2));

		int halfUnits = Mth.ceil(stats.sanity() / 5.0F);
		int fullSpriteU = critical ? 45 : (ratio < 0.60F ? 27 : 9);
		int halfSpriteU = fullSpriteU + 9;

		// Draw from top row down to row 0 so lower rows cleanly overlap upper rows when compressed (>2 rows).
		for (int i = totalIcons - 1; i >= 0; i--) {
			int row = i / ICONS_PER_ROW;
			int col = i % ICONS_PER_ROW;

			// Right-to-left placement mirrors vanilla Food & Air bars symmetrically against left-side Hearts.
			int x = rightEdge - col * ICON_STEP - ICON_SIZE;
			int y = baseY - row * rowStep;

			// Subtle vertical jitter per icon when sanity drops below 30% (like vanilla low-HP shaking hearts).
			if (critical) {
				long hash = ((player.tickCount / 2L) * 31L + i * 17L) & 0x7FFFFFFFL;
				if ((hash % 3L) == 0L) {
					y += (int) ((hash % 2L) * 2L - 1L);
				}
			}

			int iconHalfThreshold = i * 2;
			int u;
			if (halfUnits >= iconHalfThreshold + 2) {
				u = fullSpriteU;
			} else if (halfUnits == iconHalfThreshold + 1) {
				u = halfSpriteU;
			} else {
				u = 0; // Empty container
			}

			graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS_TEXTURE, x, y, u, 0, ICON_SIZE, ICON_SIZE, TEX_W, TEX_H);
		}
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

		graphics.fillGradient(0, 0, w, bandH, edgeColor, transparent);
		graphics.fillGradient(0, h - bandH, w, h, transparent, edgeColor);
		int sideAlpha = (int) (alpha * 0.65F);
		int sideColor = (sideAlpha << 24) | (scare > 0.1F ? 0x5A0A1E : 0x2B0928);
		graphics.fill(0, 0, bandW / 2, h, sideColor);
		graphics.fill(w - bandW / 2, 0, w, h, sideColor);
	}
}
