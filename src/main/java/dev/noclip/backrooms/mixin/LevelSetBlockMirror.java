package dev.noclip.backrooms.mixin;

import dev.noclip.backrooms.level0.Level0Mirror;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Level 0 repeats its centre Stage: a block changed in one copy is changed in all of them (level0.Level0Mirror). */
@Mixin(Level.class)
public abstract class LevelSetBlockMirror {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
	private void noclipBackrooms$mirror(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> info) {
		if (info.getReturnValueZ() && (Object) this instanceof ServerLevel level) Level0Mirror.changed(level, pos, state);
	}
}
