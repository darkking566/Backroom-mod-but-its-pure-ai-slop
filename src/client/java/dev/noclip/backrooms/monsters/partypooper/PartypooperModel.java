package dev.noclip.backrooms.monsters.partypooper;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class PartypooperModel extends EntityModel<PartypooperRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("partypooper"), "main");
	public static final BoxRig RIG = BoxRig.load("partypooper");

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle");
	private final BoxRig.Clip walk = RIG.clip("walk");
	private final BoxRig.Clip beckon = RIG.clip("beckon_ally");
	private final BoxRig.Clip combat = RIG.clip("melee_defend");
	private final BoxRig.Clip gift = RIG.clip("gift_feed");

	public PartypooperModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(PartypooperRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.4F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (state.aiState == Partypooper.STATE_COMBAT) {
			combat.apply(joints, state.walkAnimationPos / 8.0F + timeSec * 0.45F, Math.max(0.65F, moveWeight));
		} else if (moveWeight > 0.01F) {
			walk.apply(joints, state.walkAnimationPos / 9.5F, moveWeight);
		} else if (state.aiState == Partypooper.STATE_BECKON) {
			beckon.apply(joints, timeSec, 0.9F);
		} else if (state.aiState == Partypooper.STATE_GIFT) {
			gift.apply(joints, timeSec, 0.9F);
		}

		float yaw = Mth.clamp(state.yRot, -55, 55) * Mth.DEG_TO_RAD;
		float pitch = Mth.clamp(state.xRot, -35, 35) * Mth.DEG_TO_RAD;
		ModelPart neck = joints.get("Neck"), head = joints.get("Head");
		if (neck != null) neck.yRot += yaw * 0.35F;
		if (head != null) {
			head.yRot += yaw * 0.55F;
			head.xRot += pitch * 0.5F;
		}
	}
}
