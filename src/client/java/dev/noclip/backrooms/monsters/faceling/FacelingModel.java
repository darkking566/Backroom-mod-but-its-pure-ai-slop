package dev.noclip.backrooms.monsters.faceling;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class FacelingModel extends EntityModel<FacelingRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("faceling"), "main");
	public static final BoxRig RIG = BoxRig.load("faceling");

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle");
	private final BoxRig.Clip walk = RIG.clip("walk");
	private final BoxRig.Clip inspect = RIG.clip("inspect_trade");
	private final BoxRig.Clip aggro = RIG.clip("aggro_chase");

	public FacelingModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(FacelingRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.4F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (state.aiState == Faceling.STATE_AGGRO) {
			aggro.apply(joints, state.walkAnimationPos / 7.5F + timeSec * 0.35F, Math.max(0.55F, moveWeight));
		} else if (moveWeight > 0.01F) {
			walk.apply(joints, state.walkAnimationPos / 10.0F, moveWeight);
		} else if (state.aiState == Faceling.STATE_INSPECT) {
			inspect.apply(joints, timeSec, 0.9F);
		}

		float yaw = Mth.clamp(state.yRot, -55, 55) * Mth.DEG_TO_RAD;
		float pitch = Mth.clamp(state.xRot, -35, 35) * Mth.DEG_TO_RAD;
		ModelPart neck = joints.get("Neck"), head = joints.get("Head");
		if (neck != null) neck.yRot += yaw * 0.3F;
		if (head != null) {
			head.yRot += yaw * 0.6F;
			head.xRot += pitch * 0.5F;
		}
	}
}
