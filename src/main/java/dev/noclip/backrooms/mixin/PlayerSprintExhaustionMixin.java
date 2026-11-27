package dev.noclip.backrooms.mixin;

import dev.noclip.backrooms.core.rpg.PlayerRpg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Agility slightly lowers hunger exhaustion consumed while sprinting. */
@Mixin(Player.class)
public abstract class PlayerSprintExhaustionMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float noclipBackrooms$scaleSprintExhaustion(float exhaustion) {
		if ((Object) this instanceof ServerPlayer sp && sp.isSprinting() && exhaustion > 0.0F) {
			return exhaustion * PlayerRpg.get(sp).sprintExhaustionScale();
		}
		return exhaustion;
	}
}
