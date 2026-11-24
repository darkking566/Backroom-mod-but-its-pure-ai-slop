package dev.noclip.backrooms.mixin.client;

import dev.noclip.backrooms.core.look.Look;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Through the camcorder, the Backrooms fade into black close by (see core.look.Look.darken). */
@Mixin(FogRenderer.class)
public class FogRendererDarkness {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void noclip_backrooms$darken(Camera camera, int renderDistance, DeltaTracker delta, float darkening, ClientLevel level,
			CallbackInfoReturnable<FogData> result) {
		Look.darken(camera, result.getReturnValue());
	}
}
