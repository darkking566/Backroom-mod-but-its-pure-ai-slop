package dev.noclip.backrooms.monsters.faceling;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public final class FacelingRenderer extends MobRenderer<Faceling, FacelingRenderer.State, FacelingModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/faceling.png");

	public FacelingRenderer(EntityRendererProvider.Context context) {
		super(context, new FacelingModel(context.bakeLayer(FacelingModel.LAYER)), 0.42F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Faceling mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.aiState = mob.aiState();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		int aiState;
	}
}
