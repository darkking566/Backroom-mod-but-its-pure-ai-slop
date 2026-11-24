package dev.noclip.backrooms.monsters.wretch;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders the 3D dehydrated crimson Wretch. */
public final class WretchRenderer extends MobRenderer<Wretch, WretchRenderer.State, WretchModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/wretch.png");

	public WretchRenderer(EntityRendererProvider.Context context) {
		super(context, new WretchModel(context.bakeLayer(WretchModel.LAYER)), 0.45F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Wretch mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.aiState = mob.aiState();
		state.maul.copyFrom(mob.maulAnimation);
		state.recoil.copyFrom(mob.recoilAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		int aiState;
		final AnimationState maul = new AnimationState();
		final AnimationState recoil = new AnimationState();
	}
}
