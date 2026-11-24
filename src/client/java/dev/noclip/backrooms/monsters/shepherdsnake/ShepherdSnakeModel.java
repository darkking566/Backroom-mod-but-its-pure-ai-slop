package dev.noclip.backrooms.monsters.shepherdsnake;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The colossal 3D Shepherd Snake built in Blender (art/monsters/shepherd_snake):
 * 14 colossal joints (~18 blocks long), 108 boxes, 4 smooth traveling-wave clips.
 */
public final class ShepherdSnakeModel extends EntityModel<ShepherdSnakeRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("shepherd_snake"), "main");
	public static final BoxRig RIG = BoxRig.load("shepherd_snake");
	private static final float FADE_IN_MS = 90, FADE_OUT_MS = 160;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_coil");
	private final BoxRig.Clip slither = RIG.clip("slither_glide");
	private final BoxRig.Clip charge = RIG.clip("ram_charge");
	private final BoxRig.Clip crush = RIG.clip("attack_crush");
	private final BoxRig.Clip stoopClip = RIG.clip("low_ceiling_stoop");

	public ShepherdSnakeModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(ShepherdSnakeRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.2F, 0.0F, 1.0F);
		float stoop = Mth.clamp(state.stoop, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (state.charging) {
			charge.apply(joints, timeSec * 1.4F, 1.0F);
		} else if (moveWeight > 0.01F) {
			slither.apply(joints, state.walkAnimationPos / 10.0F, moveWeight);
		}

		if (state.crush.isStarted()) {
			float ms = state.crush.getTimeInMillis(state.ageInTicks);
			float length = crush.length() * 1000.0F;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
				crush.apply(joints, ms / 1000.0F, weight);
			}
		}

		if (stoop > 0.01F) {
			float stoopTime = moveWeight > 0.05F ? state.walkAnimationPos / 10.0F : timeSec * 0.45F;
			stoopClip.apply(joints, stoopTime, stoop);
		}

		float yaw = Mth.clamp(state.yRot, -50, 50) * Mth.DEG_TO_RAD;
		float minPitch = stoop > 0.5F ? -12.0F : -30.0F;
		float pitch = Mth.clamp(state.xRot, minPitch, 30.0F) * Mth.DEG_TO_RAD;
		ModelPart neck = joints.get("Neck_3"), head = joints.get("Head");
		if (neck != null) neck.yRot += yaw * 0.35F;
		if (head != null) {
			head.yRot += yaw * 0.55F;
			head.xRot += pitch * 0.45F;
		}
	}
}
