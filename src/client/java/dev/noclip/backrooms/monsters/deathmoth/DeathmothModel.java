package dev.noclip.backrooms.monsters.deathmoth;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class DeathmothModel extends EntityModel<DeathmothRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("deathmoth"), "main");
	public static final BoxRig RIG = BoxRig.load("deathmoth");
	private static final float FADE_IN_MS = 75, FADE_OUT_MS = 140;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip hover = RIG.clip("hover_idle");
	private final BoxRig.Clip chase = RIG.clip("fly_chase");
	private final BoxRig.Clip frenzy = RIG.clip("light_frenzy");
	private final BoxRig.Clip feed = RIG.clip("eat_feed");
	private final BoxRig.Clip spit = RIG.clip("spit_acid");

	public DeathmothModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(DeathmothRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.2F, 0.0F, 1.0F);

		hover.apply(joints, timeSec, 1.0F);
		if (state.aiState == Deathmoth.STATE_FEEDING) {
			feed.apply(joints, timeSec, 0.9F);
		} else if (state.aiState == Deathmoth.STATE_LIGHT_FRENZY) {
			frenzy.apply(joints, timeSec * 1.2F, Math.max(0.65F, moveWeight));
		} else if (moveWeight > 0.01F || state.aiState == Deathmoth.STATE_CHASE) {
			chase.apply(joints, timeSec * 1.15F, Math.max(0.35F, moveWeight));
		}

		if (state.spit.isStarted()) {
			float ms = state.spit.getTimeInMillis(state.ageInTicks);
			float length = spit.length() * 1000.0F;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
				spit.apply(joints, ms / 1000.0F, weight);
			}
		}

		ModelPart head = joints.get("Head");
		if (head != null) {
			head.yRot += Mth.clamp(state.yRot, -45, 45) * Mth.DEG_TO_RAD * 0.5F;
			head.xRot += Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.5F;
		}
	}
}
