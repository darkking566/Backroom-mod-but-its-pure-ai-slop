package dev.noclip.backrooms.monsters.windowentity;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 3D Entity 2 ("Window") built in Blender 5.3 (art/monsters/window_entity):
 * hunched rib-ridged silhouette with glowing pinprick eyes, hinged jaw, and elongated
 * pale-palmed hands that beckon wanderers toward un-blacked glass.
 */
public final class WindowEntityModel extends EntityModel<WindowEntityRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("window_entity"), "main");
	public static final BoxRig RIG = BoxRig.load("window_entity");
	private static final float FADE_IN_MS = 80, FADE_OUT_MS = 140;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_hover");
	private final BoxRig.Clip beckon = RIG.clip("beckon_lure");
	private final BoxRig.Clip lunge = RIG.clip("lunge_grab");
	private final BoxRig.Clip banish = RIG.clip("recoil_banish");

	public WindowEntityModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(WindowEntityRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		idle.apply(joints, timeSec, 1.0F);
		if (state.beckoning) {
			beckon.apply(joints, timeSec, 0.92F);
		}
		applyOneShot(lunge, state.lunge, state.ageInTicks);
		applyOneShot(banish, state.banish, state.ageInTicks);

		float yaw = Mth.clamp(state.yRot, -55, 55) * Mth.DEG_TO_RAD;
		float pitch = Mth.clamp(state.xRot, -35, 35) * Mth.DEG_TO_RAD;
		ModelPart neck = joints.get("NeckUpper"), head = joints.get("Head");
		if (neck != null) neck.yRot += yaw * 0.35F;
		if (head != null) {
			head.yRot += yaw * 0.55F;
			head.xRot += pitch * 0.55F;
		}
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
