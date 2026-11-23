package dev.noclip.backrooms.monsters.hound;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Hound as Antigravity built it in Blender (art/monsters/hound): 39 joints, 214 boxes, three clips.
 * Standing it breathes and twitches (idle_twitch), moving it prowls (prowl_walk, kept in step with how far it
 * has gone), and a lunge plays attack_roar over both.
 */
public final class HoundModel extends EntityModel<HoundRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("hound"), "main");
	public static final BoxRig RIG = BoxRig.load("hound");
	/** Blocks walked per second of prowl_walk. */
	private static final float STRIDE = 16;
	private static final float FADE_IN_MS = 80, FADE_OUT_MS = 150;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_twitch"), prowl = RIG.clip("prowl_walk"), lunge = RIG.clip("attack_roar");

	public HoundModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(HoundRenderer.State state) {
		super.setupAnim(state);
		idle.apply(joints, state.ageInTicks / 20, 1);
		prowl.apply(joints, state.walkAnimationPos / STRIDE, Mth.clamp(state.walkAnimationSpeed * 2, 0, 1));
		if (state.lunge.isStarted()) {
			float ms = state.lunge.getTimeInMillis(state.ageInTicks), length = lunge.length() * 1000;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1);
				lunge.apply(joints, ms / 1000, weight);
			}
		}
		// Looking about: the neck takes some of it, the head the rest.
		float yaw = Mth.clamp(state.yRot, -60, 60) * Mth.DEG_TO_RAD, pitch = Mth.clamp(state.xRot, -40, 40) * Mth.DEG_TO_RAD;
		joints.get("Neck_Upper").yRot += yaw * 0.4F;
		joints.get("Head").yRot += yaw * 0.4F;
		joints.get("Head").xRot += pitch * 0.5F;
	}
}
