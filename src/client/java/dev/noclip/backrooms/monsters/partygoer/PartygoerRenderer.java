package dev.noclip.backrooms.monsters.partygoer;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

public final class PartygoerRenderer extends MobRenderer<Partygoer, PartygoerRenderer.State, PartygoerModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/partygoer.png");

	public PartygoerRenderer(EntityRendererProvider.Context context) {
		super(context, new PartygoerModel(context.bakeLayer(PartygoerModel.LAYER)), 0.48F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Partygoer mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.aiState = mob.aiState();
		state.reveal.copyFrom(mob.revealAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		int aiState;
		final AnimationState reveal = new AnimationState();
	}
}
