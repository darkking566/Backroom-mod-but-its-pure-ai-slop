package dev.noclip.backrooms.monsters.nguithrxurh;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders Nguithr'xurh with pinpoint reflective silver-white eyes glowing in the dark. */
public final class NguithrxurhRenderer extends MobRenderer<Nguithrxurh, NguithrxurhRenderer.State, NguithrxurhModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/nguithrxurh.png");
	private static final RenderType EYES = RenderTypes.eyes(BackroomsMod.id("textures/entity/nguithrxurh_eyes.png"));

	public NguithrxurhRenderer(EntityRendererProvider.Context context) {
		super(context, new NguithrxurhModel(context.bakeLayer(NguithrxurhModel.LAYER)), 0.95F);
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
	public void extractRenderState(Nguithrxurh mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.threat.copyFrom(mob.threatAnimation);
		state.bite.copyFrom(mob.biteAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		final AnimationState threat = new AnimationState();
		final AnimationState bite = new AnimationState();
	}
}
