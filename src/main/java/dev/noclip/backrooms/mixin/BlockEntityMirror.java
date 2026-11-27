package dev.noclip.backrooms.mixin;

import dev.noclip.backrooms.level0.Level0Mirror;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Level 0 repeats its centre Stage: what a block entity holds (a chest's things, a sign's words) changed in one copy is
 * changed in all of them (level0.Level0Mirror).
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMirror {
	@Inject(method = "setChanged()V", at = @At("RETURN"))
	private void noclipBackrooms$mirror(CallbackInfo info) {
		BlockEntity self = (BlockEntity) (Object) this;
		if (self.getLevel() instanceof ServerLevel level) Level0Mirror.contentsChanged(level, self);
	}
}
