package dev.noclip.backrooms.monsters.nguithrxurh;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 3D Nguithr'xurh built in Blender (art/monsters/nguithrxurh):
 * 20 joints, 186 striped-leg & parasitic-fringe boxes, 4 smooth clips.
 */
public final class NguithrxurhModel extends EntityModel<NguithrxurhRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("nguithrxurh"), "main");
	public static final BoxRig RIG = BoxRig.load("nguithrxurh");
	private static final float FADE_IN_MS = 65, FADE_OUT_MS = 120;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_ambush");
	private final BoxRig.Clip prowl = RIG.clip("prowl_scuttle");
	private final BoxRig.Clip threat = RIG.clip("threat_display");
	private final BoxRig.Clip bite = RIG.clip("pounce_bite");

	public NguithrxurhModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(NguithrxurhRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.4F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (moveWeight > 0.01F) {
			prowl.apply(joints, state.walkAnimationPos / 8.0F, moveWeight);
		}
		applyOneShot(threat, state.threat, state.ageInTicks);
		applyOneShot(bite, state.bite, state.ageInTicks);
	}

	private void applyOneShot(BoxRig.Clip clip, net.minecraft.world.entity.AnimationState anim, float ageInTicks) {
		if (!anim.isStarted()) return;
		float ms = anim.getTimeInMillis(ageInTicks);
		float length = clip.length() * 1000.0F;
		if (ms < length) {
			float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
			clip.apply(joints, ms / 1000.0F, weight);
		}
	}
}
