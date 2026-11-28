package dev.noclip.backrooms.mixin.client;

import dev.noclip.backrooms.core.field.Viewfinder;
import dev.noclip.backrooms.weapons.BackroomsFirearmItem;
import dev.noclip.backrooms.weapons.WeaponAnimState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zooms player FOV: the camera's viewfinder, and aiming down sights (ADS) with any Backrooms firearm. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerAimFov {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void noclip_backrooms$applyAimFov(boolean isFirstPerson, float fovEffectScale, CallbackInfoReturnable<Float> cir) {
		if (!isFirstPerson) return;
		Minecraft mc = Minecraft.getInstance();
		if ((Object) this != mc.player) return;
		float zoom = Viewfinder.fovZoom(mc);
		if (zoom > 1) {
			cir.setReturnValue(cir.getReturnValue() / zoom);
			return;
		}
		ItemStack held = mc.player.getMainHandItem();
		if (!(held.getItem() instanceof BackroomsFirearmItem gun)) return;

		float ads = WeaponAnimState.adsProgress(0.5F);
		if (ads <= 0.005F) return;
		float zoomMult = Mth.lerp(ads, 1.0F, gun.adsFovMultiplier());
		cir.setReturnValue(cir.getReturnValue() * zoomMult);
	}
}
