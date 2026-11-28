package dev.noclip.backrooms.mixin;

import dev.noclip.backrooms.core.trip.Relics;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Death does not drop M.E.G.'s mapping beacon (core.trip.Relics). */
@Mixin(Inventory.class)
abstract class InventoryKeepsBeacon {
	@Unique
	private List<ItemStack> noclip$aside;

	@Inject(method = "dropAll", at = @At("HEAD"))
	private void noclip$setAside(CallbackInfo ci) {
		noclip$aside = Relics.setAside((Inventory) (Object) this);
	}

	@Inject(method = "dropAll", at = @At("RETURN"))
	private void noclip$putBack(CallbackInfo ci) {
		Relics.putBack((Inventory) (Object) this, noclip$aside);
		noclip$aside = null;
	}
}
