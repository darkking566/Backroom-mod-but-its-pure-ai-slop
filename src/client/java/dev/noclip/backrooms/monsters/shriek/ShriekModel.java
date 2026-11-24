package dev.noclip.backrooms.monsters.shriek;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 3D Shriek built in Blender (art/monsters/shriek): 17 joints, 248 colorful tendril boxes, 4 smooth clips.
 */
public final class ShriekModel extends EntityModel<ShriekRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("shriek"), "main");
	public static final BoxRig RIG = BoxRig.load("shriek");
	private static final float FADE_IN_MS = 60, FADE_OUT_MS = 140;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_quiver");
	private final BoxRig.Clip walk = RIG.clip("skitter_walk");
	private final BoxRig.Clip flee = RIG.clip("flee_dash");
	private final BoxRig.Clip call = RIG.clip("shriek_call");

	public ShriekModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(ShriekRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.4F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (moveWeight > 0.01F) {
			if (state.fleeing) {
				flee.apply(joints, state.walkAnimationPos / 7.5F, moveWeight);
			} else {
				walk.apply(joints, state.walkAnimationPos / 10.0F, moveWeight);
			}
		}

		if (state.shriek.isStarted()) {
			float ms = state.shriek.getTimeInMillis(state.ageInTicks);
			float length = call.length() * 1000.0F;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
				call.apply(joints, ms / 1000.0F, weight);
			}
		}
	}
}
