package dev.noclip.backrooms.monsters.duller;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders the 3D faceless plaster Duller. */
public final class DullerRenderer extends MobRenderer<Duller, DullerRenderer.State, DullerModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/duller.png");

	public DullerRenderer(EntityRendererProvider.Context context) {
		super(context, new DullerModel(context.bakeLayer(DullerModel.LAYER)), 0.45F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Duller mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.stoop = mob.stoopAmount();
		state.phase.copyFrom(mob.phaseAnimation);
		state.strike.copyFrom(mob.strikeAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		float stoop;
		final AnimationState phase = new AnimationState();
		final AnimationState strike = new AnimationState();
	}
}
