package net.charter.client;

import net.charter.CharterMod;
import net.charter.fx.CharterFxEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the golden visuals of Charter: the sigil on the ground, the beam of light, the chains around a condemned
 * player and the shrinking cage of Divine Dominance. Everything is drawn with the fullbright translucent entity
 * layer, so it glows in the dark and is animated purely from the age of the entity.
 */
public class CharterFxRenderer extends EntityRenderer<CharterFxEntity> {
	private static final Identifier SIGIL = CharterMod.id("textures/entity/sigil.png");
	private static final Identifier CHAIN = CharterMod.id("textures/entity/chain.png");
	private static final Identifier BEAM = CharterMod.id("textures/entity/beam.png");
	private static final Identifier CAGE = CharterMod.id("textures/entity/cage.png");

	/** Blocks covered by one repeat of the cage texture. */
	private static final float CAGE_TILE = 4.0f;
	/** Blocks covered by one repeat of the beam texture, vertically. */
	private static final float BEAM_TILE = 24.0f;
	/** Blocks covered by one repeat of the chain texture, along the chain. */
	private static final float CHAIN_TILE = 1.3f;

	public CharterFxRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(CharterFxEntity entity) {
		return SIGIL;
	}

	/** The cage and the beam are far bigger than the entity's tiny hitbox, so the default culling would hide them. */
	@Override
	public boolean shouldRender(CharterFxEntity entity, Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public void render(CharterFxEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider providers, int light) {
		float age = entity.age(tickDelta);
		if (age < 0.0f) {
			return;
		}
		switch (entity.getKind()) {
			case CharterFxEntity.SIGIL:
				renderSigil(entity, age, matrices, providers);
				break;
			case CharterFxEntity.BEAM:
				renderBeam(entity, age, matrices, providers);
				break;
			case CharterFxEntity.CHAINS:
				renderChains(entity, age, tickDelta, matrices, providers);
				break;
			case CharterFxEntity.CAGE:
				renderCage(entity, age, matrices, providers);
				break;
			default:
				break;
		}
	}

	// ------------------------------------------------------------------ the sigil

	private void renderSigil(CharterFxEntity entity, float age, MatrixStack matrices, VertexConsumerProvider providers) {
		int life = entity.getLifetime();
		float alpha = clamp01(age / 12.0f);
		if (life > 0) {
			alpha *= clamp01(((float) life - age) / 14.0f);
		}
		if (alpha <= 0.001f) {
			return;
		}
		float radius = entity.getSize();
		// the strike lands 30 ticks before the entity ends: the closer it gets, the brighter the sigil burns
		float charge = life > 30 ? clamp01(age / (float) (life - 30)) : 1.0f;
		float glow = 0.55f + 0.45f * charge;
		float pulse = 0.88f + 0.12f * MathHelper.sin(age * 0.45f);

		VertexConsumer consumer = providers.getBuffer(RenderLayer.getEntityTranslucentEmissive(SIGIL));

		matrices.push();
		matrices.translate(0.0, 0.04, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 1.1f));
		flat(consumer, matrices.peek(), radius, 1.0f, 0.80f, 0.30f, alpha * glow * pulse);
		matrices.pop();

		matrices.push();
		matrices.translate(0.0, 0.06, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-age * 2.2f));
		flat(consumer, matrices.peek(), radius * 0.6f, 1.0f, 0.93f, 0.55f, alpha * glow);
		matrices.pop();
	}

	// ------------------------------------------------------------------ the beam

	private void renderBeam(CharterFxEntity entity, float age, MatrixStack matrices, VertexConsumerProvider providers) {
		int life = entity.getLifetime();
		float fadeOut = life > 0 ? clamp01(((float) life - age) / 10.0f) : 1.0f;
		float grow = easeOut(clamp01(age / 3.0f));
		float alpha = fadeOut * grow;
		if (alpha <= 0.001f) {
			return;
		}
		float width = entity.getSize() * (0.25f + 0.75f * grow) * (0.55f + 0.45f * fadeOut);
		float height = 300.0f;
		float scroll = age * 0.05f;

		VertexConsumer consumer = providers.getBuffer(RenderLayer.getEntityTranslucentEmissive(BEAM));
		for (int layer = 0; layer < 3; layer++) {
			float half = layer == 0 ? width * 1.7f : (layer == 1 ? width : width * 0.45f);
			float layerAlpha = layer == 0 ? 0.30f : (layer == 1 ? 0.75f : 1.0f);
			float green = layer == 2 ? 0.97f : 0.82f;
			float blue = layer == 2 ? 0.80f : 0.35f;
			for (int plane = 0; plane < 3; plane++) {
				matrices.push();
				matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) plane * 60.0f + (layer == 1 ? age * 2.0f : 0.0f)));
				band(consumer, matrices.peek(), -half, 0.0f, -4.0f, height, half, 0.0f, -4.0f, height,
						0.0f, 1.0f, scroll, scroll + height / BEAM_TILE, 1.0f, green, blue, alpha * layerAlpha);
				matrices.pop();
			}
		}

		// the shockwave that rolls out over the ground when the beam lands
		float wave = clamp01(age / 14.0f);
		if (wave < 1.0f) {
			VertexConsumer ring = providers.getBuffer(RenderLayer.getEntityTranslucentEmissive(SIGIL));
			matrices.push();
			matrices.translate(0.0, 0.08, 0.0);
			flat(ring, matrices.peek(), entity.getSize() * (1.0f + wave * 2.5f), 1.0f, 0.90f, 0.50f, (1.0f - wave) * 0.8f);
			matrices.pop();
		}
	}

	// ------------------------------------------------------------------ the chains

	private void renderChains(CharterFxEntity entity, float age, float tickDelta, MatrixStack matrices, VertexConsumerProvider providers) {
		int life = entity.getLifetime();
		float alpha = clamp01(age / 6.0f);
		if (life > 0) {
			alpha *= clamp01(((float) life - age) / 8.0f);
		}
		if (alpha <= 0.001f) {
			return;
		}

		matrices.push();
		// stay glued to the chained player, which moves more smoothly than this entity does
		Entity target = entity.getFollowId() == 0 ? null : entity.getWorld().getEntityById(entity.getFollowId());
		if (target != null) {
			Vec3d wanted = target.getLerpedPos(tickDelta);
			Vec3d own = entity.getLerpedPos(tickDelta);
			matrices.translate(wanted.x - own.x, wanted.y - own.y, wanted.z - own.z);
		}

		// the chains fly in from all sides and tighten
		float settle = easeOut(clamp01(age / 12.0f));
		float radius = 0.52f + (1.0f - settle) * 1.1f;
		float crawl = age * 0.015f;

		VertexConsumer consumer = providers.getBuffer(RenderLayer.getEntityTranslucentEmissive(CHAIN));
		MatrixStack.Entry entry = matrices.peek();
		ring(consumer, entry, 0.40f, radius, 0.22f, 16, age * 3.0f, crawl, alpha);
		ring(consumer, entry, 0.95f, radius + 0.03f, 0.22f, 16, -age * 3.5f, -crawl, alpha);
		ring(consumer, entry, 1.50f, radius, 0.22f, 16, age * 3.0f, crawl, alpha);
		helix(consumer, entry, 0.0f, 1.9f, radius + 0.08f, 2.0f, 0.22f, 28, age * 2.0f, alpha);
		helix(consumer, entry, 0.0f, 1.9f, radius + 0.10f, -2.0f, 0.22f, 28, 90.0f - age * 2.0f, alpha);
		matrices.pop();
	}

	/** A horizontal loop of chain around the origin. */
	private static void ring(VertexConsumer consumer, MatrixStack.Entry entry, float y, float radius, float height, int segments, float rotationDegrees, float shift, float alpha) {
		float circumference = (float) (Math.PI * 2.0) * radius;
		float links = circumference / CHAIN_TILE;
		float start = (float) Math.toRadians((double) rotationDegrees);
		for (int i = 0; i < segments; i++) {
			float a0 = start + (float) (Math.PI * 2.0) * (float) i / (float) segments;
			float a1 = start + (float) (Math.PI * 2.0) * (float) (i + 1) / (float) segments;
			float u0 = shift + links * (float) i / (float) segments;
			float u1 = shift + links * (float) (i + 1) / (float) segments;
			band(consumer, entry,
					MathHelper.cos(a0) * radius, MathHelper.sin(a0) * radius, y - height * 0.5f, y + height * 0.5f,
					MathHelper.cos(a1) * radius, MathHelper.sin(a1) * radius, y - height * 0.5f, y + height * 0.5f,
					u0, u1, 0.0f, 1.0f, 1.0f, 0.85f, 0.35f, alpha);
		}
	}

	/** A chain spiralling up around the origin. */
	private static void helix(VertexConsumer consumer, MatrixStack.Entry entry, float yStart, float yEnd, float radius, float turns, float height, int segments, float rotationDegrees, float alpha) {
		float start = (float) Math.toRadians((double) rotationDegrees);
		float length = (float) (Math.PI * 2.0) * radius * Math.abs(turns);
		float links = length / CHAIN_TILE;
		for (int i = 0; i < segments; i++) {
			float t0 = (float) i / (float) segments;
			float t1 = (float) (i + 1) / (float) segments;
			float a0 = start + turns * (float) (Math.PI * 2.0) * t0;
			float a1 = start + turns * (float) (Math.PI * 2.0) * t1;
			float y0 = yStart + (yEnd - yStart) * t0;
			float y1 = yStart + (yEnd - yStart) * t1;
			band(consumer, entry,
					MathHelper.cos(a0) * radius, MathHelper.sin(a0) * radius, y0 - height * 0.5f, y0 + height * 0.5f,
					MathHelper.cos(a1) * radius, MathHelper.sin(a1) * radius, y1 - height * 0.5f, y1 + height * 0.5f,
					links * t0, links * t1, 0.0f, 1.0f, 1.0f, 0.88f, 0.40f, alpha * 0.9f);
		}
	}

	// ------------------------------------------------------------------ the cage

	private void renderCage(CharterFxEntity entity, float age, MatrixStack matrices, VertexConsumerProvider providers) {
		int arm = entity.getArm();
		float half = CharterFxEntity.cageHalfWidth(entity.getSize(), entity.getSizeEnd(), arm, entity.getShrink(), age);
		float up = entity.getUp();
		float down = entity.getDown();

		// the walls rise out of the ground when the tablet is placed
		float rise = easeOut(clamp01(age / 40.0f));
		float top = -down + (up + down) * rise;
		boolean live = age >= (float) arm;
		float flash = live ? clamp01(1.0f - (age - (float) arm) / 12.0f) : 0.0f;
		float closing = live ? clamp01((age - (float) arm) / (float) Math.max(1, entity.getShrink())) : 0.0f;
		float alpha = (live ? 0.62f : 0.30f) + 0.08f * MathHelper.sin(age * 0.2f) + 0.30f * flash;
		alpha = clamp01(alpha * clamp01(age / 10.0f));
		if (alpha <= 0.001f) {
			return;
		}
		float green = 0.82f - 0.22f * closing;
		float blue = 0.32f - 0.12f * closing;
		float scroll = age * 0.012f;
		float vSpan = (top + down) / CAGE_TILE;
		float uSpan = 2.0f * half / CAGE_TILE;

		VertexConsumer consumer = providers.getBuffer(RenderLayer.getEntityTranslucentEmissive(CAGE));
		MatrixStack.Entry entry = matrices.peek();
		// north, east, south and west wall
		band(consumer, entry, -half, -half, -down, top, half, -half, -down, top, 0.0f, uSpan, scroll, scroll + vSpan, 1.0f, green, blue, alpha);
		band(consumer, entry, half, -half, -down, top, half, half, -down, top, 0.0f, uSpan, scroll, scroll + vSpan, 1.0f, green, blue, alpha);
		band(consumer, entry, half, half, -down, top, -half, half, -down, top, 0.0f, uSpan, scroll, scroll + vSpan, 1.0f, green, blue, alpha);
		band(consumer, entry, -half, half, -down, top, -half, -half, -down, top, 0.0f, uSpan, scroll, scroll + vSpan, 1.0f, green, blue, alpha);
	}

	// ------------------------------------------------------------------ drawing helpers

	private static float clamp01(float value) {
		return MathHelper.clamp(value, 0.0f, 1.0f);
	}

	private static float easeOut(float t) {
		float inverse = 1.0f - t;
		return 1.0f - inverse * inverse * inverse;
	}

	private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, float u, float v, float nx, float ny, float nz, float red, float green, float blue, float alpha) {
		consumer.vertex(entry.getPositionMatrix(), x, y, z)
				.color(red, green, blue, alpha)
				.texture(u, v)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(entry.getNormalMatrix(), nx, ny, nz)
				.next();
	}

	/** A square lying flat in the XZ plane, centred on the origin, covering the whole texture. */
	private static void flat(VertexConsumer consumer, MatrixStack.Entry entry, float half, float red, float green, float blue, float alpha) {
		vertex(consumer, entry, -half, 0.0f, -half, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, red, green, blue, alpha);
		vertex(consumer, entry, -half, 0.0f, half, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, red, green, blue, alpha);
		vertex(consumer, entry, half, 0.0f, half, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, red, green, blue, alpha);
		vertex(consumer, entry, half, 0.0f, -half, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, red, green, blue, alpha);
	}

	/**
	 * A vertical strip standing between two points of the XZ plane. Each end has its own bottom and top, so the
	 * strip can slope (the chains spiral). The texture runs from {@code u0} to {@code u1} along the strip and
	 * from {@code v0} (bottom) to {@code v1} (top) up it.
	 */
	private static void band(VertexConsumer consumer, MatrixStack.Entry entry,
			float xStart, float zStart, float bottomStart, float topStart,
			float xEnd, float zEnd, float bottomEnd, float topEnd,
			float u0, float u1, float v0, float v1,
			float red, float green, float blue, float alpha) {
		float dx = xEnd - xStart;
		float dz = zEnd - zStart;
		float length = MathHelper.sqrt(dx * dx + dz * dz);
		float nx = length > 0.0001f ? dz / length : 0.0f;
		float nz = length > 0.0001f ? -dx / length : 1.0f;
		vertex(consumer, entry, xStart, bottomStart, zStart, u0, v0, nx, 0.0f, nz, red, green, blue, alpha);
		vertex(consumer, entry, xEnd, bottomEnd, zEnd, u1, v0, nx, 0.0f, nz, red, green, blue, alpha);
		vertex(consumer, entry, xEnd, topEnd, zEnd, u1, v1, nx, 0.0f, nz, red, green, blue, alpha);
		vertex(consumer, entry, xStart, topStart, zStart, u0, v1, nx, 0.0f, nz, red, green, blue, alpha);
	}
}
