package dev.noclip.backrooms.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the camcorder filter switch on a post effect the way spectating a creeper does (see core.look.Look). */
@Mixin(GameRenderer.class)
public interface GameRendererAccess {
	@Invoker("setPostEffect")
	void noclip_backrooms$setPostEffect(Identifier id);
}
