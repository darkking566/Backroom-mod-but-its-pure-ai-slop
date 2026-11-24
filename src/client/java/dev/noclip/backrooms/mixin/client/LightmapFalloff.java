package dev.noclip.backrooms.mixin.client;

import dev.noclip.backrooms.core.look.Look;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Through the camcorder, light in the Backrooms falls off fast: pools under the lamps, dark between them (see Look.falloff). */
@Mixin(Lightmap.class)
public class LightmapFalloff {
	@Inject(method = "render", at = @At("HEAD"))
	private void noclip_backrooms$falloff(LightmapRenderState state, CallbackInfo info) {
		Look.falloff(state);
	}
}
