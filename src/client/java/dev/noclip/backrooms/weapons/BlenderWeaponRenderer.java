package dev.noclip.backrooms.weapons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * In-game Blender true-mesh + full-bright emissive glow renderer for all 80 Backrooms weapons
 * (19 firearms, 20 magic weapons, 41 melee weapons) with live Tier 1 -> Tier 2 -> Tier 3 evolution
 * geometry swapping and animated spinning evolution halos in both First-Person and Third-Person.
 */
public final class BlenderWeaponRenderer {
	private static final Identifier WHITE_TEX = BackroomsMod.id("textures/item/blender_white.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	private record WeaponVisual(
			String meshBase,
			boolean evolvingStaff,
			int primaryGlow,
			int secondaryGlow,
			Kind kind,
			float fpScale,
			float fpOffsetY,
			float fpOffsetZ,
			float haloY,
			float haloZ
	) {
	}

	private enum Kind {
		FIREARM,
		STAFF,
		RELIC,
		MELEE
	}

	private BlenderWeaponRenderer() {
	}

	public static boolean hasBlenderMesh(ItemStack stack) {
		// The realistic items pack (core.look.ItemLook) draws the weapons it lists from ordinary models while it is on.
		return WeaponEvolution.isEvolvable(stack) && !dev.noclip.backrooms.core.look.ItemLook.replaces(stack);
	}

	public static void renderFirstPerson(ItemStack stack, boolean rightHand, float time,
			PoseStack poseStack, SubmitNodeCollector collector, int light) {
		WeaponVisual spec = resolve(stack);
		int evoTier = WeaponEvolution.tier(stack);
		BlenderWeaponMesh mesh = BlenderWeaponMesh.get(meshNameFor(spec, evoTier));
		if (mesh.isEmpty()) return;

		int side = rightHand ? 1 : -1;
		poseStack.pushPose();
		poseStack.translate(0.0F, spec.fpOffsetY, spec.fpOffsetZ);
		if (spec.kind == Kind.STAFF) {
			poseStack.mulPose(Axis.XP.rotationDegrees(-14.0F));
			poseStack.mulPose(Axis.YP.rotationDegrees(side * -12.0F));
		} else if (spec.kind == Kind.RELIC) {
			poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
			poseStack.mulPose(Axis.YP.rotationDegrees(side * -18.0F));
		} else if (spec.kind == Kind.MELEE) {
			poseStack.mulPose(Axis.XP.rotationDegrees(-12.0F));
			poseStack.mulPose(Axis.YP.rotationDegrees(side * -10.0F));
		}
		poseStack.scale(spec.fpScale, spec.fpScale, spec.fpScale);

		submitWeaponAndHalos(mesh, spec, evoTier, time, poseStack, collector, light);
		poseStack.popPose();
	}

	public static void renderThirdPerson(ItemStack stack, boolean rightHand, float time,
			PoseStack poseStack, SubmitNodeCollector collector, int light) {
		WeaponVisual spec = resolve(stack);
		int evoTier = WeaponEvolution.tier(stack);
		BlenderWeaponMesh mesh = BlenderWeaponMesh.get(meshNameFor(spec, evoTier));
		if (mesh.isEmpty()) return;

		poseStack.pushPose();
		// Align Blender/First-Person space (-Z forward, +Y up) with ItemInHandLayer post-transform (+Y forward, -Z up)
		float pitch = spec.kind == Kind.FIREARM ? 90.0F : 76.0F;
		poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
		float tpScale = switch (spec.kind) {
			case FIREARM -> 0.92F;
			case STAFF -> 0.78F;
			case RELIC -> 0.85F;
			case MELEE -> 0.84F;
		};
		poseStack.translate(0.0F, -0.06F, 0.04F);
		poseStack.scale(tpScale, tpScale, tpScale);

		submitWeaponAndHalos(mesh, spec, evoTier, time, poseStack, collector, light);
		poseStack.popPose();
	}

