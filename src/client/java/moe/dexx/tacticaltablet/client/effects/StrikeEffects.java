package moe.dexx.tacticaltablet.client.effects;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import moe.dexx.tacticaltablet.ModSounds;
import moe.dexx.tacticaltablet.client.TacticalTabletClient;
import moe.dexx.tacticaltablet.client.config.ClientConfig;
import moe.dexx.tacticaltablet.client.state.ClientStrikes;
import moe.dexx.tacticaltablet.net.StrikeState;
import moe.dexx.tacticaltablet.strike.StrikePhase;
import moe.dexx.tacticaltablet.strike.StrikeTimeline;
import moe.dexx.tacticaltablet.strike.StrikeType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * What every player near a strike sees and hears: the beams, the fireball, the shockwave, the flash, particles,
 * camera shake and the sounds of each phase. Driven by the strike states the server sends.
 */
public final class StrikeEffects {
    /** Ticks a hit keeps animating. */
    private static final int HIT_LIFE = 50;
    /** How long a kinetic rod stays standing in the ground. */
    private static final int ROD_LIFE = 200;
    private static final int BEAM_TICKS = 16;
    private static final int FIREBALL_TICKS = 26;
    private static final int SHOCKWAVE_TICKS = 45;
    private static final float BEAM_HEIGHT = 800.0F;

    private record Hit(long tick, double x, double y, double z, int radius, float scale, StrikeType type, int life) {
    }

    private static final class Tracker {
        StrikeState state;
        boolean own;
        int salvosHit;
        int countdownSecond = -1;
        int chargeCue;
        boolean flyby;
    }

    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();
    private static final List<Hit> HITS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private StrikeEffects() {
    }

    // ---------------------------------------------------------------- per tick

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            TRACKERS.clear();
            HITS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        long now = level.getGameTime();
        HITS.removeIf(hit -> now - hit.tick() > hit.life() || now < hit.tick() - 40);

        Set<UUID> alive = new HashSet<>();
        for (StrikeState state : ClientStrikes.all()) {
            alive.add(state.id());
        }
        Iterator<Map.Entry<UUID, Tracker>> ended = TRACKERS.entrySet().iterator();
        while (ended.hasNext()) {
            Tracker tracker = ended.next().getValue();
            if (alive.contains(tracker.state.id())) {
                continue;
            }
            StrikePhase last = tracker.state.phase();
            if (last == StrikePhase.IMPACT) {
                landRemaining(minecraft, level, tracker, now);
            }
            if (tracker.own && (last == StrikePhase.IMPACT || last == StrikePhase.DESTROYING)) {
                play(minecraft, ModSounds.STRIKE_COMPLETE, 1.0F, 1.0F);
            }
            ended.remove();
        }

