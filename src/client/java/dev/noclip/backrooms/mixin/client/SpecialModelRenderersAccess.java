package dev.noclip.backrooms.mixin.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the mod add its own kind of special item model (core.look.MeshItemRenderer); there is no Fabric hook for it. */
@Mixin(SpecialModelRenderers.class)
public interface SpecialModelRenderersAccess {
	@Accessor("ID_MAPPER")
	static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> idMapper() {
		throw new AssertionError();
	}
}
