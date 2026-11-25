package dev.noclip.backrooms.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.noclip.backrooms.weapons.BlenderWeaponRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders Blender true-curved weapon meshes & glowing Tier 1-3 evolution halos in Third-Person (F5
 * and multiplayer teammates) right inside ItemInHandLayer after vanilla hand positioning!
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerBlenderWeapons {
	@Inject(
			method = "submitArmWithItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
			),
			cancellable = true
	)
	private void noclip_backrooms$renderThirdPersonBlenderWeapon(
			ArmedEntityRenderState renderState,
			ItemStackRenderState itemStackRenderState,
			ItemStack itemStack,
			HumanoidArm arm,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			int light,
			CallbackInfo ci) {
		if (!BlenderWeaponRenderer.hasBlenderMesh(itemStack)) return;
		boolean rightHand = arm == HumanoidArm.RIGHT;
		BlenderWeaponRenderer.renderThirdPerson(itemStack, rightHand, renderState.ageInTicks * 0.12F, poseStack, collector, light);
		poseStack.popPose();
		ci.cancel();
	}
}
