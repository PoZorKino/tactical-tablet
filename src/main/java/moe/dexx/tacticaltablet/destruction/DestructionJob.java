package moe.dexx.tacticaltablet.destruction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import moe.dexx.tacticaltablet.config.ServerConfig;
import moe.dexx.tacticaltablet.strike.StrikeParams;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Digs the crater chunk by chunk, nearest to the target first. Call tick() once per server tick with a deadline;
 * the job resumes where it stopped. At most maxForcedChunks chunks are held by tickets at any time.
 * Server thread only.
 */
public final class DestructionJob {
    public static final TicketType<UUID> TICKET =
            TicketType.create("tactical_tablet_strike", Comparator.<UUID>naturalOrder());
    public static final int MAX_FIRES = 512;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    private final UUID strikeId;
    private final BlockPos target;
    private final StrikeParams params;
    private final ServerConfig config;
    private final CraterProfile profile;
    private final long[] order;
    private final int minY;
    private final List<Slot> window = new ArrayList<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private int nextToQueue;
    private int chunksDone;
    private int fires;
    private long blocksRemoved;
    private boolean stopped;
    private final long startNanos = System.nanoTime();
    private long endNanos;
    private boolean ended;

    private static final class Slot {
        final ChunkPos pos;
        boolean ticketed;
        /** Pending "does this chunk exist on disk" lookup; only used when generation of new chunks is off. */
        CompletableFuture<Boolean> existsProbe;
        boolean entitiesDone;
        boolean changed;
        int nextColumn;

        Slot(ChunkPos pos) {
            this.pos = pos;
        }
    }

    public DestructionJob(ServerLevel level, UUID strikeId, BlockPos target, StrikeParams params, ServerConfig config) {
        this.level = level;
        this.strikeId = strikeId;
        this.target = target.immutable();
        this.params = params;
        this.config = config;
        this.profile = new CraterProfile(params.radius(), params.power(),
                strikeId.getMostSignificantBits() ^ strikeId.getLeastSignificantBits());
        this.order = ChunkWaveOrder.compute(target.getX(), target.getZ(), params.radius());
        this.minY = level.getMinBuildHeight();
    }

    /**
     * @param deadlineNanos a System.nanoTime() value after which the job yields
     * @return true when there is nothing left to do
     */
    public boolean tick(long deadlineNanos) {
        if (stopped) {
            return true;
        }
        refillWindow();
        Iterator<Slot> iterator = window.iterator();
        while (iterator.hasNext()) {
            Slot slot = iterator.next();
            if (advance(slot, deadlineNanos)) {
                release(slot);
                iterator.remove();
                chunksDone++;
            }
            if (expired(deadlineNanos)) {
                break;
            }
        }
        refillWindow();
        if (isFinished()) {
            markEnded();
        }
        return isFinished();
    }

    public void stop() {
        stopped = true;
        for (Slot slot : window) {
            release(slot);
        }
        window.clear();
        markEnded();
    }

    private void markEnded() {
        if (!ended) {
            ended = true;
            endNanos = System.nanoTime();
        }
    }

    public boolean isFinished() {
        return stopped || (nextToQueue >= order.length && window.isEmpty());
    }

    public int chunksDone() {
        return chunksDone;
    }

    public int chunksTotal() {
        return order.length;
    }

    public int pendingChunks() {
        return window.size();
    }

    public long blocksRemoved() {
        return blocksRemoved;
    }

    /** @return how long the job has been running; frozen once it is finished */
    public long elapsedNanos() {
        return (ended ? endNanos : System.nanoTime()) - startNanos;
    }

    private static boolean expired(long deadlineNanos) {
        return System.nanoTime() - deadlineNanos >= 0;
    }

    private void refillWindow() {
        while (window.size() < config.maxForcedChunks && nextToQueue < order.length) {
            long packed = order[nextToQueue++];
            ChunkPos pos = new ChunkPos(ChunkWaveOrder.chunkX(packed), ChunkWaveOrder.chunkZ(packed));
            if (!level.getWorldBorder().isWithinBounds(pos)) {
                chunksDone++;
                continue;
            }
            Slot slot = new Slot(pos);
            if (config.generateMissingChunks || level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                addTicket(slot);
            } else {
                slot.existsProbe = level.getChunkSource().chunkMap.read(pos)
                        .thenApply(tag -> tag.isPresent()
                                && ChunkSerializer.getChunkTypeFromTag(tag.get()) == ChunkStatus.ChunkType.LEVELCHUNK)
                        .exceptionally(error -> false);
            }
            window.add(slot);
        }
    }

    private void addTicket(Slot slot) {
        level.getChunkSource().addRegionTicket(TICKET, slot.pos, 0, strikeId);
        slot.ticketed = true;
    }

    private void release(Slot slot) {
        if (slot.ticketed) {
            level.getChunkSource().removeRegionTicket(TICKET, slot.pos, 0, strikeId);
            slot.ticketed = false;
        }
    }

