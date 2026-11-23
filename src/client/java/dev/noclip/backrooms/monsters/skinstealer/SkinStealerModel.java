package dev.noclip.backrooms.monsters.skinstealer;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 3D Skin-Stealer built in Blender (art/monsters/skin_stealer): 18 joints, 43 boxes, 5 smooth clips.
 * Blends between disguised_idle/walk, spasmodic reveal_twitch, hunt_sprint, and attack_slash.
 */
public final class SkinStealerModel extends EntityModel<SkinStealerRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("skin_stealer"), "main");
	public static final BoxRig RIG = BoxRig.load("skin_stealer");
	private static final float FADE_IN_MS = 80, FADE_OUT_MS = 140;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("disguised_idle");
	private final BoxRig.Clip walk = RIG.clip("disguised_walk");
	private final BoxRig.Clip reveal = RIG.clip("reveal_twitch");
	private final BoxRig.Clip sprint = RIG.clip("hunt_sprint");
	private final BoxRig.Clip slash = RIG.clip("attack_slash");

	public SkinStealerModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(SkinStealerRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.2F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (state.mode == SkinStealer.STATE_HUNTING) {
			sprint.apply(joints, state.walkAnimationPos / 10.0F, Math.max(0.55F, moveWeight));
		} else if (moveWeight > 0.01F) {
			walk.apply(joints, state.walkAnimationPos / 12.0F, moveWeight);
		}

		applyOneShot(reveal, state.reveal, state.ageInTicks);
		applyOneShot(slash, state.slash, state.ageInTicks);

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
