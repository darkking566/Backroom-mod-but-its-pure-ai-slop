package dev.noclip.backrooms.monsters.beastlevel5;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class BeastOfLevel5Model extends EntityModel<BeastOfLevel5Renderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("beast_level5"), "main");
	public static final BoxRig RIG = BoxRig.load("beast_level5");
	private static final float FADE_IN_MS = 85, FADE_OUT_MS = 160;
	private static final String[] PHASE2_JOINTS = {"BossWing_L", "BossWing_R", "BackTent_L1", "BackTent_R1"};

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("gentleman_idle");
	private final BoxRig.Clip stride = RIG.clip("stalk_stride");
	private final BoxRig.Clip unfurl = RIG.clip("phase2_unfurl");
	private final BoxRig.Clip lash = RIG.clip("tentacle_lash");

	public BeastOfLevel5Model(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(BeastOfLevel5Renderer.State state) {
		super.setupAnim(state);
		for (String name : PHASE2_JOINTS) {
			ModelPart part = joints.get(name);
			if (part != null) part.visible = state.phase2;
		}

		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.2F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (moveWeight > 0.01F) {
			stride.apply(joints, state.walkAnimationPos / 8.5F, moveWeight);
		}
		if (state.lashing) {
			lash.apply(joints, timeSec * (state.phase2 ? 1.35F : 1.05F), 0.9F);
		}

		if (state.unfurl.isStarted()) {
			float ms = state.unfurl.getTimeInMillis(state.ageInTicks);
			float length = unfurl.length() * 1000.0F;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
				unfurl.apply(joints, ms / 1000.0F, weight);
			}
		}

		float yaw = Mth.clamp(state.yRot, -50, 50) * Mth.DEG_TO_RAD;
		float pitch = Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD;
		ModelPart neck = joints.get("Neck"), head = joints.get("Head");
		if (neck != null) neck.yRot += yaw * 0.35F;
		if (head != null) {
			head.yRot += yaw * 0.55F;
			head.xRot += pitch * 0.5F;
		}
	}
}
