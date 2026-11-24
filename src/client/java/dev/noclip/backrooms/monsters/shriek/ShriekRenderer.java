package dev.noclip.backrooms.monsters.shriek;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders the 3D multicolored tangled-tendril Shriek. */
public final class ShriekRenderer extends MobRenderer<Shriek, ShriekRenderer.State, ShriekModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/shriek.png");

	public ShriekRenderer(EntityRendererProvider.Context context) {
		super(context, new ShriekModel(context.bakeLayer(ShriekModel.LAYER)), 0.85F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Shriek mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.fleeing = mob.isFleeing();
		state.shriek.copyFrom(mob.shriekAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		boolean fleeing;
		final AnimationState shriek = new AnimationState();
	}
}
