package dev.noclip.backrooms.monsters.crawler;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.monsters.BoxRig;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 3D Crawler built in Blender (art/monsters/crawler): 17 joints, 136 white-mold fungal spider boxes, 4 clips.
 */
public final class CrawlerModel extends EntityModel<CrawlerRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BackroomsMod.id("crawler"), "main");
	public static final BoxRig RIG = BoxRig.load("crawler");
	private static final float FADE_IN_MS = 65, FADE_OUT_MS = 120;

	private final Map<String, ModelPart> joints;
	private final BoxRig.Clip idle = RIG.clip("idle_pulse");
	private final BoxRig.Clip crawl = RIG.clip("scuttle_crawl");
	private final BoxRig.Clip spore = RIG.clip("spore_burst");
	private final BoxRig.Clip lunge = RIG.clip("attack_lunge");

	public CrawlerModel(ModelPart root) {
		super(root);
		joints = RIG.bind(root);
	}

	@Override
	public void setupAnim(CrawlerRenderer.State state) {
		super.setupAnim(state);
		float timeSec = state.ageInTicks / 20.0F;
		float moveWeight = Mth.clamp(state.walkAnimationSpeed * 2.4F, 0.0F, 1.0F);

		idle.apply(joints, timeSec, 1.0F);
		if (moveWeight > 0.01F) {
			crawl.apply(joints, state.walkAnimationPos / 8.5F, moveWeight);
		}
		applyOneShot(spore, state.spore, state.ageInTicks);
		applyOneShot(lunge, state.lunge, state.ageInTicks);
	}

	private void applyOneShot(BoxRig.Clip clip, net.minecraft.world.entity.AnimationState anim, float ageInTicks) {
		if (!anim.isStarted()) return;
		float ms = anim.getTimeInMillis(ageInTicks);
		float length = clip.length() * 1000.0F;
		if (ms < length) {
			float weight = Math.min(Math.min(ms / FADE_IN_MS, (length - ms) / FADE_OUT_MS), 1.0F);
			clip.apply(joints, ms / 1000.0F, weight);
		}
	}
}
