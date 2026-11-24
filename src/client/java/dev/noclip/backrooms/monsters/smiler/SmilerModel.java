package dev.noclip.backrooms.monsters.smiler;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 3D Smiler built in Blender (art/monsters/smiler): 17 joints, 51 boxes, 4 smooth Catmull-Rom clips.
 * Blends seamlessly between idle_hover, stalk_drift, enraged_rush (wide-open jaw), and attack_bite.
 */
public final class SmilerModel extends EntityModel<SmilerRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("smiler"), "main");
	public static final BoxRig RIG = BoxRig.load("smiler");
	private static final float FADE_IN_MS = 90, FADE_OUT_MS = 160;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_hover");
	private final BoxRig.Clip stalk = RIG.clip("stalk_drift");
	private final BoxRig.Clip rush = RIG.clip("enraged_rush");
	private final BoxRig.Clip bite = RIG.clip("attack_bite");

	public SmilerModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(SmilerRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		// Base weightless levitation
		idle.apply(joints, timeSec, 1.0F);
		// Blend gliding stalk motion when moving
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.2F, 0.0F, 1.0F);
		if (moveWeight > 0.01F) {
			stalk.apply(joints, timeSec * 1.1F, moveWeight);
		}
		// Blend violent enraged rush (unhinged jaw + slashing claws) when enraged
		if (state.enraged) {
			rush.apply(joints, timeSec * 1.25F, Math.max(0.65F, moveWeight));
		}
		// Overlay wind-up & snap-bite attack clip
		if (state.bite.isStarted()) {
			float ms = state.bite.getTimeInMillis(state.ageInTicks);
			float length = bite.length() * 1000.0F;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
				bite.apply(joints, ms / 1000.0F, weight);
			}
		}
		// Smooth head tracking toward prey
		float yaw = Mth.clamp(state.yRot, -60, 60) * Mth.DEG_TO_RAD;
		float pitch = Mth.clamp(state.xRot, -40, 40) * Mth.DEG_TO_RAD;
		ModelPart neck = joints.get("Neck"), head = joints.get("Head");
		if (neck != null) neck.yRot += yaw * 0.35F;
		if (head != null) {
			head.yRot += yaw * 0.55F;
			head.xRot += pitch * 0.55F;
		}
	}
}
