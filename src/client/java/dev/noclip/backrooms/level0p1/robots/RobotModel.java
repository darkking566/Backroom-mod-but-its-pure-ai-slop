package dev.noclip.backrooms.level0p1.robots;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The station's robots as modelled in Blender (art/robots): one rig holds all three, and each kind shows only its own
 * joints. They idle, roll or walk, and the chef stirs while standing; the cleaner's roller brush turns and a broken servant
 * twitches now and then. The wheels and the brush are turned in code, since their turning never stops.
 */
public final class RobotModel extends EntityModel<RobotRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("robots"), "main");
	public static final BoxRig RIG = BoxRig.load("robots");
	private static final float STRIDE = 9.4F;

	private final Robot.Kind kind;
	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle, walk, extra;

	public RobotModel(ModelPart root, Robot.Kind kind) {
		super(root);
		this.kind = kind;
		joints = RIG.bind(root);
		List<String> shown = RIG.look(kind.id);
		for (String joint : RIG.accessories()) joints.get(joint).visible = shown.contains(joint);
		idle = RIG.clip("idle_" + kind.id);
		walk = RIG.clip("walk_" + kind.id);
		extra = kind == Robot.Kind.CHEF ? RIG.clip("cook_chef") : kind == Robot.Kind.SERVANT ? RIG.clip("twitch_servant") : null;
	}

	@Override
	public void setupAnim(RobotRenderer.State state) {
		super.setupAnim(state);
		float moving = Mth.clamp(state.walkAnimationSpeed * 6, 0, 1);
		idle.apply(joints, state.ageInTicks / 20, 1);
		if (extra != null) {
			// The chef stirs while standing; the servant twitches in fits, whatever it is doing.
			float still = kind == Robot.Kind.CHEF ? 1 - moving : 1;
			if (kind == Robot.Kind.SERVANT) {
				float cycle = Mth.positiveModulo(state.ageInTicks / 20, 9F);
				still = cycle > 5 && cycle < 7.5F ? 1 : 0;
				extra.apply(joints, cycle - 5, still);
			} else {
				extra.apply(joints, state.ageInTicks / 20, still);
			}
		}
		walk.apply(joints, state.walkAnimationPos / STRIDE, moving);
		switch (kind) {
			case CLEANER -> joints.get("ClBrush").xRot += state.ageInTicks * (0.25F + moving * 0.5F);
			case CHEF -> {
				joints.get("KWheelR").xRot += state.walkAnimationPos * 0.6F;
				joints.get("KWheelL").xRot += state.walkAnimationPos * 0.6F;
			}
			case SERVANT -> {
			}
		}
		String head = switch (kind) {
			case CLEANER -> null;
			case CHEF -> "KHead";
			case SERVANT -> "SHead";
		};
		if (head != null) {
			joints.get(head).yRot += Mth.clamp(state.yRot, -60, 60) * Mth.DEG_TO_RAD * 0.6F;
		}
	}
}
