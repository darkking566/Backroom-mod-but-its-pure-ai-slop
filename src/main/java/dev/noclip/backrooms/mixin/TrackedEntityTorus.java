package dev.noclip.backrooms.mixin;

import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0.Level0Space;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Level 0 is a torus (level0.Level0Space): a monster by the far edge of the Stage is also just past the near edge.
 * Players are sent the things that are near them round the torus, not only in a straight line, so the client can show
 * them past the edge (level0.Level0Ghosts).
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityTorus {
	@Shadow
	@Final
	private Entity entity;

	@Redirect(method = "updatePlayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 noclipBackrooms$roundTheTorus(Vec3 player, Vec3 thing) {
		Vec3 delta = player.subtract(thing);
		if (entity.level().dimension() != Level0.LEVEL_0) return delta;
		return new Vec3(Level0Space.nearest(delta.x), delta.y, Level0Space.nearest(delta.z));
	}

	@Redirect(method = "updatePlayer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
	private boolean noclipBackrooms$acrossTheEdge(ChunkMap map, ServerPlayer player, int chunkX, int chunkZ) {
		return map.isChunkTracked(player, chunkX, chunkZ) || entity.level().dimension() == Level0.LEVEL_0;
	}
}
