package net.mattseq.echoes_of_time;

import net.mattseq.echoes_of_time.events.ClientEvents;
import net.mattseq.echoes_of_time.snapshots.WorldSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;

public class RewindController {
    private static final long MAX_SNAPSHOT_AGE_MS = 10000; // Store 10 seconds of snapshots
    private static final long SNAPSHOT_INTERVAL_MS = 500;  // Take a snapshot every 0.5 seconds

    private final ServerPlayer player;
    private final Deque<CompoundTag> rewindBuffer = new ArrayDeque<>();

    public boolean isRewinding = false;

    public long rewindSteps;
    public long rewindStepCounter = 0;

    private int tickDelay = 5; // delay between snapshots during rewind
    private int tickCounter = 0;

    private long lastSnapshotTime = 0;

    public RewindController(ServerPlayer player) {
        this.player = player;
    }

    public void tick(long currentTimeMillis) {
        if (isRewinding) return; // Don't record during rewind

        // record
        if (currentTimeMillis - lastSnapshotTime >= SNAPSHOT_INTERVAL_MS) {
            WorldSnapshot snapshot = SnapshotManager.captureSnapshot(player);
            rewindBuffer.addLast(snapshot.toNbt());
            lastSnapshotTime = currentTimeMillis;
        }

        // Trim old snapshots
        while (rewindBuffer.size() > (MAX_SNAPSHOT_AGE_MS/SNAPSHOT_INTERVAL_MS)) {
            rewindBuffer.removeFirst(); // remove the oldest snapshot
        }
    }

    public void startRewind(float percent) {
        if (rewindBuffer.isEmpty()) return;
        isRewinding = true;
        rewindSteps = (long) (percent * (MAX_SNAPSHOT_AGE_MS / SNAPSHOT_INTERVAL_MS));
        tickCounter = 0;
        ClientEvents.lockMovement = true;
        player.setInvulnerable(true);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void stopRewind() {
        isRewinding = false;
        rewindStepCounter = 0;
        rewindBuffer.clear();
        tickCounter = 0;
        ClientEvents.lockMovement = false;
        player.setInvulnerable(false);
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isRewinding) return;

        tickCounter++;

        if (tickCounter >= tickDelay) {
            tickCounter = 0;

            if (rewindStepCounter < rewindSteps && !rewindBuffer.isEmpty()) {
                CompoundTag tag = rewindBuffer.pollLast();
                WorldSnapshot snapshot = WorldSnapshot.fromNbt(tag);
                SnapshotManager.restoreSnapshot(player, snapshot);
                rewindStepCounter += 1;
            } else {
                stopRewind();
            }
        }
    }
}