package dev.noclip.backrooms.monsters.bacteria;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class BacteriaModel extends EntityModel<BacteriaRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("bacteria"), "main");
	public static final BoxRig RIG = BoxRig.load("bacteria");
	private static final float FADE_IN_MS = 65, FADE_OUT_MS = 130;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("glitch_idle");
	private final BoxRig.Clip sprint = RIG.clip("howl_sprint");
	private final BoxRig.Clip lunge = RIG.clip("shriek_lunge");
	private final BoxRig.Clip flinch = RIG.clip("flinch_light");

	public BacteriaModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(BacteriaRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.4F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (state.aiState == Bacteria.STATE_FLINCH) {
			flinch.apply(joints, timeSec * 1.2F, 0.95F);
		} else if (state.aiState == Bacteria.STATE_SPRINT || moveWeight > 0.01F) {
			sprint.apply(joints, state.walkAnimationPos / 7.0F + timeSec * 0.45F, Math.max(0.5F, moveWeight));
		}

		if (state.lunge.isStarted()) {
			float ms = state.lunge.getTimeInMillis(state.ageInTicks);
			float length = lunge.length() * 1000.0F;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
				lunge.apply(joints, ms / 1000.0F, weight);
			}
		}

		ModelPart neck = joints.get("Neck"), head = joints.get("Head");
		float yaw = Mth.clamp(state.yRot, -55, 55) * Mth.DEG_TO_RAD;
		float pitch = Mth.clamp(state.xRot, -35, 35) * Mth.DEG_TO_RAD;
		if (neck != null) neck.yRot += yaw * 0.35F;
		if (head != null) {
			head.yRot += yaw * 0.55F;
			head.xRot += pitch * 0.5F;
		}
	}
}
