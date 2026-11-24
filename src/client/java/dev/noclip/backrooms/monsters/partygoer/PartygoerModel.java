package dev.noclip.backrooms.monsters.partygoer;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class PartygoerModel extends EntityModel<PartygoerRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("partygoer"), "main");
	public static final BoxRig RIG = BoxRig.load("partygoer");
	private static final float FADE_IN_MS = 70, FADE_OUT_MS = 140;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip statue = RIG.clip("statue_idle");
	private final BoxRig.Clip skip = RIG.clip("skip_stalk");
	private final BoxRig.Clip reveal = RIG.clip("ambush_reveal");
	private final BoxRig.Clip grab = RIG.clip("convert_grab");

	public PartygoerModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(PartygoerRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.3F, 0.0F, 1.0F);

		statue.apply(joints, timeSec, 1.0F);
		if (state.aiState == Partygoer.STATE_GRAB) {
			grab.apply(joints, timeSec * 1.15F, 0.95F);
		} else if (state.aiState == Partygoer.STATE_SKIP || moveWeight > 0.01F) {
			skip.apply(joints, state.walkAnimationPos / 8.5F + timeSec * 0.3F, Math.max(0.45F, moveWeight));
		}

		if (state.reveal.isStarted()) {
			float ms = state.reveal.getTimeInMillis(state.ageInTicks);
			float length = reveal.length() * 1000.0F;
			if (ms < length) {
				float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
				reveal.apply(joints, ms / 1000.0F, weight);
			}
		}

		if (state.aiState != Partygoer.STATE_STATUE) {
			ModelPart head = joints.get("Head");
			if (head != null) {
				head.yRot += Mth.clamp(state.yRot, -50, 50) * Mth.DEG_TO_RAD * 0.55F;
				head.xRot += Mth.clamp(state.xRot, -30, 30) * Mth.DEG_TO_RAD * 0.5F;
			}
		}
	}
}
