package dev.noclip.backrooms.monsters.arms;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The 3D Arms built in Blender (art/monsters/arms): 16 joints, 96 boxes, 4 smooth Catmull-Rom clips.
 */
public final class ArmsModel extends EntityModel<ArmsRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("arms"), "main");
	public static final BoxRig RIG = BoxRig.load("arms");
	private static final float FADE_IN_MS = 70, FADE_OUT_MS = 130;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_dangle");
	private final BoxRig.Clip reach = RIG.clip("reach_grab");
	private final BoxRig.Clip snatch = RIG.clip("attack_snatch");
	private final BoxRig.Clip recoil = RIG.clip("light_recoil");

	public ArmsModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(ArmsRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		idle.apply(joints, timeSec, 1.0F);
		if (state.reaching) {
			reach.apply(joints, timeSec * 1.2F, 0.85F);
		}
		applyOneShot(snatch, state.snatch, state.ageInTicks);
		applyOneShot(recoil, state.recoil, state.ageInTicks);
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
