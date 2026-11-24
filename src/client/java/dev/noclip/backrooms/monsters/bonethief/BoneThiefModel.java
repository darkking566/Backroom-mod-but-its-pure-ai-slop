package dev.noclip.backrooms.monsters.bonethief;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class BoneThiefModel extends EntityModel<BoneThiefRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("bone_thief"), "main");
	public static final BoxRig RIG = BoxRig.load("bone_thief");
	private static final float FADE_IN_MS = 70, FADE_OUT_MS = 140;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("waddle_idle");
	private final BoxRig.Clip walk = RIG.clip("walk_waddle");
	private final BoxRig.Clip gaze = RIG.clip("gaze_open");
	private final BoxRig.Clip chomp = RIG.clip("chomp_lunge");

	public BoneThiefModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(BoneThiefRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.5F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (moveWeight > 0.01F) {
			walk.apply(joints, state.walkAnimationPos / 8.0F, moveWeight);
		}
		if (state.aiState == BoneThief.STATE_GAZE) {
			gaze.apply(joints, timeSec, 0.9F);
		}

		if (state.chomp.isStarted()) {
			float ms = state.chomp.getTimeInMillis(state.ageInTicks);
			float length = chomp.length() * 1000.0F;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
				chomp.apply(joints, ms / 1000.0F, weight);
			}
		}
	}
}
