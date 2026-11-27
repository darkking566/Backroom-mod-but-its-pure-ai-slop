package dev.noclip.backrooms.mixin.client;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a Level 0 ghost (level0.Level0Ghosts) walk in step with the one it copies. */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccess {
	@Accessor("speedOld")
	float noclipBackrooms$speedOld();

	@Accessor("speedOld")
	void noclipBackrooms$setSpeedOld(float value);

	@Accessor("speed")
	void noclipBackrooms$setSpeed(float value);

	@Accessor("position")
	void noclipBackrooms$setPosition(float value);

	@Accessor("positionScale")
	float noclipBackrooms$positionScale();

	@Accessor("positionScale")
	void noclipBackrooms$setPositionScale(float value);
}
