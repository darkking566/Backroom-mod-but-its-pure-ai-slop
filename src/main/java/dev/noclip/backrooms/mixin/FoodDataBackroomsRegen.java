package dev.noclip.backrooms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** In the Backrooms a full stomach heals at a quarter of the usual speed (docs/HORROR_ESCAPE_PLAN.md). */
@Mixin(FoodData.class)
public abstract class FoodDataBackroomsRegen {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
	private void noclipBackrooms$slowInTheBackrooms(ServerPlayer player, float amount, Operation<Void> original) {
		original.call(player, PlayerRpg.inBackrooms(player) ? amount * PlayerRpg.BACKROOMS_FOOD_REGEN : amount);
	}
}
