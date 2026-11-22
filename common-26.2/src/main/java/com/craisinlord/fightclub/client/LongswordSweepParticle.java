package com.craisinlord.fightclub.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class LongswordSweepParticle extends SingleQuadParticle {
    private static final float BASE_SIZE = 2.2f;
    private static final float CUT_START = 0.25f, CUT_END = 0.95f, SPIN_START = 0.12f, SPIN_END = 0.85f;

    private final float delay;
    private final float playTicks;
    private boolean mirrored;

    private final SpriteSet sprites;
    private final Quaternionf top;
    private final Quaternionf bottom;

    private LongswordSweepParticle(ClientLevel level, double x, double y, double z, double dirX, double size, double dirZ, SpriteSet sprites) {
        this(level, x, y, z, dirX, size, dirZ, sprites,
                CombatPoseConfig.get(size < 0.0 ? CombatPose.LONGSWORD_SPIN : CombatPose.HEAVY_LONGSWORD).blendIn(), 0.0f);
    }

    private LongswordSweepParticle(ClientLevel level, double x, double y, double z, double dirX, double size, double dirZ, SpriteSet sprites,
                                   float duration, float pitch) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        boolean spin = size < 0.0;
        this.delay = duration * (spin ? SPIN_START : CUT_START);
        this.playTicks = Math.max(2.0f, duration * (spin ? SPIN_END - SPIN_START : CUT_END - CUT_START));
        this.lifetime = Mth.ceil(this.delay + this.playTicks);
        this.quadSize = BASE_SIZE * (size != 0.0 ? (float) Math.abs(size) : 1.0f);
        float shade = 0.85f + this.random.nextFloat() * 0.15f;
        setColor(shade, shade, shade);
        float yaw = dirX * dirX + dirZ * dirZ > 1.0e-6 ? (float) Mth.atan2(-dirX, -dirZ) : 0.0f;
        this.top = new Quaternionf().rotateY(yaw).rotateX(-Mth.HALF_PI - pitch * Mth.DEG_TO_RAD);
        this.bottom = new Quaternionf(this.top).rotateY(Mth.PI);
        setSprite(sprites.get(0, 7));
    }

    private static final float CUT_REACH = 1.4f, CUT_DROP = 0.4f, CUT_PITCH_LIMIT = 60.0f;
    private static SpriteSet cutSprites;

    private Player owner;
    private Vec3 offset;

    public static void cut(Player owner, int swingTicks, boolean backhand) {
        Minecraft client = Minecraft.getInstance();
        if (cutSprites == null || client.level == null || !(owner.level() instanceof ClientLevel level)) return;
        float pitch = Mth.clamp(owner.getXRot(), -CUT_PITCH_LIMIT, CUT_PITCH_LIMIT);
        Vec3 forward = Vec3.directionFromRotation(pitch, owner.getYRot());
        Vec3 up = Vec3.directionFromRotation(pitch - 90.0f, owner.getYRot());
        Vec3 offset = new Vec3(0.0, owner.getEyeHeight(), 0.0).add(forward.scale(CUT_REACH)).subtract(up.scale(CUT_DROP));
        Vec3 at = owner.position().add(offset);
        Vec3 flat = Vec3.directionFromRotation(0.0f, owner.getYRot());
        LongswordSweepParticle particle = new LongswordSweepParticle(level, at.x, at.y, at.z, flat.x, 1.0, flat.z, cutSprites,
                Math.max(2, swingTicks), pitch);
        particle.owner = owner;
        particle.offset = offset;
        particle.mirrored = backhand != (owner.getMainArm() == HumanoidArm.LEFT);
        client.particleEngine.add(particle);
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
        if (this.owner != null && !this.owner.isRemoved()) {
            Vec3 at = this.owner.position().add(this.offset);
            setPos(at.x, at.y, at.z);
        }
        if (this.age++ >= this.lifetime) remove();
    }

    @Override public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float played = this.age + partialTick - this.delay;
        if (played < 0.0f) return;
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
            cutSprites = sprites;
        }

        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double xd, double yd, double zd, RandomSource random) {
            return new LongswordSweepParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
