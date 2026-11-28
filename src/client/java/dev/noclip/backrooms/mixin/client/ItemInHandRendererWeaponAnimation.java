package dev.noclip.backrooms.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.noclip.backrooms.core.field.Viewfinder;
import dev.noclip.backrooms.weapons.BackroomsFirearmItem;
import dev.noclip.backrooms.weapons.BackroomsMagicItem;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem;
import dev.noclip.backrooms.weapons.BlenderWeaponRenderer;
import dev.noclip.backrooms.weapons.WeaponAnimState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bespoke first-person 3D weapon animations for all Backrooms firearms (hipfire sway, smooth ADS eye-line
 * alignment, recoil kick/pitch/roll, optical scope hide), melee weapons (combo-alternating blunt slams,
 * western diagonal cleaves & rapier thrusts, chinese sweeping cuts & spear lunges, right-click guard stance),
 * and magic armaments (staff spell thrusts, grimoire hovering open, ritual bell ringing, watch lift).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererWeaponAnimation {
	@Shadow
	public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			PoseStack poseStack, SubmitNodeCollector collector, int light);

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void noclip_backrooms$animateWeapon(AbstractClientPlayer player, float partialTicks, float pitch,
			InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
			PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		// Nothing in front of the lens while the camera is held up.
		if (Viewfinder.showing(Minecraft.getInstance())) {
			ci.cancel();
			return;
		}
		boolean isMain = hand == InteractionHand.MAIN_HAND;
		if (!isMain) return;

		boolean isFirearm = stack.getItem() instanceof BackroomsFirearmItem;
		boolean isMelee = stack.getItem() instanceof BackroomsMeleeItem;
		boolean isMagic = stack.getItem() instanceof BackroomsMagicItem;
		if (!isFirearm && !isMelee && !isMagic) return;

		HumanoidArm arm = player.getMainArm();
		boolean rightHand = arm == HumanoidArm.RIGHT;
		int side = rightHand ? 1 : -1;
		ItemDisplayContext ctx = rightHand ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		float time = (player.tickCount + partialTicks) * 0.12F;

		// Hide gun model completely once fully inside an optical sniper/railgun scope
		if (isFirearm && WeaponAnimState.isInOpticalScope(Minecraft.getInstance(), partialTicks)) {
			ci.cancel();
			return;
		}

		poseStack.pushPose();
		if (isFirearm) {
			animateFirearm((BackroomsFirearmItem) stack.getItem(), side, equipProgress, partialTicks, time, poseStack);
		} else if (isMelee) {
			animateMelee((BackroomsMeleeItem) stack.getItem(), side, swingProgress, equipProgress, partialTicks, time, poseStack);
		} else {
			animateMagic((BackroomsMagicItem) stack.getItem(), side, swingProgress, equipProgress, partialTicks, time, poseStack);
		}

		if (BlenderWeaponRenderer.hasBlenderMesh(stack)) {
			BlenderWeaponRenderer.renderFirstPerson(stack, rightHand, time, poseStack, collector, light);
		} else {
			renderItem(player, stack, ctx, poseStack, collector, light);
		}
		poseStack.popPose();
		ci.cancel();
	}

	private static void animateFirearm(BackroomsFirearmItem gun, int side, float equipProgress,
			float partialTicks, float time, PoseStack pose) {
		float ads = WeaponAnimState.adsProgress(partialTicks);
		float kick = WeaponAnimState.recoilKick(partialTicks);
		float pitchKick = WeaponAnimState.recoilPitch(partialTicks);
		float rollKick = WeaponAnimState.recoilRoll();

		// Smoothly glide 3D gun from hipfire side pose into dead-center sight-aligned ADS pose!
		float hipX = side * (gun.isLongGun() ? 0.44F : 0.48F);
		float hipY = (gun.isLongGun() ? -0.44F : -0.46F) - equipProgress * 0.55F;
		float hipZ = gun.isLongGun() ? -0.72F : -0.78F;

		float adsX = 0.0F;
		float adsY = (gun.isLongGun() ? -0.34F : -0.38F) - equipProgress * 0.35F;
		float adsZ = gun.isLongGun() ? -0.52F : -0.60F;

		float swayScale = 1.0F - ads * 0.85F;
		float swayX = Mth.sin(time * 0.7F) * 0.008F * swayScale;
		float swayY = Mth.cos(time * 1.4F) * 0.007F * swayScale;

		float x = Mth.lerp(ads, hipX, adsX) + swayX;
		float y = Mth.lerp(ads, hipY, adsY) + swayY + kick * 0.025F;
		float z = Mth.lerp(ads, hipZ, adsZ) + kick * 0.14F;

		pose.translate(x, y, z);
		// Recoil muzzle climb + slight roll shake + hipfire cant flattening to 0 on ADS
		pose.mulPose(Axis.XP.rotationDegrees(pitchKick * 12.0F));
		pose.mulPose(Axis.YP.rotationDegrees(side * (1.0F - ads) * -3.5F));
		pose.mulPose(Axis.ZP.rotationDegrees(side * (1.0F - ads) * -4.0F + rollKick * 10.0F));
	}

	private static void animateMelee(BackroomsMeleeItem melee, int side, float swing, float equipProgress,
			float partialTicks, float time, PoseStack pose) {
		float guard = WeaponAnimState.guardProgress(partialTicks);
		float cast = WeaponAnimState.castAnim(partialTicks);

		float baseX = side * 0.54F;
		float baseY = -0.50F - equipProgress * 0.6F + Mth.sin(time) * 0.008F;
		float baseZ = -0.74F;

		// Right-click defensive blade guard / parry stance
		if (guard > 0.01F) {
			pose.translate(
					Mth.lerp(guard, baseX, side * 0.16F),
					Mth.lerp(guard, baseY, -0.36F),
					Mth.lerp(guard, baseZ, -0.58F)
			);
			pose.mulPose(Axis.XP.rotationDegrees(guard * -18.0F));
			pose.mulPose(Axis.YP.rotationDegrees(side * guard * 55.0F));
			pose.mulPose(Axis.ZP.rotationDegrees(side * guard * 52.0F));
			return;
		}

		pose.translate(baseX, baseY, baseZ);

		// Active melee skill forward sweep/lunge pulse (Void Wave, Meteor Pull, Sleeve Darts, Coin Ward)
		if (cast > 0.01F) {
			float pulse = Mth.sin(cast * Mth.PI);
			pose.translate(-side * pulse * 0.18F, pulse * 0.14F, -pulse * 0.36F);
			pose.mulPose(Axis.XP.rotationDegrees(-pulse * 55.0F));
			pose.mulPose(Axis.YP.rotationDegrees(side * pulse * 24.0F));
			return;
		}

		if (swing <= 0.001F) return;
		float s = Mth.sin(swing * Mth.PI);
		float sSq = Mth.sin(Mth.sqrt(swing) * Mth.PI);
		int combo = WeaponAnimState.meleeComboStep();

		if (melee.isThrustStyle()) {
			// Crisp forward martial thrust (Rapier, Spear, Han Jian, Fangtian Halberd)
			pose.translate(-side * s * 0.16F, s * 0.08F, -sSq * 0.52F);
			pose.mulPose(Axis.XP.rotationDegrees(-sSq * 68.0F));
			pose.mulPose(Axis.YP.rotationDegrees(side * s * 14.0F));
		} else if (melee.style() == BackroomsMeleeItem.Style.BLUNT) {
			// Heavy overhead wind-up -> crushing downward slam
			float arc = swing < 0.35F ? -(swing / 0.35F) : ((swing - 0.35F) / 0.65F);
			pose.translate(-side * s * 0.14F, -arc * 0.24F + 0.12F * s, -sSq * 0.32F);
			pose.mulPose(Axis.XP.rotationDegrees(-35.0F - arc * 95.0F));
			pose.mulPose(Axis.ZP.rotationDegrees(side * s * 18.0F));
		} else if (melee.style() == BackroomsMeleeItem.Style.CHINESE) {
			// Fluid alternating horizontal slash & reverse diagonal upward cut
			if (combo == 0) {
				pose.translate(-side * sSq * 0.56F, -s * 0.08F, -s * 0.26F);
				pose.mulPose(Axis.YP.rotationDegrees(side * (-20.0F + sSq * 105.0F)));
				pose.mulPose(Axis.ZP.rotationDegrees(side * 64.0F * s));
				pose.mulPose(Axis.XP.rotationDegrees(-38.0F * s));
			} else if (combo == 1) {
				pose.translate(-side * (0.45F - sSq * 0.62F), -0.18F + sSq * 0.36F, -s * 0.24F);
				pose.mulPose(Axis.YP.rotationDegrees(side * (65.0F - sSq * 95.0F)));
				pose.mulPose(Axis.ZP.rotationDegrees(side * -52.0F * s));
				pose.mulPose(Axis.XP.rotationDegrees(-25.0F * s));
			} else {
				pose.translate(-side * s * 0.20F, s * 0.06F, -sSq * 0.44F);
				pose.mulPose(Axis.XP.rotationDegrees(-sSq * 74.0F));
			}
		} else {
			// Western alternating diagonal cleaves
			float dir = (combo % 2 == 0) ? 1.0F : -1.0F;
			pose.translate(-side * dir * sSq * 0.42F, -s * 0.16F, -s * 0.28F);
			pose.mulPose(Axis.YP.rotationDegrees(side * dir * sSq * 78.0F));
			pose.mulPose(Axis.XP.rotationDegrees(-sSq * 65.0F));
			pose.mulPose(Axis.ZP.rotationDegrees(side * dir * s * 36.0F));
		}
	}

	private static void animateMagic(BackroomsMagicItem magic, int side, float swing, float equipProgress,
			float partialTicks, float time, PoseStack pose) {
		float cast = WeaponAnimState.castAnim(partialTicks);
		float pulse = Mth.sin(cast * Mth.PI);
		float hover = Mth.sin(time * 1.1F) * 0.018F;

		pose.translate(side * 0.50F, -0.48F - equipProgress * 0.55F + hover, -0.74F);

		switch (magic.spell()) {
			case ACID_GRIMOIRE, PARTY_GRIMOIRE, ASTRAL_CODEX -> {
				// Raise grimoire toward center & tilt open while pulsing arcane energy
				pose.translate(-side * pulse * 0.24F, pulse * 0.18F, -pulse * 0.16F);
				pose.mulPose(Axis.YP.rotationDegrees(side * (16.0F + pulse * 36.0F)));
				pose.mulPose(Axis.XP.rotationDegrees(-pulse * 22.0F));
			}
			case SCREECH_BELL, BEAST_SUMMON_BELL -> {
				// Crisp damped bell-ringing oscillation!
				float ring = Mth.sin(cast * Mth.PI * 5.0F) * cast;
				pose.translate(-side * pulse * 0.14F, pulse * 0.16F, -pulse * 0.14F);
				pose.mulPose(Axis.ZP.rotationDegrees(ring * 32.0F));
				pose.mulPose(Axis.XP.rotationDegrees(-pulse * 18.0F));
			}
			case STOPMOTION_WATCH, DECOY_MIRROR, ALMOND_CENSER -> {
				// Lift smoothly to eye level and pulse
				pose.translate(-side * pulse * 0.28F, pulse * 0.22F, -pulse * 0.18F);
				pose.mulPose(Axis.XP.rotationDegrees(-pulse * 15.0F));
			}
			default -> {
				// Staff / Wand / Scythe dramatic arcane thrust + optional melee swing
				if (swing > 0.001F) {
					float s = Mth.sin(Mth.sqrt(swing) * Mth.PI);
					pose.translate(-side * s * 0.36F, -s * 0.12F, -s * 0.26F);
					pose.mulPose(Axis.XP.rotationDegrees(-s * 68.0F));
					pose.mulPose(Axis.YP.rotationDegrees(side * s * 62.0F));
				}
				if (cast > 0.01F) {
					pose.translate(-side * pulse * 0.14F, pulse * 0.16F, -pulse * 0.34F);
					pose.mulPose(Axis.XP.rotationDegrees(-pulse * 48.0F));
					pose.mulPose(Axis.ZP.rotationDegrees(side * pulse * 14.0F));
				}
			}
		}
	}
}
