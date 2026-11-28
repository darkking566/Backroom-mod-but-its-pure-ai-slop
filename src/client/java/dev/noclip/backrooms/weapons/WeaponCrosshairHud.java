package dev.noclip.backrooms.weapons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Replaces the vanilla crosshair with bespoke dynamic weapon crosshairs, an optical Sniper/Railgun
 * scope overlay when aiming down sights, arcane magic rune reticles, tactical melee indicators,
 * and crisp white/crimson Hitmarker feedback.
 */
public final class WeaponCrosshairHud {
	private WeaponCrosshairHud() {
	}

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			// The camera's viewfinder has its own focus brackets.
			if (dev.noclip.backrooms.core.field.Viewfinder.showing(mc)) return;
			if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || mc.gui.hud.isHidden()) {
				vanilla.extractRenderState(graphics, delta);
				return;
			}
			ItemStack held = mc.player.getMainHandItem();
			if (held.getItem() instanceof BackroomsFirearmItem gun) {
				drawFirearmHud(graphics, mc, held, gun, delta);
			} else if (held.getItem() instanceof BackroomsMagicItem magic) {
				drawMagicCrosshair(graphics, mc, held, magic, delta);
			} else if (held.getItem() instanceof BackroomsMeleeItem melee) {
				drawMeleeCrosshair(graphics, mc, held, melee, delta);
			} else {
				vanilla.extractRenderState(graphics, delta);
			}
		});
	}

	/**
	 * Draws a reticle about the exact middle of the screen, two screen pixels to a unit whatever the GUI scale, so it
	 * stays small and centred (drawn on whole GUI pixels it was half a pixel off, and grew with the GUI scale). Unit
	 * pixel (0, 0) is the middle; shapes are drawn symmetric about it.
	 */
	private static void centred(GuiGraphicsExtractor g, Minecraft mc, Runnable draw) {
		var pose = g.pose();
		pose.pushMatrix();
		float k = 2.0F / (float) mc.getWindow().getGuiScale();
		pose.translate(g.guiWidth() / 2.0F, g.guiHeight() / 2.0F);
		pose.scale(k, k);
		pose.translate(-0.5F, -0.5F);
		draw.run();
		pose.popMatrix();
	}

	private static void drawFirearmHud(GuiGraphicsExtractor g, Minecraft mc, ItemStack stack,
			BackroomsFirearmItem gun, DeltaTracker delta) {
		float partial = delta.getGameTimeDeltaPartialTick(true);
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		float ads = WeaponAnimState.adsProgress(partial);
		float kick = WeaponAnimState.recoilKick(partial);

		// Subtle warm muzzle flash corner pulse on firing
		if (WeaponAnimState.muzzleFlashTicks() > 0 && ads < 0.72F) {
			int flashAlpha = gun.fireMode() == BackroomsFirearmItem.FireMode.RAILGUN_ARC ? 0x2248EEFF : 0x22FFCC66;
			g.fill(0, 0, sw, sh, flashAlpha);
		}

		// High-magnification optical sniper/railgun scope overlay when fully aimed
		if (gun.hasOpticalScope() && ads >= 0.72F) {
			drawOpticalScope(g, mc, stack, gun, sw, sh, sw / 2, sh / 2);
			centred(g, mc, () -> drawHitmarker(g, 0, 0));
			return;
		}

		float moveBloom = (float) mc.player.getDeltaMovement().horizontalDistance() * 14.0F;
		float spreadGap = (4.0F + gun.baseSpread() * 75.0F + kick * 10.0F + moveBloom) * (1.0F - ads * 0.72F);
		int gap = Math.max(2, Math.round(spreadGap));
		int color = 0xF0FFFFFF;
		int accent = switch (gun.fireMode()) {
			case RAILGUN_ARC, TASER_STUN -> 0xFF5CEEFF;
			case ACID_SHOTGUN -> 0xFF9AF842;
			case FLAMETHROWER -> 0xFFFF9A38;
			case SONIC_PULSE -> 0xFFD888FF;
			default -> 0xFFFF3E3E;
		};
		float cd = mc.player.getCooldowns().getCooldownPercent(stack, partial);

		centred(g, mc, () -> {
			// Aimed down the sights, the gun's own sights are the aim: no reticle over them.
			if (ads < 0.85F) {
				switch (gun.crosshairStyle()) {
					case SHOTGUN_CIRCLE -> {
						int r = Math.max(5, Math.round((11.0F + kick * 7.0F) * (1.0F - ads * 0.35F)));
						for (int i = 0; i < 16; i++) {
							double a = Math.PI * 2.0 * i / 16;
							drawOutlinedRect(g, (int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r), 1, 1, color);
						}
						drawOutlinedRect(g, 0, 0, 1, 1, accent);
					}
					case ARC_BRACKET -> {
						int br = Math.max(5, gap + 3);
						drawOutlinedRect(g, -br - 1, -5, 1, 11, accent);
						drawOutlinedRect(g, -br, -5, 2, 1, accent);
						drawOutlinedRect(g, -br, 5, 2, 1, accent);
						drawOutlinedRect(g, br + 1, -5, 1, 11, accent);
						drawOutlinedRect(g, br - 1, -5, 2, 1, accent);
						drawOutlinedRect(g, br - 1, 5, 2, 1, accent);
						drawOutlinedRect(g, 0, 0, 1, 1, color);
					}
					default -> {
						int arm = gun.crosshairStyle() == BackroomsFirearmItem.CrosshairStyle.RIFLE_CROSS ? 5 : 3;
						drawOutlinedRect(g, -gap - arm, 0, arm, 1, color);
						drawOutlinedRect(g, gap + 1, 0, arm, 1, color);
						drawOutlinedRect(g, 0, -gap - arm, 1, arm, color);
						drawOutlinedRect(g, 0, gap + 1, 1, arm, color);
						drawOutlinedRect(g, 0, 0, 1, 1, ads > 0.35F ? accent : color);
					}
				}
			}
			// Subtle cooldown recharge bar beneath the reticle
			if (cd > 0.01F) {
				int by = 14;
				g.fill(-11, by - 1, 12, by + 3, 0xA0000000);
				g.fill(-10, by, -10 + Math.round(21 * (1.0F - cd)), by + 2, accent);
			}
			drawHitmarker(g, 0, 0);
		});
	}

	private static void drawOpticalScope(GuiGraphicsExtractor g, Minecraft mc, ItemStack stack,
			BackroomsFirearmItem gun, int sw, int sh, int cx, int cy) {
		int radius = Math.min(sw, sh) * 42 / 100;
		boolean railgun = gun.fireMode() == BackroomsFirearmItem.FireMode.RAILGUN_ARC;
		int reticleColor = railgun ? 0xDD4CEEFF : 0xCC121418;
		int dotColor = railgun ? 0xFF72F8FF : 0xFFFF2C2C;
		int rimColor = railgun ? 0xFF38A4C8 : 0xFF282C32;

		// Subtle optic lens tint (drawn beneath the circular black scope mask so outer corners stay pure black)
		g.fill(cx - radius, cy - radius, cx + radius, cy + radius, railgun ? 0x1040D8FF : 0x0C68B890);

		// Crisp circular black optical scope mask outside lens radius
		g.fill(0, 0, sw, Math.max(0, cy - radius), 0xFF050608);
		g.fill(0, Math.min(sh, cy + radius), sw, sh, 0xFF050608);
		int step = 2;
		for (int dy = -radius; dy < radius; dy += step) {
			double norm = dy / (double) radius;
			int halfChord = (int) Math.round(radius * Math.sqrt(Math.max(0.0, 1.0 - norm * norm)));
			int y0 = cy + dy;
			int y1 = Math.min(sh, y0 + step);
			if (cx - halfChord > 0) g.fill(0, y0, cx - halfChord, y1, 0xFF050608);
			if (cx + halfChord < sw) g.fill(cx + halfChord, y0, sw, y1, 0xFF050608);
			// Subtle bevel rim around circular lens edge
			g.fill(cx - halfChord, y0, cx - halfChord + 2, y1, rimColor);
			g.fill(cx + halfChord - 2, y0, cx + halfChord, y1, rimColor);
		}

		// Fine mil-dot crosshairs spanning full circular lens
		g.fill(cx - radius + 4, cy, cx + radius - 4, cy + 1, reticleColor);
		g.fill(cx, cy - radius + 4, cx + 1, cy + radius - 4, reticleColor);

		// Thick outer stadia bars + mil-dot rangefinder ticks
		g.fill(cx - radius + 4, cy - 1, cx - radius / 2, cy + 2, 0xEE0C0E12);
		g.fill(cx + radius / 2, cy - 1, cx + radius - 4, cy + 2, 0xEE0C0E12);
		g.fill(cx - 1, cy + radius / 2, cx + 2, cy + radius - 4, 0xEE0C0E12);
		for (int t = 1; t <= 4; t++) {
			int d = t * (radius / 10);
			int tick = (t % 2 == 0) ? 4 : 2;
			g.fill(cx - d, cy - tick, cx - d + 1, cy + tick + 1, reticleColor);
			g.fill(cx + d, cy - tick, cx + d + 1, cy + tick + 1, reticleColor);
			g.fill(cx - tick, cy + d, cx + tick + 1, cy + d + 1, reticleColor);
		}

		// Illuminated center dot
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, dotColor);

		// Cooldown / capacitor charge bar inside lower scope arc
		float cd = mc.player.getCooldowns().getCooldownPercent(stack, 0.0F);
		int bw = 48;
		int bx = cx - bw / 2;
		int by = cy + radius - 18;
		g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xB0000000);
		g.fill(bx, by, bx + Math.round(bw * (1.0F - cd)), by + 3, railgun ? 0xFF5CF2FF : 0xFFE6C84C);
	}

	private static void drawMagicCrosshair(GuiGraphicsExtractor g, Minecraft mc, ItemStack stack,
			BackroomsMagicItem magic, DeltaTracker delta) {
		float partial = delta.getGameTimeDeltaPartialTick(true);
		float cast = WeaponAnimState.castAnim(partial);
		float cd = mc.player.getCooldowns().getCooldownPercent(stack, partial);

		int color = switch (magic.spell()) {
			case CRIMSON_SCYTHE -> 0xFFFF3858;
			case VOID_RIFT, NOCLIP_WAND, FATE_DICE -> 0xFFD46EFF;
			case ACID_GRIMOIRE, PLAGUE_CENSER -> 0xFF98F842;
			case STEAM_FLAME, PHOSPHOR_TALISMAN -> 0xFFFF9C38;
			default -> 0xFF66F0FF;
		};

		centred(g, mc, () -> {
			// Diamond rune reticle, opening a little on a cast
			int d = 4 + Math.round(cast * 4.0F);
			drawOutlinedRect(g, 0, -d - 2, 1, 3, color);
			drawOutlinedRect(g, 0, d, 1, 3, color);
			drawOutlinedRect(g, -d - 2, 0, 3, 1, color);
			drawOutlinedRect(g, d, 0, 3, 1, color);
			int k = Math.max(2, d - 2);
			drawOutlinedRect(g, -k, -k, 1, 1, 0xE8FFFFFF);
			drawOutlinedRect(g, k, -k, 1, 1, 0xE8FFFFFF);
			drawOutlinedRect(g, -k, k, 1, 1, 0xE8FFFFFF);
			drawOutlinedRect(g, k, k, 1, 1, 0xE8FFFFFF);
			drawOutlinedRect(g, 0, 0, 1, 1, cd <= 0.01F ? color : 0xAA888888);
			if (cd > 0.01F) {
				int by = 12;
				g.fill(-11, by - 1, 12, by + 3, 0xA0000000);
				g.fill(-10, by, -10 + Math.round(21 * (1.0F - cd)), by + 2, color);
			}
			drawHitmarker(g, 0, 0);
		});
	}

	private static void drawMeleeCrosshair(GuiGraphicsExtractor g, Minecraft mc, ItemStack stack,
			BackroomsMeleeItem melee, DeltaTracker delta) {
		float partial = delta.getGameTimeDeltaPartialTick(true);
		float guard = WeaponAnimState.guardProgress(partial);
		float charge = mc.player.getAttackStrengthScale(partial);
		int mainColor = guard > 0.4F ? 0xFF72E8FF : 0xEEFFFFFF;

		centred(g, mc, () -> {
			drawOutlinedRect(g, -5, 0, 3, 1, mainColor);
			drawOutlinedRect(g, 3, 0, 3, 1, mainColor);
			drawOutlinedRect(g, 0, -5, 1, 3, mainColor);
			drawOutlinedRect(g, 0, 3, 1, 3, mainColor);
			drawOutlinedRect(g, 0, 0, 1, 1, charge >= 0.99F ? 0xFFFFE67A : 0xAA909090);
			// Guard brackets when blocking with Right Click
			if (guard > 0.35F) {
				drawOutlinedRect(g, -9, -4, 2, 9, 0xFF66E4FF);
				drawOutlinedRect(g, 8, -4, 2, 9, 0xFF66E4FF);
			}
			// Swing charge bar
			if (charge < 0.99F) {
				int by = 9;
				g.fill(-10, by - 1, 11, by + 3, 0x90000000);
				g.fill(-9, by, -9 + Math.round(19 * Mth.clamp(charge, 0.0F, 1.0F)), by + 2, 0xFFF2D668);
			}
			drawHitmarker(g, 0, 0);
		});
	}

	private static void drawHitmarker(GuiGraphicsExtractor g, int cx, int cy) {
		int ticks = WeaponAnimState.hitmarkerTicks();
		if (ticks <= 0) return;
		int col = WeaponAnimState.hitmarkerKill() ? 0xFFFF2A3C : 0xFFFFFFFF;
		for (int i = 3; i <= 6; i++) {
			g.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, col);
			g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, col);
			g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, col);
			g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, col);
		}
	}

	private static void drawArcDotCircle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		int steps = 16;
		for (int i = 0; i < steps; i++) {
			double a = (Math.PI * 2.0 * i) / steps;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			drawOutlinedRect(g, x, y, 2, 2, color);
		}
	}

	private static void drawOutlinedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x90000000);
		g.fill(x, y, x + w, y + h, color);
	}
}