    /** @return true when the slot is finished and can leave the window */
    private boolean advance(Slot slot, long deadlineNanos) {
        if (slot.existsProbe != null) {
            if (!slot.existsProbe.isDone()) {
                return false;
            }
            boolean exists = slot.existsProbe.getNow(false);
            slot.existsProbe = null;
            if (!exists) {
                return true;
            }
            addTicket(slot);
            return false;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(slot.pos.x, slot.pos.z);
        if (chunk == null) {
            return false;
        }
        if (!slot.entitiesDone) {
            if (params.damageEntities()) {
                hurtEntities(chunk);
            }
            slot.entitiesDone = true;
        }
        while (slot.nextColumn < 256) {
            processColumn(chunk, slot, slot.nextColumn & 15, slot.nextColumn >> 4);
            slot.nextColumn++;
            if ((slot.nextColumn & 15) == 0 && expired(deadlineNanos)) {
                break;
            }
        }
        if (slot.nextColumn < 256) {
            return false;
        }
        finishChunk(chunk, slot);
        return true;
    }

    private void processColumn(LevelChunk chunk, Slot slot, int localX, int localZ) {
        int x = slot.pos.getMinBlockX() + localX;
        int z = slot.pos.getMinBlockZ() + localZ;
        int dx = x - target.getX();
        int dz = z - target.getZ();
        int depth = profile.depthAt(dx, dz);
        if (depth < 1) {
            return;
        }
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        int ground = findGround(chunk, x, z, top);
        if (ground < minY) {
            return;
        }
        int floor = CraterProfile.floorY(ground, depth, minY);
        for (int y = top; y > floor; y--) {
            cursor.set(x, y, z);
            BlockState old = chunk.getBlockState(cursor);
            if (old.isAir() || old.is(ModTags.STRIKE_PROTECTED)) {
                continue;
            }
            if (!params.destroyLiquids() && !old.getFluidState().isEmpty()) {
                continue;
            }
            replace(chunk, cursor.immutable(), old, AIR);
            slot.changed = true;
            blocksRemoved++;
        }
        if (profile.scorched(dx, dz)) {
            scorch(chunk, slot, x, floor, z, dx, dz);
        }
    }

    /** @return y of the highest block that blocks motion and is not a leaf or a log, or minY - 1 when there is none */
    private int findGround(LevelChunk chunk, int x, int z, int top) {
        for (int y = top; y >= minY; y--) {
            BlockState state = chunk.getBlockState(cursor.set(x, y, z));
            if (state.blocksMotion() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
                return y;
            }
        }
        return minY - 1;
    }

    private void replace(LevelChunk chunk, BlockPos pos, BlockState old, BlockState replacement) {
        if (old.hasBlockEntity()) {
            // Containers spill their contents from onRemove; empty them first so no item entities appear.
            Clearable.tryClear(chunk.getBlockEntity(pos));
        }
        chunk.setBlockState(pos, replacement, false);
        level.getChunkSource().blockChanged(pos);
        if (PoiTypes.hasPoi(old)) {
            level.onBlockStateChange(pos, old, replacement);
        }
    }

    private void scorch(LevelChunk chunk, Slot slot, int x, int floor, int z, int dx, int dz) {
        BlockPos floorPos = new BlockPos(x, floor, z);
        BlockState base = chunk.getBlockState(floorPos);
        if (base.isAir() || base.is(ModTags.STRIKE_PROTECTED) || !base.getFluidState().isEmpty() || !base.blocksMotion()) {
            return;
        }
        int roll = profile.hash(dx, dz, 1) % 100;
        Block melted = roll < 60 ? Blocks.BLACKSTONE : roll < 85 ? Blocks.BASALT : Blocks.MAGMA_BLOCK;
        replace(chunk, floorPos, base, melted.defaultBlockState());
        slot.changed = true;

        if (fires < MAX_FIRES && profile.hash(dx, dz, 2) % 40 == 0 && floor + 1 < level.getMaxBuildHeight()) {
            BlockPos firePos = floorPos.above();
            if (chunk.getBlockState(firePos).isAir()) {
                chunk.setBlockState(firePos, Blocks.FIRE.defaultBlockState(), false);
                level.getChunkSource().blockChanged(firePos);
                fires++;
            }
        }
    }

    private void hurtEntities(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        AABB box = new AABB(pos.getMinBlockX(), minY, pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxBuildHeight() + 64, pos.getMaxBlockZ() + 1);
        List<Entity> entities = level.getEntities((Entity) null, box, entity -> true);
        for (Entity entity : entities) {
            int x = entity.getBlockX();
            int z = entity.getBlockZ();
            if ((x >> 4) != pos.x || (z >> 4) != pos.z) {
                continue; // handled by the chunk that owns its column
            }
            int dx = x - target.getX();
            int dz = z - target.getZ();
            int depth = profile.depthAt(dx, dz);
            if (depth < 1) {
                continue;
            }
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            int ground = Math.max(minY, findGround(chunk, x, z, top));
            int floor = CraterProfile.floorY(ground, depth, minY);
            if (entity.getY() < floor - 1) {
                continue; // deep below the future crater floor
            }
            if (entity instanceof Player player) {
                double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
                float damage = (float) (40.0 * (1.0 - distance / params.radius()));
                if (damage > 0.0F) {
                    player.hurt(level.damageSources().explosion(null, null), damage);
                }
            } else {
                entity.discard();
            }
        }
    }

    private void finishChunk(LevelChunk chunk, Slot slot) {
        if (!slot.changed) {
            return;
        }
        // The queued light updates fix the light while the chunk stays loaded. If it unloads before the light
        // thread catches up, this flag makes the game relight it from scratch on the next load.
        chunk.setLightCorrect(false);
        chunk.setUnsaved(true);
        if (!level.shouldTickBlocksAt(slot.pos.toLong())) {
            // blockChanged() only reaches clients for ticking chunks; edge-of-view chunks get the whole chunk.
            List<ServerPlayer> watchers = level.getChunkSource().chunkMap.getPlayers(slot.pos, false);
            if (!watchers.isEmpty()) {
                ClientboundLevelChunkWithLightPacket packet =
                        new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
                for (ServerPlayer watcher : watchers) {
                    watcher.connection.send(packet);
                }
            }
        }
    }
}
