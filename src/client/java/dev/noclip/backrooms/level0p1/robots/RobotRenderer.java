package dev.noclip.backrooms.level0p1.robots;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Draws a robot of its kind. */
public final class RobotRenderer extends MobRenderer<Robot, RobotRenderer.State, RobotModel> {
	private final Identifier texture;

	public RobotRenderer(EntityRendererProvider.Context context, Robot.Kind kind) {
		super(context, new RobotModel(context.bakeLayer(RobotModel.LAYER), kind), 0.5F);
		texture = BackroomsMod.id("textures/entity/robots/" + kind.id + ".png");
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return texture;
	}

	public static final class State extends LivingEntityRenderState {
	}
}