        for (StrikeState state : ClientStrikes.all()) {
            Tracker tracker = TRACKERS.computeIfAbsent(state.id(), id -> new Tracker());
            StrikePhase previous = tracker.state == null ? null : tracker.state.phase();
            tracker.state = state;
            tracker.own = state.owner().equals(minecraft.player.getUUID());
            long ticks = Math.max(0L, now - state.phaseStartTick());
            boolean entered = previous != state.phase();
            float volume = audibility(minecraft, tracker);
            switch (state.phase()) {
                case COUNTDOWN -> {
                    int second = (int) Math.ceil(ClientStrikes.ticksLeft(state) / 20.0);
                    if (tracker.own && second != tracker.countdownSecond && second > 0) {
                        play(minecraft, ModSounds.COUNTDOWN_BEEP, second <= 3 ? 1.5F : 1.0F, 1.0F);
                    }
                    tracker.countdownSecond = second;
                }
                case CHARGE -> {
                    // The whine rises three times: at the start, when the cannon is revealed and just before the shot.
                    int cue = ticks >= 360 ? 3 : ticks >= 280 ? 2 : 1;
                    if (cue != tracker.chargeCue) {
                        tracker.chargeCue = cue;
                        play(minecraft, ModSounds.CANNON_CHARGE, 0.8F + 0.25F * cue, volume);
                    }
                }
                case TRAVEL -> {
                    if (entered) {
                        play(minecraft, ModSounds.CANNON_FIRE, 1.0F, volume);
                    }
                    if (!tracker.flyby && ticks >= StrikeTimeline.TRAVEL_TICKS - 30) {
                        tracker.flyby = true;
                        play(minecraft, ModSounds.LASER_FLYBY, 1.0F, volume);
                    }
                }
                case IMPACT -> {
                    while (tracker.salvosHit < state.salvos()
                            && ticks >= (long) tracker.salvosHit * StrikeTimeline.IMPACT_TICKS_PER_EXTRA_SALVO) {
                        long hitTick = state.phaseStartTick() + (long) tracker.salvosHit * StrikeTimeline.IMPACT_TICKS_PER_EXTRA_SALVO;
                        land(minecraft, level, tracker, tracker.salvosHit, hitTick);
                        tracker.salvosHit++;
                    }
                }
                case DESTROYING -> {
                    if (previous == StrikePhase.IMPACT) {
                        landRemaining(minecraft, level, tracker, now);
                    }
                    if (ticks % 30 == 0) {
                        play(minecraft, ModSounds.DESTRUCTION, 0.8F + RANDOM.nextFloat() * 0.4F, 0.8F * volume);
                    }
                    smoulder(level, state);
                }
                default -> {
                }
            }
        }
    }

    /** Salvos the client's clock had not reached when the server moved on still land, all at once. */
    private static void landRemaining(Minecraft minecraft, ClientLevel level, Tracker tracker, long now) {
        while (tracker.salvosHit < tracker.state.salvos()) {
            land(minecraft, level, tracker, tracker.salvosHit, now);
            tracker.salvosHit++;
        }
    }

    private static void land(Minecraft minecraft, ClientLevel level, Tracker tracker, int index, long tick) {
        StrikeState state = tracker.state;
        double x = state.target().getX() + 0.5;
        double y = state.target().getY();
        double z = state.target().getZ() + 0.5;
        float scale = 1.0F;
        if (index > 0) {
            // Later salvos walk around the zone; the place of each one is the same on every client.
            Random placed = new Random(state.id().getLeastSignificantBits() ^ index * 0x9E3779B97F4A7C15L);
            double angle = placed.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(placed.nextDouble()) * state.radius() * 0.6;
            x += Math.cos(angle) * distance;
            z += Math.sin(angle) * distance;
            scale = 0.55F;
            int blockX = Mth.floor(x);
            int blockZ = Mth.floor(z);
            if (level.hasChunk(blockX >> 4, blockZ >> 4)) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                if (surface > level.getMinBuildHeight()) {
                    y = surface;
                }
            }
        }
        HITS.add(new Hit(tick, x, y, z, state.radius(), scale, state.type(), state.type() == StrikeType.KINETIC ? ROD_LIFE : HIT_LIFE));
        play(minecraft, ModSounds.EXPLOSION, index == 0 ? 0.9F : 0.9F + RANDOM.nextFloat() * 0.3F, audibility(minecraft, tracker));

        ClientConfig config = TacticalTabletClient.config();
        if (!config.visualEffects) {
            return;
        }
        int budget = Math.max(24, config.particleBudget() / state.salvos()) * (state.type() == StrikeType.METEOR ? 2 : 1);
        double spread = Math.max(3.0, Math.min(state.radius(), 48) * scale);
        level.addParticle(ParticleTypes.FLASH, true, x, y + 2.0, z, 0.0, 0.0, 0.0);
        for (int i = 0; i < budget; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(RANDOM.nextDouble()) * spread;
            double px = x + Math.cos(angle) * distance;
            double pz = z + Math.sin(angle) * distance;
            double py = y + 0.5 + RANDOM.nextDouble() * 3.0;
            double outX = Math.cos(angle);
            double outZ = Math.sin(angle);
            switch (i % 5) {
                case 0 -> level.addParticle(ParticleTypes.EXPLOSION, true, px, py + RANDOM.nextDouble() * spread * 0.4, pz, 0.0, 0.0, 0.0);
                case 1 -> level.addParticle(ParticleTypes.LARGE_SMOKE, true, px, py, pz,
                        outX * 0.1, 0.15 + RANDOM.nextDouble() * 0.45, outZ * 0.1);
                case 2 -> level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, px, py, pz,
                        outX * 0.03, 0.08 + RANDOM.nextDouble() * 0.14, outZ * 0.03);
                case 3 -> level.addParticle(ParticleTypes.FLAME, true, px, py, pz,
                        outX * (0.2 + RANDOM.nextDouble() * 0.6), 0.1 + RANDOM.nextDouble() * 0.5, outZ * (0.2 + RANDOM.nextDouble() * 0.6));
                default -> level.addParticle(ParticleTypes.LAVA, true, px, py, pz, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Smoke hanging over the zone while the server is still carving the crater. */
    private static void smoulder(ClientLevel level, StrikeState state) {
        ClientConfig config = TacticalTabletClient.config();
        if (!config.visualEffects) {
            return;
        }
        int count = Math.max(1, config.particleBudget() / 200);
        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(RANDOM.nextDouble()) * state.radius();
            int blockX = Mth.floor(state.target().getX() + 0.5 + Math.cos(angle) * distance);
            int blockZ = Mth.floor(state.target().getZ() + 0.5 + Math.sin(angle) * distance);
            if (!level.hasChunk(blockX >> 4, blockZ >> 4)) {
                continue;
            }
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
            level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, blockX + 0.5, surface + 0.5, blockZ + 0.5,
                    0.0, 0.06 + RANDOM.nextDouble() * 0.08, 0.0);
        }
    }

    /** How loud a strike is for this player: full for the owner, fading with distance from the zone for others. */
    private static float audibility(Minecraft minecraft, Tracker tracker) {
        if (tracker.own) {
            return 1.0F;
        }
        double distance = Math.sqrt(minecraft.player.distanceToSqr(Vec3.atCenterOf(tracker.state.target())));
        return (float) Mth.clamp(1.0 - (distance - tracker.state.radius()) / 400.0, 0.0, 1.0);
    }

    private static void play(Minecraft minecraft, SoundEvent sound, float pitch, float volume) {
        if (!TacticalTabletClient.config().soundEffects || volume <= 0.02F) {
            return;
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ---------------------------------------------------------------- camera and overlay

    private static float nearness(Vec3 camera, Hit hit, double falloff) {
        double dx = camera.x - hit.x();
        double dz = camera.z - hit.z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        return (float) Mth.clamp(1.0 - (distance - hit.radius()) / falloff, 0.0, 1.0);
    }

    private static double now(Minecraft minecraft, float partialTick) {
        return minecraft.level.getGameTime() + partialTick;
    }

    /** @return how far to swing the camera this frame, in degrees */
    public static float shake(Vec3 camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientConfig config = TacticalTabletClient.config();
        if (minecraft.level == null || !config.cameraShake || !config.visualEffects) {
            return 0.0F;
        }
        double now = now(minecraft, partialTick);
        float total = 0.0F;
        for (Hit hit : HITS) {
            double age = now - hit.tick();
            if (age < 0.0 || age > 35.0) {
                continue;
            }
            float fade = 1.0F - (float) age / 35.0F;
            total += fade * fade * 2.2F * hit.scale() * nearness(camera, hit, 600.0);
        }
        for (Tracker tracker : TRACKERS.values()) {
            if (tracker.state.phase() == StrikePhase.DESTROYING) {
                Vec3 target = Vec3.atCenterOf(tracker.state.target());
                double distance = Math.sqrt(camera.distanceToSqr(target));
                total += 0.12F * (float) Mth.clamp(1.0 - (distance - tracker.state.radius()) / 200.0, 0.0, 1.0);
            }
        }
        return Math.min(total, 4.0F);
    }

    /** White-out when a salvo lands. */
    public static void renderFlash(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || HITS.isEmpty() || !TacticalTabletClient.config().visualEffects) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double now = now(minecraft, partialTick);
        float strongest = 0.0F;
        for (Hit hit : HITS) {
            double age = now - hit.tick();
            if (age < 0.0 || age > 14.0) {
                continue;
            }
            float fade = 1.0F - (float) age / 14.0F;
            strongest = Math.max(strongest, fade * fade * 0.9F * hit.scale() * nearness(camera, hit, 500.0));
        }
        int alpha = (int) (Mth.clamp(strongest, 0.0F, 1.0F) * 255.0F);
        if (alpha > 2) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | 0xFFFFFF);
        }
    }

    // ---------------------------------------------------------------- world rendering

    public static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !TacticalTabletClient.config().visualEffects) {
            return;
        }
        boolean incoming = false;
        for (Tracker tracker : TRACKERS.values()) {
            incoming |= tracker.state.phase() == StrikePhase.TRAVEL;
        }
        if (HITS.isEmpty() && !incoming) {
            return;
        }
        double now = now(minecraft, context.tickDelta());
        Vec3 camera = context.camera().getPosition();
        Matrix4f matrix = context.matrixStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Hit hit : HITS) {
            double age = now - hit.tick();
            if (hit.type() == StrikeType.KINETIC && age >= 0.0 && age < hit.life()) {
                spireBody(buffer, matrix, (float) (hit.x() - camera.x), (float) (hit.y() - camera.y), (float) (hit.z() - camera.z),
                        hit, (float) age);
            }
        }
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Tracker tracker : TRACKERS.values()) {
            StrikeState state = tracker.state;
            if (state.phase() != StrikePhase.TRAVEL) {
                continue;
            }
            // A thin guide beam finds the target just before the salvo arrives.
            double left = state.phaseStartTick() + StrikeTimeline.TRAVEL_TICKS - now;
            if (left < 30.0) {
                float closeness = (float) Mth.clamp(1.0 - left / 30.0, 0.0, 1.0);
                beam(buffer, matrix, (float) (state.target().getX() + 0.5 - camera.x), (float) (state.target().getY() - camera.y),
                        (float) (state.target().getZ() + 0.5 - camera.z), 0.15F + 0.5F * closeness, 0.5F * closeness);
            }
        }
        for (Hit hit : HITS) {
            double age = now - hit.tick();
            if (age < 0.0 || age > hit.life()) {
                continue;
            }
            boolean kinetic = hit.type() == StrikeType.KINETIC;
            boolean meteor = hit.type() == StrikeType.METEOR;
            float x = (float) (hit.x() - camera.x);
            float y = (float) (hit.y() - camera.y);
            float z = (float) (hit.z() - camera.z);
            if (kinetic) {
                spireGlow(buffer, matrix, x, y, z, hit, (float) age);
            }
            if (age < BEAM_TICKS && !kinetic && !meteor) {
                float life = (float) age / BEAM_TICKS;
                float width = Math.max(1.2F, hit.radius() * 0.05F) * hit.scale() * (age < 3.0 ? (float) age / 3.0F : 1.0F - 0.7F * life);
                beam(buffer, matrix, x, y, z, width, 1.0F - life * life);
            }
            if (age < FIREBALL_TICKS) {
                float life = (float) age / FIREBALL_TICKS;
                float size = meteor ? 1.3F : 0.85F;
                float radius = hit.radius() * size * hit.scale() * (0.15F + 0.85F * easeOut(life));
                fireball(buffer, matrix, x, y, z, Math.max(3.0F, radius), 0.55F * (1.0F - life), meteor);
            }
            if (age < SHOCKWAVE_TICKS) {
                float life = (float) age / SHOCKWAVE_TICKS;
                float radius = (hit.radius() * 1.25F * hit.scale() + 6.0F) * easeOut(life);
                float height = Math.max(4.0F, hit.radius() * 0.1F) * hit.scale() * (1.0F - 0.5F * life) * (meteor ? 1.7F : 1.0F);
                shockwave(buffer, matrix, x, y, z, radius, height, 0.6F * (1.0F - life), kinetic ? 6 : 0, kinetic);
            }
        }

        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static float easeOut(float value) {
        float inverse = 1.0F - Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - inverse * inverse * inverse;
    }

    /** A column of light from the ground into the sky, turned to face the camera. Coordinates are camera-relative. */
    private static void beam(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float width, float alpha) {
        float length = (float) Math.sqrt(x * x + z * z);
        float rightX = length < 0.01F ? 1.0F : -z / length;
        float rightZ = length < 0.01F ? 0.0F : x / length;
        float bottom = y - 4.0F;
        float top = y + BEAM_HEIGHT;
        // White core.
        float cx = rightX * width;
        float cz = rightZ * width;
        buffer.vertex(matrix, x - cx, bottom, z - cz).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        buffer.vertex(matrix, x + cx, bottom, z + cz).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        buffer.vertex(matrix, x + cx, top, z + cz).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        buffer.vertex(matrix, x - cx, top, z - cz).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        // Blue halo fading outwards on both sides.
        float hx = rightX * width * 4.0F;
        float hz = rightZ * width * 4.0F;
        for (int sign = -1; sign <= 1; sign += 2) {
            buffer.vertex(matrix, x + cx * sign, bottom, z + cz * sign).color(0.45F, 0.85F, 1.0F, alpha * 0.7F).endVertex();
            buffer.vertex(matrix, x + hx * sign, bottom, z + hz * sign).color(0.3F, 0.6F, 1.0F, 0.0F).endVertex();
            buffer.vertex(matrix, x + hx * sign, top, z + hz * sign).color(0.3F, 0.6F, 1.0F, 0.0F).endVertex();
            buffer.vertex(matrix, x + cx * sign, top, z + cz * sign).color(0.45F, 0.85F, 1.0F, alpha * 0.7F).endVertex();
        }
    }

    /** A glowing dome growing out of the point of impact. */
    private static void fireball(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float radius, float alpha, boolean red) {
        int rings = 8;
        int sectors = 28;
        for (int ring = 0; ring < rings; ring++) {
            float p0 = Mth.HALF_PI * ring / rings;
            float p1 = Mth.HALF_PI * (ring + 1) / rings;
            float y0 = Mth.sin(p0) * radius;
            float y1 = Mth.sin(p1) * radius;
            float r0 = Mth.cos(p0) * radius;
            float r1 = Mth.cos(p1) * radius;
            // Orange at the ground, white-hot at the crown.
            float low = red ? 0.3F : 0.6F;
            float g0 = low + (1.0F - low) * ring / rings;
            float g1 = low + (1.0F - low) * (ring + 1) / rings;
            for (int sector = 0; sector < sectors; sector++) {
                float a0 = Mth.TWO_PI * sector / sectors;
                float a1 = Mth.TWO_PI * (sector + 1) / sectors;
                buffer.vertex(matrix, x + Mth.cos(a0) * r0, y + y0, z + Mth.sin(a0) * r0).color(1.0F, g0, g0 * 0.6F, alpha).endVertex();
                buffer.vertex(matrix, x + Mth.cos(a1) * r0, y + y0, z + Mth.sin(a1) * r0).color(1.0F, g0, g0 * 0.6F, alpha).endVertex();
                buffer.vertex(matrix, x + Mth.cos(a1) * r1, y + y1, z + Mth.sin(a1) * r1).color(1.0F, g1, g1 * 0.6F, alpha).endVertex();
                buffer.vertex(matrix, x + Mth.cos(a0) * r1, y + y1, z + Mth.sin(a0) * r1).color(1.0F, g1, g1 * 0.6F, alpha).endVertex();
            }
        }
    }

    /** A wall of dust and light racing outwards along the ground. */
    private static void shockwave(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float radius, float height, float alpha,
                                  int fixedSegments, boolean orange) {
        int segments = fixedSegments > 0 ? fixedSegments : Mth.clamp((int) radius, 24, 160);
        float green = orange ? 0.5F : 0.9F;
        float blue = orange ? 0.15F : 0.75F;
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            float x0 = x + Mth.cos(a0) * radius;
            float z0 = z + Mth.sin(a0) * radius;
            float x1 = x + Mth.cos(a1) * radius;
            float z1 = z + Mth.sin(a1) * radius;
            buffer.vertex(matrix, x0, y - 2.0F, z0).color(1.0F, green, blue, alpha).endVertex();
            buffer.vertex(matrix, x1, y - 2.0F, z1).color(1.0F, green, blue, alpha).endVertex();
            buffer.vertex(matrix, x1, y + height, z1).color(1.0F, green * 0.9F, blue * 0.7F, 0.0F).endVertex();
            buffer.vertex(matrix, x0, y + height, z0).color(1.0F, green * 0.9F, blue * 0.7F, 0.0F).endVertex();
        }
    }

    /** Height of the rod standing in the ground; it drops in from above during the first ticks. */
    private static float spireHeight(Hit hit, float age) {
        return (150.0F + hit.radius() * 0.6F) * hit.scale() * Mth.clamp(age / 2.5F, 0.0F, 1.0F);
    }

    private static float spireWidth(Hit hit) {
        return Math.max(1.6F, hit.radius() * 0.022F) * hit.scale();
    }

    /** The dark body of the rod: a six-sided needle that narrows towards its tip. Camera-relative coordinates. */
    private static void spireBody(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, Hit hit, float age) {
        float height = spireHeight(hit, age);
        float width = spireWidth(hit);
        float alpha = Mth.clamp((hit.life() - age) / 30.0F, 0.0F, 1.0F);
        int sides = 6;
        float topWidth = width * 0.35F;
        for (int i = 0; i < sides; i++) {
            float a0 = Mth.TWO_PI * i / sides;
            float a1 = Mth.TWO_PI * (i + 1) / sides;
            float shade = 0.05F + 0.04F * (i % 3);
            buffer.vertex(matrix, x + Mth.cos(a0) * width, y - 3.0F, z + Mth.sin(a0) * width).color(shade, shade, shade * 1.2F, alpha).endVertex();
            buffer.vertex(matrix, x + Mth.cos(a1) * width, y - 3.0F, z + Mth.sin(a1) * width).color(shade, shade, shade * 1.2F, alpha).endVertex();
            buffer.vertex(matrix, x + Mth.cos(a1) * topWidth, y + height, z + Mth.sin(a1) * topWidth).color(shade, shade, shade * 1.2F, alpha).endVertex();
            buffer.vertex(matrix, x + Mth.cos(a0) * topWidth, y + height, z + Mth.sin(a0) * topWidth).color(shade, shade, shade * 1.2F, alpha).endVertex();
        }
    }

    /** Glowing orange bands up the rod and a hot ring at its foot, drawn additively. */
    private static void spireGlow(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, Hit hit, float age) {
        float height = spireHeight(hit, age);
        float width = spireWidth(hit);
        float alpha = Mth.clamp((hit.life() - age) / 30.0F, 0.0F, 1.0F);
        float cool = Mth.clamp(1.0F - age / 120.0F, 0.0F, 1.0F);
        int bands = Math.max(4, (int) (height / 14.0F));
        for (int band = 0; band < bands; band++) {
            float t0 = (band + 0.15F) / bands;
            float t1 = (band + 0.3F) / bands;
            float w0 = Mth.lerp(t0, width, width * 0.35F) * 1.04F;
            float w1 = Mth.lerp(t1, width, width * 0.35F) * 1.04F;
            float pulse = 0.55F + 0.45F * Mth.sin(age * 0.35F - band * 0.6F);
            float strength = alpha * (0.35F + 0.65F * cool) * pulse;
            for (int i = 0; i < 6; i++) {
                float a0 = Mth.TWO_PI * i / 6;
                float a1 = Mth.TWO_PI * (i + 1) / 6;
                buffer.vertex(matrix, x + Mth.cos(a0) * w0, y + height * t0, z + Mth.sin(a0) * w0).color(1.0F, 0.45F, 0.12F, strength).endVertex();
                buffer.vertex(matrix, x + Mth.cos(a1) * w0, y + height * t0, z + Mth.sin(a1) * w0).color(1.0F, 0.45F, 0.12F, strength).endVertex();
                buffer.vertex(matrix, x + Mth.cos(a1) * w1, y + height * t1, z + Mth.sin(a1) * w1).color(1.0F, 0.45F, 0.12F, strength).endVertex();
                buffer.vertex(matrix, x + Mth.cos(a0) * w1, y + height * t1, z + Mth.sin(a0) * w1).color(1.0F, 0.45F, 0.12F, strength).endVertex();
            }
        }
        float foot = width * (2.4F + 3.0F * cool);
        float strength = alpha * (0.3F + 0.7F * cool);
        for (int i = 0; i < 6; i++) {
            float a0 = Mth.TWO_PI * i / 6;
            float a1 = Mth.TWO_PI * (i + 1) / 6;
            buffer.vertex(matrix, x + Mth.cos(a0) * width, y + 0.2F, z + Mth.sin(a0) * width).color(1.0F, 0.9F, 0.7F, strength).endVertex();
            buffer.vertex(matrix, x + Mth.cos(a1) * width, y + 0.2F, z + Mth.sin(a1) * width).color(1.0F, 0.9F, 0.7F, strength).endVertex();
            buffer.vertex(matrix, x + Mth.cos(a1) * foot, y + 5.0F * cool + 0.5F, z + Mth.sin(a1) * foot).color(1.0F, 0.4F, 0.1F, 0.0F).endVertex();
            buffer.vertex(matrix, x + Mth.cos(a0) * foot, y + 5.0F * cool + 0.5F, z + Mth.sin(a0) * foot).color(1.0F, 0.4F, 0.1F, 0.0F).endVertex();
        }
    }
}
