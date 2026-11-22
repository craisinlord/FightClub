package com.craisinlord.fightclub.client;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

public final class LongswordSweepParticle extends SingleQuadParticle {
    private static final float BASE_SIZE = 2.2f;
    private static final float CUT_START = 0.25f, CUT_END = 0.95f, SPIN_START = 0.12f, SPIN_END = 0.85f;

    private final float delay;
    private final float playTicks;
    private final double ownerX, ownerZ;
    private final boolean spin;
    private boolean resolved;
    private boolean mirrored;

    private final SpriteSet sprites;
    private final Quaternionf top;
    private final Quaternionf bottom;

    private LongswordSweepParticle(ClientLevel level, double x, double y, double z, double dirX, double size, double dirZ, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        boolean spin = size < 0.0;
        this.spin = spin;
        this.ownerX = x - dirX * 1.4;
        this.ownerZ = z - dirZ * 1.4;
        float duration = CombatPoseConfig.get(spin ? CombatPose.LONGSWORD_SPIN : CombatPose.HEAVY_LONGSWORD).blendIn();
        this.delay = duration * (spin ? SPIN_START : CUT_START);
        this.playTicks = Math.max(2.0f, duration * (spin ? SPIN_END - SPIN_START : CUT_END - CUT_START));
        this.lifetime = Mth.ceil(this.delay + this.playTicks);
        this.quadSize = BASE_SIZE * (size != 0.0 ? (float) Math.abs(size) : 1.0f);
        float shade = 0.85f + this.random.nextFloat() * 0.15f;
        setColor(shade, shade, shade);
        float yaw = dirX * dirX + dirZ * dirZ > 1.0e-6 ? (float) Mth.atan2(-dirX, -dirZ) : 0.0f;
        this.top = new Quaternionf().rotateY(yaw).rotateX(-Mth.HALF_PI);
        this.bottom = new Quaternionf(this.top).rotateY(Mth.PI);
        setSprite(sprites.get(0, 7));
    }

    private void resolveDirection() {
        this.resolved = true;
        if (this.spin) return;
        net.minecraft.world.entity.player.Player owner = null;
        double best = 1.0;
        for (net.minecraft.world.entity.player.Player player : this.level.players()) {
            double dx = player.getX() - this.ownerX, dz = player.getZ() - this.ownerZ;
            double distance = dx * dx + dz * dz;
            if (distance < best && com.craisinlord.fightclub.combat.LongswordSpin.isLongsword(player.getMainHandItem())) {
                best = distance;
                owner = player;
            }
        }
        if (owner == null) return;
        boolean leftHanded = owner.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
        this.mirrored = CombatClientPoses.backhand(owner) != leftHanded;
    }

    @Override protected float getU0() {
        return this.mirrored ? super.getU1() : super.getU0();
    }

    @Override protected float getU1() {
        return this.mirrored ? super.getU0() : super.getU1();
    }

    @Override public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) remove();
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float played = this.age + partialTick - this.delay;
        if (played < 0.0f) return;
        if (!this.resolved) resolveDirection();
        int frame = Math.min(7, (int) (played * 8.0f / this.playTicks));
        setSprite(this.sprites.get(frame, 7));
        extractRotatedQuad(state, camera, this.top, partialTick);
        extractRotatedQuad(state, camera, this.bottom, partialTick);
    }

    @Override public int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    @Override protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double xd, double yd, double zd, RandomSource random) {
            return new LongswordSweepParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
