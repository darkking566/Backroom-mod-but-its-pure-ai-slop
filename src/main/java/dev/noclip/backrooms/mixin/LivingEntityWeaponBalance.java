package dev.noclip.backrooms.mixin;

import dev.noclip.backrooms.core.combat.WeaponBalance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every hit on a living thing passes through the horror balance of the weapons (core.combat.WeaponBalance). */
@Mixin(LivingEntity.class)
abstract class LivingEntityWeaponBalance {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float noclip$balance(float amount, ServerLevel level, DamageSource source) {
		return WeaponBalance.adjust((LivingEntity) (Object) this, source, amount);
	}
}
