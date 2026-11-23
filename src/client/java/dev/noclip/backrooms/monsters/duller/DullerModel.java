package dev.noclip.backrooms.monsters.duller;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 3D Duller built in Blender (art/monsters/duller): 17 joints, 25 boxes, 4 smooth Catmull-Rom clips.
 */
public final class DullerModel extends EntityModel<DullerRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("duller"), "main");
	public static final BoxRig RIG = BoxRig.load("duller");
	private static final float FADE_IN_MS = 80, FADE_OUT_MS = 140;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_sway");
	private final BoxRig.Clip stride = RIG.clip("stalk_stride");
	private final BoxRig.Clip emerge = RIG.clip("phase_emerge");
	private final BoxRig.Clip strike = RIG.clip("attack_strike");

	public DullerModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(DullerRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.2F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (moveWeight > 0.01F) {
			stride.apply(joints, state.walkAnimationPos / 12.0F, moveWeight);
		}
		applyOneShot(emerge, state.phase, state.ageInTicks);
		applyOneShot(strike, state.strike, state.ageInTicks);

		float yaw = Mth.clamp(state.yRot, -55, 55) * Mth.DEG_TO_RAD;
		float pitch = Mth.clamp(state.xRot, -35, 35) * Mth.DEG_TO_RAD;
		ModelPart neck = joints.get("Neck"), head = joints.get("Head");
		if (neck != null) neck.yRot += yaw * 0.35F;
		if (head != null) {
			head.yRot += yaw * 0.55F;
			head.xRot += pitch * 0.55F;
		}
	}

	private void applyOneShot(BoxRig.Clip clip, net.minecraft.world.entity.AnimationState anim, float ageInTicks) {
		if (!anim.isStarted()) return;
		float ms = anim.getTimeInMillis(ageInTicks);
		float length = clip.length() * 1000.0F;
		if (ms < length) {
			float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
			clip.apply(joints, ms / 1000.0F, weight);
		}
	}
}