	private static String meshNameFor(WeaponVisual spec, int evoTier) {
		if (spec.evolvingStaff) {
			return "magic_staff_t" + Math.max(1, Math.min(3, evoTier));
		}
		return spec.meshBase;
	}

	private static void submitWeaponAndHalos(BlenderWeaponMesh mesh, WeaponVisual spec, int evoTier,
			float time, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		float pulse = 0.88F + 0.12F * Mth.sin(time * 2.4F);
		float tierBoost = evoTier == 3 ? 1.15F : (evoTier == 2 ? 1.06F : 1.0F);
		int mainRgb = scaleRgb(spec.primaryGlow, pulse * tierBoost);
		int altRgb = scaleRgb(spec.secondaryGlow, (0.86F + 0.14F * Mth.cos(time * 3.1F)) * tierBoost);
		int hotRgb = blendToWhite(spec.primaryGlow, 0.58F);

		submitMesh(mesh, mainRgb, hotRgb, altRgb, poseStack, collector, light);

		// Render animated 3D spinning Evolution Aura Halos on Tier 2 (1 halo) and Tier 3 (dual counter-rotating halos!)
		if (evoTier >= 2) {
			BlenderWeaponMesh halo = BlenderWeaponMesh.get("evo_halo_ring");
			if (!halo.isEmpty()) {
				float spin = time * 42.0F;
				if (!spec.evolvingStaff) {
					// Primary spinning halo around barrel / blade fuller / relic
					poseStack.pushPose();
					poseStack.translate(0.0F, spec.haloY, spec.haloZ);
					if (spec.kind == Kind.FIREARM) {
						poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
						poseStack.scale(0.62F, 0.62F, 0.62F);
					} else {
						poseStack.mulPose(Axis.XP.rotationDegrees(68.0F));
						poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
						poseStack.scale(0.75F, 0.75F, 0.75F);
					}
					submitMesh(halo, mainRgb, hotRgb, altRgb, poseStack, collector, light);
					poseStack.popPose();

					if (evoTier >= 3) {
						// Counter-rotating secondary awakening halo!
						poseStack.pushPose();
						poseStack.translate(0.0F, spec.haloY, spec.haloZ);
						if (spec.kind == Kind.FIREARM) {
							poseStack.mulPose(Axis.YP.rotationDegrees(18.0F));
							poseStack.mulPose(Axis.ZP.rotationDegrees(-spin * 1.35F));
							poseStack.scale(0.84F, 0.84F, 0.84F);
						} else {
							poseStack.mulPose(Axis.XP.rotationDegrees(-62.0F));
							poseStack.mulPose(Axis.YP.rotationDegrees(22.0F));
							poseStack.mulPose(Axis.ZP.rotationDegrees(-spin * 1.35F));
							poseStack.scale(0.96F, 0.96F, 0.96F);
						}
						submitMesh(halo, altRgb, hotRgb, mainRgb, poseStack, collector, light);
						poseStack.popPose();
					}
				} else if (evoTier >= 3) {
					// Tier 3 evolving staffs get a live orbiting crown ring around their crystal!
					poseStack.pushPose();
					poseStack.translate(0.0F, 1.075F, 0.0F);
					poseStack.mulPose(Axis.XP.rotationDegrees(52.0F));
					poseStack.mulPose(Axis.YP.rotationDegrees(spin * 0.85F));
					poseStack.scale(1.18F, 1.18F, 1.18F);
					submitMesh(halo, altRgb, hotRgb, mainRgb, poseStack, collector, light);
					poseStack.popPose();
				}
			}
		}
	}

