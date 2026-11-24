package dev.noclip.backrooms.monsters.shepherdsnake;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders the colossal Shepherd Snake with blazing full-bright white eyes. */
public final class ShepherdSnakeRenderer extends MobRenderer<ShepherdSnake, ShepherdSnakeRenderer.State, ShepherdSnakeModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/shepherd_snake.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/shepherd_snake_eyes.png"));

	public ShepherdSnakeRenderer(EntityRendererProvider.Context context) {
		super(context, new ShepherdSnakeModel(context.bakeLayer(ShepherdSnakeModel.LAYER)), 1.65F);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return EYES;
			}
		});
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ShepherdSnake mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.charging = mob.isCharging();
		state.stoop = mob.stoopAmount();
		state.crush.copyFrom(mob.crushAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		boolean charging;
		float stoop;
		final AnimationState crush = new AnimationState();
	}
}
