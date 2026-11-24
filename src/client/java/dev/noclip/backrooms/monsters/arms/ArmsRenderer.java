package dev.noclip.backrooms.monsters.arms;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders the 3D ceiling-hanging Arms entity. */
public final class ArmsRenderer extends MobRenderer<Arms, ArmsRenderer.State, ArmsModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/arms.png");

	public ArmsRenderer(EntityRendererProvider.Context context) {
		super(context, new ArmsModel(context.bakeLayer(ArmsModel.LAYER)), 0.0F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Arms mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.reaching = mob.isReaching();
		state.snatch.copyFrom(mob.snatchAnimation);
		state.recoil.copyFrom(mob.recoilAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		boolean reaching;
		final AnimationState snatch = new AnimationState();
		final AnimationState recoil = new AnimationState();
	}
}
