package xaeroplus.module.impl;

import net.lenni0451.lambdaevents.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import xaeroplus.event.XaeroWorldChangeEvent;
import xaeroplus.module.Module;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

public class LavaHeatMap extends Module {

    private final Set<ChunkPos> scannedChunks = new HashSet<>();
    private final List<String> pendingDetections = new ArrayList<>();
    private long lastSendTime = 0L;
    private static final long SEND_INTERVAL_MS = 2000; // 2 seconds between chat sends

    public LavaHeatMap() {
        // Empty constructor
    }

    @Override
    public void onEnable() {
        scannedChunks.clear();
        pendingDetections.clear();
    }

    @Override
    public void onDisable() {
        scannedChunks.clear();
        pendingDetections.clear();
    }

    @EventHandler
    public void onTick(Minecraft minecraft) {
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null || mc.levelRenderer.viewArea == null) return;
        if (!level.dimension().location().getPath().equals("the_nether")) return;

        int playerChunkX = player.chunkPosition().x;
        int playerChunkZ = player.chunkPosition().z;
        int renderDistance = mc.options.getEffectiveRenderDistance();

        // Scan chunks within render distance
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

                if (scannedChunks.contains(chunkPos)) continue; // Already scanned

                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue; // Chunk not loaded yet

                scanChunk(level, chunk);
                scannedChunks.add(chunkPos); // Mark as scanned
            }
        }

        // Batch print detections
        long now = System.currentTimeMillis();
        if (!pendingDetections.isEmpty() && now - lastSendTime >= SEND_INTERVAL_MS) {
            StringBuilder sb = new StringBuilder("[LavaHeatMap] Detected Lava Pillars at: ");
            for (String coords : pendingDetections) {
                sb.append(coords).append(" ");
            }
            mc.player.sendSystemMessage(Component.literal(sb.toString().trim()));
            pendingDetections.clear();
            lastSendTime = now;
        }
    }

    private void scanChunk(Level level, LevelChunk chunk) {
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;

                for (int y = 110; y >= 35; y--) {
                    BlockPos pos = new BlockPos(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(pos);

                    if (state.getBlock() == Blocks.LAVA && state.getFluidState().isSource()) {
                        if (isLavaPillar(level, worldX, y, worldZ)) {
                            pendingDetections.add("(" + worldX + ", " + worldZ + ")");
                            break; // Only record the topmost source block
                        }
                    }
                }
            }
        }
    }

    private boolean isLavaPillar(final Level level, final int x, final int startY, final int z) {
        int flowingLavaCount = 0;
        for (int offsetY = 1; offsetY <= 10; offsetY++) {
            int checkY = startY - offsetY;
            if (checkY < 35) break;
            BlockState belowState = level.getBlockState(new BlockPos(x, checkY, z));

            if (belowState.getFluidState().isEmpty()) break;
            if (belowState.getFluidState().isSource()) break;

            if (belowState.getFluidState().getType() == Fluids.LAVA) {
                flowingLavaCount++;
            } else {
                break;
            }
        }
        return flowingLavaCount >= 2; // 3 block pillar minimum (1 source + 2 flowing)
    }

    @EventHandler
    public void onWorldChange(final XaeroWorldChangeEvent event) {
        scannedChunks.clear();
        pendingDetections.clear();
    }
}