	private static void submitMesh(BlenderWeaponMesh mesh, int mainRgb, int hotRgb, int altRgb,
			PoseStack poseStack, SubmitNodeCollector collector, int light) {
		// Pass 1: Solid shaded metallic / wood / polymer / blade geometry
		if (mesh.shaded.length > 0) {
			final float[] buf = mesh.shaded;
			final int packedLight = light;
			collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(WHITE_TEX), (pose, vc) -> {
				for (int i = 0; i < buf.length; i += 27) {
					emitShadedVertex(pose, vc, buf, i, packedLight);
					emitShadedVertex(pose, vc, buf, i + 9, packedLight);
					emitShadedVertex(pose, vc, buf, i + 18, packedLight);
					// Duplicate 3rd vertex to satisfy Minecraft's QUADS VertexFormat mode
					emitShadedVertex(pose, vc, buf, i + 18, packedLight);
				}
			});
		}

		// Pass 2: Full-bright opaque emissive geometry (writes depth so floating crystals stay razor-crisp)
		if (mesh.emitMain.length > 0 || mesh.emitHot.length > 0 || mesh.emitAlt.length > 0) {
			final int mR = (mainRgb >> 16) & 0xFF, mG = (mainRgb >> 8) & 0xFF, mB = mainRgb & 0xFF;
			final int hR = (hotRgb >> 16) & 0xFF, hG = (hotRgb >> 8) & 0xFF, hB = hotRgb & 0xFF;
			final int aR = (altRgb >> 16) & 0xFF, aG = (altRgb >> 8) & 0xFF, aB = altRgb & 0xFF;

			collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(WHITE_TEX), (pose, vc) -> {
				emitColoredBucket(pose, vc, mesh.emitMain, mR, mG, mB, 255);
				emitColoredBucket(pose, vc, mesh.emitHot, hR, hG, hB, 255);
				emitColoredBucket(pose, vc, mesh.emitAlt, aR, aG, aB, 255);
			});

