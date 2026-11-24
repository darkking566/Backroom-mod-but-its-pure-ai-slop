package dev.noclip.backrooms.monsters.wretch;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 3D Wretch built in Blender (art/monsters/wretch): 20 joints, 147 boxes, 5 Catmull-Rom clips.
 */
public final class WretchModel extends EntityModel<WretchRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("wretch"), "main");
	public static final BoxRig RIG = BoxRig.load("wretch");
	private static final float FADE_IN_MS = 65, FADE_OUT_MS = 120;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip sob = RIG.clip("crouch_sob");
	private final BoxRig.Clip hobble = RIG.clip("hobble_stalk");
	private final BoxRig.Clip sprint = RIG.clip("frenzy_sprint");
	private final BoxRig.Clip maul = RIG.clip("attack_maul");
	private final BoxRig.Clip recoil = RIG.clip("almond_recoil");

	public WretchModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(WretchRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.4F, 0.0F, 1.0F);

		// Base idle: hunched trembling sobbing
		sob.apply(joints, timeSec, 1.0F);

		if (state.aiState == Wretch.STATE_FRENZY) {
			float runWeight = Math.max(0.45F, moveWeight);
			sprint.apply(joints, state.walkAnimationPos / 7.5F + timeSec * 0.4F, runWeight);
		} else if (moveWeight > 0.01F) {
			hobble.apply(joints, state.walkAnimationPos / 11.0F, moveWeight);
		}

		applyOneShot(maul, state.maul, state.ageInTicks);
		applyOneShot(recoil, state.recoil, state.ageInTicks);

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
