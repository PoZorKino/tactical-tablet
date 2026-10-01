package moe.dexx.tacticaltablet.strike;

import java.util.UUID;

public record StrikeSummary(UUID owner, int chunksDone, int chunksTotal, long blocksRemoved, long wallMillis) {
}
