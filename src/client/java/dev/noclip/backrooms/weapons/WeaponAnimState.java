package dev.noclip.backrooms.weapons;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Smooth client-side weapon animation state: firearm ADS interpolation, damped recoil kick/pitch/roll,
 * muzzle flash pulse, automatic hold-to-fire trigger, melee combo alternation, magic cast pulse, and hitmarkers.
 */
public final class WeaponAnimState {
	private static float adsProgress = 0.0F;
	private static float prevAdsProgress = 0.0F;

	private static float guardProgress = 0.0F;
	private static float prevGuardProgress = 0.0F;

	private static float recoilKick = 0.0F;
	private static float prevRecoilKick = 0.0F;
	private static float recoilPitch = 0.0F;
	private static float prevRecoilPitch = 0.0F;
	private static float recoilRoll = 0.0F;

	private static float castAnim = 0.0F;
	private static float prevCastAnim = 0.0F;

	private static int muzzleFlashTicks = 0;
	private static int hitmarkerTicks = 0;
	private static boolean hitmarkerKill = false;
	private static int meleeComboStep = 0;
	private static int clientFireDelay = 0;

	private WeaponAnimState() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(WeaponHitPayload.TYPE, (payload, context) -> {
			hitmarkerTicks = payload.killed() ? 9 : 6;
			hitmarkerKill = payload.killed();
			Minecraft mc = context.client();
			if (mc.player != null) {
				mc.player.playSound(
						payload.killed() ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.ARROW_HIT_PLAYER,
						0.45F,
						payload.killed() ? 1.55F : 1.85F
				);
			}
		});

		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClientSide() && hand == InteractionHand.MAIN_HAND) {
				ItemStack stack = player.getItemInHand(hand);
				if (stack.getItem() instanceof BackroomsMagicItem
						|| (stack.getItem() instanceof BackroomsMeleeItem melee && melee.hasActiveRightClickSkill())) {
					if (!player.getCooldowns().isOnCooldown(stack)) {
						castAnim = 1.0F;
					}
				}
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(WeaponAnimState::tick);
	}

	private static void tick(Minecraft mc) {
		prevAdsProgress = adsProgress;
		prevGuardProgress = guardProgress;
		prevRecoilKick = recoilKick;
		prevRecoilPitch = recoilPitch;
		prevCastAnim = castAnim;

		if (muzzleFlashTicks > 0) muzzleFlashTicks--;
		if (hitmarkerTicks > 0) hitmarkerTicks--;
		if (clientFireDelay > 0) clientFireDelay--;

		// Smooth spring decay for recoil & cast pulse
		recoilKick *= 0.62F;
		if (recoilKick < 0.004F) recoilKick = 0.0F;
		recoilPitch *= 0.68F;
		if (recoilPitch < 0.004F) recoilPitch = 0.0F;
		recoilRoll *= 0.66F;
		if (Math.abs(recoilRoll) < 0.004F) recoilRoll = 0.0F;
		castAnim *= 0.72F;
		if (castAnim < 0.005F) castAnim = 0.0F;

		if (mc.player == null || mc.gui.screen() != null) {
			adsProgress = Mth.lerp(0.35F, adsProgress, 0.0F);
			guardProgress = Mth.lerp(0.35F, guardProgress, 0.0F);
			return;
		}

		ItemStack held = mc.player.getMainHandItem();
		boolean holdingRight = mc.options.keyUse.isDown();

		if (held.getItem() instanceof BackroomsFirearmItem gun) {
			float targetAds = holdingRight ? 1.0F : 0.0F;
			float speed = gun.hasOpticalScope() ? 0.30F : 0.40F;
			adsProgress = Mth.lerp(speed, adsProgress, targetAds);
			if (Math.abs(adsProgress - targetAds) < 0.01F) adsProgress = targetAds;
			guardProgress = Mth.lerp(0.4F, guardProgress, 0.0F);

			// Smooth automatic hold-to-fire support
			if (gun.isAutomatic() && mc.options.keyAttack.isDown()) {
				tryFireFirearm(mc, held, gun);
			}
		} else {
			adsProgress = Mth.lerp(0.45F, adsProgress, 0.0F);
			if (adsProgress < 0.01F) adsProgress = 0.0F;

			if (held.getItem() instanceof BackroomsMeleeItem melee && !melee.hasActiveRightClickSkill() && holdingRight) {
				guardProgress = Mth.lerp(0.38F, guardProgress, 1.0F);
			} else {
				guardProgress = Mth.lerp(0.38F, guardProgress, 0.0F);
				if (guardProgress < 0.01F) guardProgress = 0.0F;
			}
		}

		// Subtle client-only ambient emissive weapon particles (Iron's Spells / TACZ energy shimmer)
		spawnHeldWeaponGlowParticles(mc, held);
	}

	private static void spawnHeldWeaponGlowParticles(Minecraft mc, ItemStack held) {
		if (mc.level == null || mc.player == null || mc.player.tickCount % 4 != 0) return;
		if (isInOpticalScope(mc, 1.0F)) return;

		ParticleOptions particle = null;
		if (held.getItem() instanceof BackroomsMagicItem magic) {
			particle = switch (magic.spell()) {
				case VOID_RIFT, NOCLIP_WAND, FATE_DICE, PARTY_GRIMOIRE -> ParticleTypes.PORTAL;
				case ARC_LIGHTNING, STATIC_PULSE -> ParticleTypes.ELECTRIC_SPARK;
				case STEAM_FLAME, PHOSPHOR_TALISMAN -> ParticleTypes.FLAME;
				case CRIMSON_SCYTHE -> ParticleTypes.CRIMSON_SPORE;
				case ABYSSAL_FROST -> ParticleTypes.SNOWFLAKE;
				case ACID_GRIMOIRE, PLAGUE_CENSER -> ParticleTypes.SPORE_BLOSSOM_AIR;
				default -> ParticleTypes.END_ROD;
			};
		} else if (held.getItem() instanceof BackroomsFirearmItem gun) {
			particle = switch (gun.fireMode()) {
				case RAILGUN_ARC, TASER_STUN -> ParticleTypes.ELECTRIC_SPARK;
				case FLAMETHROWER -> ParticleTypes.FLAME;
				case ACID_SHOTGUN -> ParticleTypes.SPORE_BLOSSOM_AIR;
				case SONIC_PULSE -> ParticleTypes.END_ROD;
				default -> null;
			};
		} else if (held.getItem() instanceof BackroomsMeleeItem melee) {
			particle = switch (melee.trait()) {
				case VOID_WAVE -> ParticleTypes.PORTAL;
				case THUNDER_AOE -> ParticleTypes.ELECTRIC_SPARK;
				case CRIMSON_SHOCKWAVE, ALPHA_BLOODLUST, VAMPIRIC_CLEANSE -> ParticleTypes.CRIMSON_SPORE;
				case ACID_CORRODE -> ParticleTypes.SPORE_BLOSSOM_AIR;
				case UNDEAD_SMILER_BANE, COIN_WARD, STOPMOTION_FREEZE -> ParticleTypes.END_ROD;
				default -> null;
			};
		}
		if (particle == null) return;

		Vec3 look = mc.player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() > 1.0E-4) right = right.normalize();
		double side = mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 0.36 : -0.36;
		double jitter = (mc.player.getRandom().nextDouble() - 0.5) * 0.14;
		Vec3 pos = mc.player.getEyePosition()
				.add(look.scale(0.62))
				.add(right.scale(side + jitter))
				.add(0, -0.22 + jitter, 0);
		mc.level.addParticle(particle, pos.x, pos.y, pos.z, 0.0, 0.015, 0.0);
	}

	public static void tryFireFirearm(Minecraft mc, ItemStack stack, BackroomsFirearmItem gun) {
		if (mc.player == null || mc.gui.screen() != null) return;
		if (clientFireDelay > 0 || mc.player.getCooldowns().isOnCooldown(stack)) return;

		boolean aiming = adsProgress > 0.45F || mc.options.keyUse.isDown();
		ClientPlayNetworking.send(new FireWeaponPayload(aiming));
		if (!gun.hasAmmo(mc.player)) {
			clientFireDelay = 8;
			return;
		}
		clientFireDelay = Math.max(2, gun.cooldownTicks());

		// Immediate zero-latency client recoil + slight camera pitch kick + muzzle flash!
		float strength = gun.recoilStrength() * (aiming ? 0.58F : 1.0F);
		recoilKick = Math.min(1.6F, recoilKick + 0.55F * strength);
		recoilPitch = Math.min(2.2F, recoilPitch + 0.85F * strength);
		recoilRoll = (mc.player.getRandom().nextFloat() - 0.5F) * 0.45F * strength;
		if (gun.fireMode() != BackroomsFirearmItem.FireMode.SILENT_NAIL) {
			muzzleFlashTicks = 2;
		}
		mc.player.setXRot(Mth.clamp(mc.player.getXRot() - 1.15F * strength, -90.0F, 90.0F));
	}

	public static void onMeleeSwing() {
		meleeComboStep = (meleeComboStep + 1) % 3;
	}

	public static float adsProgress(float partial) {
		return Mth.lerp(partial, prevAdsProgress, adsProgress);
	}

	public static float guardProgress(float partial) {
		return Mth.lerp(partial, prevGuardProgress, guardProgress);
	}

	public static float recoilKick(float partial) {
		return Mth.lerp(partial, prevRecoilKick, recoilKick);
	}

	public static float recoilPitch(float partial) {
		return Mth.lerp(partial, prevRecoilPitch, recoilPitch);
	}

	public static float recoilRoll() {
		return recoilRoll;
	}

	public static float castAnim(float partial) {
		return Mth.lerp(partial, prevCastAnim, castAnim);
	}

	public static int muzzleFlashTicks() {
		return muzzleFlashTicks;
	}

	public static int hitmarkerTicks() {
		return hitmarkerTicks;
	}

	public static boolean hitmarkerKill() {
		return hitmarkerKill;
	}

	public static int meleeComboStep() {
		return meleeComboStep;
	}

	public static boolean isInOpticalScope(Minecraft mc, float partial) {
		if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return false;
		return mc.player.getMainHandItem().getItem() instanceof BackroomsFirearmItem gun
				&& gun.hasOpticalScope()
				&& adsProgress(partial) >= 0.72F;
	}
}
