package dev.noclip.backrooms.core.people;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The people as modelled in Blender (art/people): joints for the body and two-piece arms and legs, plus accessories
 * (hats, packs, coats, tools) that each look shows or hides. Standing they breathe (idle), moving they walk, and a
 * swung arm plays the work clip (a hammering stroke).
 */
public final class PersonModel extends EntityModel<PersonRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("people"), "main");
	public static final BoxRig RIG = BoxRig.load("people");
	/** Walk animation distance per cycle of the walk clip (vanilla swings its limbs at 0.6662 radians a unit). */
	private static final float STRIDE = 9.4F;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle"), walk = RIG.clip("walk"), work = RIG.clip("work");

	public PersonModel(ModelPart root, String look) {
		super(root);
		joints = RIG.bind(root);
		List<String> shown = RIG.look(look);
		for (String accessory : RIG.accessories()) joints.get(accessory).visible = shown.contains(accessory);
	}

	@Override
	public void setupAnim(PersonRenderer.State state) {
		super.setupAnim(state);
		idle.apply(joints, state.ageInTicks / 20, 1);
		walk.apply(joints, state.walkAnimationPos / STRIDE, Mth.clamp(state.walkAnimationSpeed * 6, 0, 1));
		if (state.swing > 0) work.apply(joints, state.swing * work.length(), 1);
		float yaw = Mth.clamp(state.yRot, -70, 70) * Mth.DEG_TO_RAD, pitch = Mth.clamp(state.xRot, -45, 45) * Mth.DEG_TO_RAD;
		joints.get("Head").yRot += yaw;
		joints.get("Head").xRot += pitch;
	}
}
