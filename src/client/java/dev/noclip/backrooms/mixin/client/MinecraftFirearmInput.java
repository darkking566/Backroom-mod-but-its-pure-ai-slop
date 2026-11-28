package dev.noclip.backrooms.mixin.client;

import dev.noclip.backrooms.core.field.FieldKit;
import dev.noclip.backrooms.core.field.Viewfinder;
import dev.noclip.backrooms.weapons.BackroomsFirearmItem;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem;
import dev.noclip.backrooms.weapons.WeaponAnimState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts left-click attack input when holding a Backrooms firearm so clicking fires the weapon
 * (both from hipfire AND while holding Right-Click ADS) instead of mining blocks or punching air,
 * and advances melee combo swing variation on melee attacks.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFirearmInput {
	@Shadow
	public LocalPlayer player;

	@Shadow
	@Final
	public Options options;

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void noclip_backrooms$allowFiringWhileAiming(CallbackInfo ci) {
		if (player == null || !player.isUsingItem()) return;
		if (player.getUseItem().is(FieldKit.CAMERA)) {
			while (options.keyAttack.consumeClick()) Viewfinder.shoot((Minecraft) (Object) this);
		}
		ItemStack held = player.getMainHandItem();
		if (held.getItem() instanceof BackroomsFirearmItem gun) {
			while (options.keyAttack.consumeClick()) {
				WeaponAnimState.tryFireFirearm((Minecraft) (Object) this, held, gun);
			}
		}
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void noclip_backrooms$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
		if (player == null) return;
		ItemStack held = player.getMainHandItem();
		if (held.getItem() instanceof BackroomsFirearmItem gun) {
			WeaponAnimState.tryFireFirearm((Minecraft) (Object) this, held, gun);
			cir.setReturnValue(false);
		} else if (held.getItem() instanceof BackroomsMeleeItem) {
			WeaponAnimState.onMeleeSwing();
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void noclip_backrooms$onContinueAttack(boolean leftClick, CallbackInfo ci) {
		if (player == null) return;
		if (player.getMainHandItem().getItem() instanceof BackroomsFirearmItem) {
			ci.cancel();
		}
	}
}
