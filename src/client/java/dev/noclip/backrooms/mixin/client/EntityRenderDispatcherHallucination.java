package dev.noclip.backrooms.mixin.client;

import dev.noclip.backrooms.core.rpg.SanityHallucinations;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pure client-side sanity hallucination hook: when the local player's sanity drops below 30%,
 * other players (teammates) may render locally as Backrooms monsters with no nametag, while
 * remaining completely normal on the server and on every other player's client.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherHallucination {
	@Unique
	private boolean noclip_backrooms$extractingDisguise;

	@Shadow
	public abstract <E extends Entity> EntityRenderState extractEntity(E entity, float partialTick);

	@Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void noclip_backrooms$disguiseTeammate(E entity, float partialTick,
			CallbackInfoReturnable<EntityRenderState> cir) {
		if (noclip_backrooms$extractingDisguise || !(entity instanceof RemotePlayer remote)) return;
		LivingEntity disguise = SanityHallucinations.disguiseFor(remote);
		if (disguise == null) return;
		noclip_backrooms$extractingDisguise = true;
		try {
			EntityRenderState state = extractEntity(disguise, partialTick);
			state.nameTag = null;
			state.nameTagAttachment = null;
			cir.setReturnValue(state);
		} finally {
			noclip_backrooms$extractingDisguise = false;
		}
	}
}