			// Pass 3: Additive eyes-layer bloom so glowing cores & halos shine in pitch darkness & shaders!
			final int bmR = (int) (mR * 0.45F), bmG = (int) (mG * 0.45F), bmB = (int) (mB * 0.45F);
			final int bhR = (int) (hR * 0.55F), bhG = (int) (hG * 0.55F), bhB = (int) (hB * 0.55F);
			final int baR = (int) (aR * 0.45F), baG = (int) (aG * 0.45F), baB = (int) (aB * 0.45F);
			collector.submitCustomGeometry(poseStack, RenderTypes.eyes(WHITE_TEX), (pose, vc) -> {
				emitColoredBucket(pose, vc, mesh.emitMain, bmR, bmG, bmB, 255);
				emitColoredBucket(pose, vc, mesh.emitHot, bhR, bhG, bhB, 255);
				emitColoredBucket(pose, vc, mesh.emitAlt, baR, baG, baB, 255);
			});
		}
	}

	private static void emitShadedVertex(PoseStack.Pose pose, VertexConsumer vc, float[] b, int o, int light) {
		int r = Math.max(0, Math.min(255, (int) (b[o + 6] * 255.0F)));
		int g = Math.max(0, Math.min(255, (int) (b[o + 7] * 255.0F)));
		int bl = Math.max(0, Math.min(255, (int) (b[o + 8] * 255.0F)));
		vc.addVertex(pose, b[o], b[o + 1], b[o + 2])
				.setColor(r, g, bl, 255)
				.setUv(0.5F, 0.5F)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, b[o + 3], b[o + 4], b[o + 5]);
	}

	private static void emitColoredBucket(PoseStack.Pose pose, VertexConsumer vc, float[] buf,
			int r, int g, int b, int a) {
		for (int i = 0; i < buf.length; i += 18) {
			emitEmissiveVertex(pose, vc, buf, i, r, g, b, a);
			emitEmissiveVertex(pose, vc, buf, i + 6, r, g, b, a);
			emitEmissiveVertex(pose, vc, buf, i + 12, r, g, b, a);
			emitEmissiveVertex(pose, vc, buf, i + 12, r, g, b, a);
		}
	}

	private static void emitEmissiveVertex(PoseStack.Pose pose, VertexConsumer vc, float[] buf, int o,
			int r, int g, int b, int a) {
		vc.addVertex(pose, buf[o], buf[o + 1], buf[o + 2])
				.setColor(r, g, b, a)
				.setUv(0.5F, 0.5F)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(FULL_BRIGHT)
				.setNormal(pose, buf[o + 3], buf[o + 4], buf[o + 5]);
	}

	private static WeaponVisual resolve(ItemStack stack) {
		Item item = stack.getItem();
		String id = BuiltInRegistries.ITEM.getKey(item).getPath();

		if (item instanceof BackroomsFirearmItem gun) {
			return switch (id) {
				case "gun_railgun" -> new WeaponVisual("gun_railgun", false, 0x1EE5FF, 0xFF2838, Kind.FIREARM, 1.15F, 0.055F, 0.04F, 0.134F, -0.58F);
				case "gun_bolt_sniper" -> new WeaponVisual("gun_sniper", false, 0x38D4FF, 0x32FF66, Kind.FIREARM, 1.12F, 0.072F, 0.04F, 0.144F, -0.60F);
				case "gun_antimateriel_rifle" -> new WeaponVisual("gun_sniper", false, 0xFFB424, 0xFF3424, Kind.FIREARM, 1.18F, 0.068F, 0.04F, 0.144F, -0.60F);
				case "gun_flamethrower" -> new WeaponVisual("gun_flamethrower", false, 0xFF6B18, 0xFFDC3A, Kind.FIREARM, 1.06F, 0.085F, 0.06F, 0.122F, -0.56F);
				case "gun_sonic_pulse" -> new WeaponVisual("gun_sonic_pulse", false, 0xA855FF, 0x2CE8FF, Kind.FIREARM, 1.08F, 0.085F, 0.04F, 0.124F, -0.52F);
				case "office_crossbow" -> new WeaponVisual("office_crossbow", false, 0x4CE4FF, 0xFF4242, Kind.FIREARM, 1.05F, 0.145F, 0.04F, 0.120F, -0.35F);
				case "gun_revolver" -> new WeaponVisual("gun_revolver", false, 0xFFAA28, 0xFF3828, Kind.FIREARM, 1.08F, 0.185F, 0.05F, 0.140F, -0.26F);
				case "gun_pipe_shotgun", "gun_double_barrel", "gun_pump_shotgun" ->
						new WeaponVisual("gun_shotgun", false, 0x3BFF68, 0xFF342C, Kind.FIREARM, 1.10F, 0.125F, 0.05F, 0.135F, -0.48F);
				case "gun_auto_shotgun" ->
						new WeaponVisual("gun_shotgun", false, 0x44FF38, 0xA0FF28, Kind.FIREARM, 1.14F, 0.125F, 0.05F, 0.135F, -0.48F);
				case "gun_pipe_pistol", "gun_nailgun", "gun_pistol_9mm", "gun_heavy_handgun" ->
						new WeaponVisual("gun_pistol", false, 0x38FF72, 0xFF2834, Kind.FIREARM, 1.06F, 0.185F, 0.04F, 0.130F, -0.24F);
				case "gun_taser" ->
						new WeaponVisual("gun_pistol", false, 0x3CE0FF, 0xFFE63C, Kind.FIREARM, 1.06F, 0.185F, 0.04F, 0.130F, -0.24F);
				default -> new WeaponVisual("gun_assault_rifle", false,
						gun.fireMode() == BackroomsFirearmItem.FireMode.SILENT_NAIL ? 0x38FF88 : 0x38CFFF,
						0xFF2838, Kind.FIREARM, 1.12F, 0.095F, 0.04F, 0.138F, -0.46F);
			};
		}

		if (item instanceof BackroomsMagicItem magic) {
			return switch (magic.spell()) {
				case VOID_RIFT -> new WeaponVisual("magic_staff", true, 0xA636FF, 0x1EE5FF, Kind.STAFF, 0.62F, -0.10F, 0.04F, 1.05F, 0.0F);
				case NOCLIP_WAND -> new WeaponVisual("magic_staff", true, 0xD850FF, 0xFFE250, Kind.STAFF, 0.56F, -0.06F, 0.04F, 1.05F, 0.0F);
				case PRISM_LIGHT -> new WeaponVisual("magic_staff", true, 0xFFE466, 0xFFF8D0, Kind.STAFF, 0.62F, -0.10F, 0.04F, 1.05F, 0.0F);
				case STEAM_FLAME -> new WeaponVisual("magic_staff", true, 0xFF6618, 0xFFD438, Kind.STAFF, 0.62F, -0.10F, 0.04F, 1.05F, 0.0F);
				case ARC_LIGHTNING -> new WeaponVisual("magic_staff", true, 0x30C8FF, 0xA8F4FF, Kind.STAFF, 0.62F, -0.10F, 0.04F, 1.05F, 0.0F);
				case STATIC_PULSE -> new WeaponVisual("magic_staff", true, 0x5EE8FF, 0xFFE866, Kind.STAFF, 0.56F, -0.06F, 0.04F, 1.05F, 0.0F);
				case ABYSSAL_FROST -> new WeaponVisual("magic_staff", true, 0x48E0FF, 0xC8F8FF, Kind.STAFF, 0.62F, -0.10F, 0.04F, 1.05F, 0.0F);
				case PLAGUE_CENSER -> new WeaponVisual("magic_staff", true, 0x48FF58, 0xC4FF38, Kind.STAFF, 0.60F, -0.08F, 0.04F, 1.05F, 0.0F);
				case FATE_DICE -> new WeaponVisual("magic_staff", true, 0xFFCC28, 0xD848FF, Kind.STAFF, 0.62F, -0.10F, 0.04F, 1.05F, 0.0F);
				case CLUMP_GRAPPLE -> new WeaponVisual("magic_staff", true, 0xE64455, 0xFFAA44, Kind.STAFF, 0.56F, -0.06F, 0.04F, 1.05F, 0.0F);
				case CRIMSON_SCYTHE -> new WeaponVisual("magic_crimson_scythe", false, 0xFF2038, 0xFF7048, Kind.STAFF, 0.62F, -0.10F, 0.04F, 0.80F, -0.20F);
				case ACID_GRIMOIRE -> new WeaponVisual("magic_grimoire", false, 0x44FF38, 0xC0FF40, Kind.RELIC, 0.86F, -0.04F, 0.06F, 0.34F, 0.0F);
				case PARTY_GRIMOIRE -> new WeaponVisual("magic_grimoire", false, 0xFF42B8, 0xFFE038, Kind.RELIC, 0.86F, -0.04F, 0.06F, 0.34F, 0.0F);
				case ASTRAL_CODEX -> new WeaponVisual("magic_grimoire", false, 0x42D8FF, 0xFFE666, Kind.RELIC, 0.88F, -0.04F, 0.06F, 0.34F, 0.0F);
				case SCREECH_BELL -> new WeaponVisual("magic_bell", false, 0x42D8FF, 0xA866FF, Kind.RELIC, 0.84F, -0.04F, 0.05F, 0.38F, 0.0F);
				case BEAST_SUMMON_BELL -> new WeaponVisual("magic_bell", false, 0xB838FF, 0xFF3850, Kind.RELIC, 0.86F, -0.04F, 0.05F, 0.38F, 0.0F);
				case ALMOND_CENSER -> new WeaponVisual("magic_bell", false, 0xFFD85C, 0x70FFAA, Kind.RELIC, 0.84F, -0.04F, 0.05F, 0.38F, 0.0F);
				case STOPMOTION_WATCH -> new WeaponVisual("magic_watch", false, 0x44E6FF, 0xFFD248, Kind.RELIC, 0.88F, -0.04F, 0.06F, 0.18F, 0.0F);
				case DECOY_MIRROR -> new WeaponVisual("magic_watch", false, 0x9CEEFF, 0xD8A8FF, Kind.RELIC, 0.86F, -0.04F, 0.06F, 0.18F, 0.0F);
				case PHOSPHOR_TALISMAN -> new WeaponVisual("magic_watch", false, 0xFF9828, 0xFFF066, Kind.RELIC, 0.82F, -0.04F, 0.06F, 0.18F, 0.0F);
			};
		}

		// Melee weapons (all 41 mapped cleanly to 5 sculpted Blender silhouettes + trait glow tint!)
		BackroomsMeleeItem melee = (BackroomsMeleeItem) item;
		int[] glow = meleeGlowColors(melee.trait());
		String mesh = meleeMeshName(id, melee);
		float scale = switch (mesh) {
			case "melee_polearm" -> 0.58F;
			case "melee_dagger" -> 0.82F;
			case "melee_warhammer" -> 0.72F;
			default -> 0.68F;
		};
		float haloY = switch (mesh) {
			case "melee_polearm" -> 1.02F;
			case "melee_warhammer" -> 0.78F;
			case "melee_dagger" -> 0.36F;
			default -> 0.56F;
		};
		return new WeaponVisual(mesh, false, glow[0], glow[1], Kind.MELEE, scale, -0.07F, 0.04F, haloY, 0.0F);
	}

	private static String meleeMeshName(String id, BackroomsMeleeItem melee) {
		if (id.contains("guandao") || id.contains("fangtian") || id.contains("spear")
				|| id.contains("halberd") || id.contains("lucerne") || id.contains("staff")) {
			return "melee_polearm";
		}
		if (id.contains("dao") || id.contains("scimitar")) {
			return "melee_curved_dao";
		}
		if (id.contains("knife") || id.contains("dagger") || id.contains("rapier") || id.contains("darts")) {
			return "melee_dagger";
		}
		if (melee.style() == BackroomsMeleeItem.Style.BLUNT
				|| id.contains("axe") || id.contains("hammer") || id.contains("nunchaku")) {
			return "melee_warhammer";
		}
		return "melee_greatsword";
	}

	private static int[] meleeGlowColors(BackroomsMeleeItem.Trait trait) {
		return switch (trait) {
			case VOID_WAVE -> new int[]{0xA836FF, 0x20E8FF};
			case THUNDER_AOE -> new int[]{0x32C8FF, 0xA8F6FF};
			case CRIMSON_SHOCKWAVE, ALPHA_BLOODLUST, VAMPIRIC_CLEANSE -> new int[]{0xFF243C, 0xFF9838};
			case ACID_CORRODE -> new int[]{0x48FF38, 0xB8FF44};
			case UNDEAD_SMILER_BANE, COIN_WARD, PEACHWOOD_BANE -> new int[]{0xFFE052, 0xFFF8C0};
			case STOPMOTION_FREEZE -> new int[]{0x48E8FF, 0xFFD452};
			case BLEED, BACKSTAB, EXECUTE -> new int[]{0xE83248, 0xFF8844};
			case BOSS_BREAKER, ARMOR_PIERCE_45, ARMOR_PIERCE_35 -> new int[]{0xFF8824, 0xFFE066};
			default -> new int[]{0x66D8FF, 0xC8F0FF};
		};
	}

	private static int scaleRgb(int rgb, float factor) {
		int r = Math.max(0, Math.min(255, (int) (((rgb >> 16) & 0xFF) * factor)));
		int g = Math.max(0, Math.min(255, (int) (((rgb >> 8) & 0xFF) * factor)));
		int b = Math.max(0, Math.min(255, (int) ((rgb & 0xFF) * factor)));
		return (r << 16) | (g << 8) | b;
	}

	private static int blendToWhite(int rgb, float amt) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.min(255, (int) (r + (255 - r) * amt));
		g = Math.min(255, (int) (g + (255 - g) * amt));
		b = Math.min(255, (int) (b + (255 - b) * amt));
		return (r << 16) | (g << 8) | b;
	}
}
