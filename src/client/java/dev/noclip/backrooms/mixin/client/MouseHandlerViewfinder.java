package dev.noclip.backrooms.mixin.client;

import dev.noclip.backrooms.core.field.Viewfinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the camera is held up, the wheel works the viewfinder (not the hotbar) and the mouse turns as finely as it is zoomed. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerViewfinder {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void noclip_backrooms$viewfinderWheel(long handle, double x, double y, CallbackInfo ci) {
		if (handle != minecraft.getWindow().handle() || minecraft.gui.overlay() != null || minecraft.gui.screen() != null
				|| !Viewfinder.showing(minecraft)) return;
		// Some systems turn the wheel sideways while shift is held.
		Viewfinder.scroll(y != 0 ? y : x, minecraft.options.keyShift.isDown());
		ci.cancel();
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double noclip_backrooms$zoomedYaw(double yaw) {
		return yaw / Viewfinder.fovZoom(minecraft);
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double noclip_backrooms$zoomedPitch(double pitch) {
		return pitch / Viewfinder.fovZoom(minecraft);
	}
}
