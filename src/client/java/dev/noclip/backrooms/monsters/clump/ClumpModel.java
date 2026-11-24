package dev.noclip.backrooms.monsters.clump;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 3D Clump built in Blender (art/monsters/clump): 17 joints, 62 boxes, 4 smooth multi-limb clips.
 * Blends between idle_writhe, alternating 8-arm scuttle_walk, frantic sound_charge, and attack_grab.
 */
public final class ClumpModel extends EntityModel<ClumpRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("clump"), "main");
	public static final BoxRig RIG = BoxRig.load("clump");
	private static final float FADE_IN_MS = 70, FADE_OUT_MS = 130;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_writhe");
	private final BoxRig.Clip scuttle = RIG.clip("scuttle_walk");
	private final BoxRig.Clip charge = RIG.clip("sound_charge");
	private final BoxRig.Clip grab = RIG.clip("attack_grab");

	public ClumpModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(ClumpRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.4F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (state.charging) {
			charge.apply(joints, timeSec * 1.4F, Math.max(0.65F, moveWeight));
		} else if (moveWeight > 0.01F) {
			scuttle.apply(joints, state.walkAnimationPos / 8.0F, moveWeight);
		}

		if (state.grab.isStarted()) {
			float ms = state.grab.getTimeInMillis(state.ageInTicks);
			float length = grab.length() * 1000.0F;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
				grab.apply(joints, ms / 1000.0F, weight);
			}
		}
	}
}
