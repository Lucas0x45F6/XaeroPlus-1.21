package xaeroplus.mixin.client.mc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaeroplus.XaeroPlus;
import xaeroplus.event.*;

import java.util.function.BiConsumer;

@Mixin(ClientPacketListener.class)
public class MixinClientPlayNetworkHandler {
    @Shadow private ClientLevel level;

    @Inject(method = "handleLevelChunkWithLight", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;updateLevelChunk(IILnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;)V"
    )) // on main thread before chunk data buf is read
    public void onChunkDataPacket(
        final ClientboundLevelChunkWithLightPacket packet,
        final CallbackInfo ci,
        @Share("seenChunk") LocalBooleanRef seenChunkRef) {
        seenChunkRef.set(level.getChunk(packet.getX(), packet.getZ(), ChunkStatus.FULL, false) != null);
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    public void onChunkData(
        final ClientboundLevelChunkWithLightPacket packet,
        final CallbackInfo ci,
        @Share("seenChunk") LocalBooleanRef seenChunkRef) {
        XaeroPlus.EVENT_BUS.call(new ChunkDataEvent(level.getChunk(packet.getX(), packet.getZ()), seenChunkRef.get()));
    }

    @WrapOperation(method = "handleChunkBlocksUpdate", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket;runUpdates(Ljava/util/function/BiConsumer;)V"
    ))
    public void onChunkBlocksUpdate(final ClientboundSectionBlocksUpdatePacket instance, final BiConsumer<BlockPos, BlockState> mutableBlockPos, final Operation<Void> original) {
        var event = new ChunkBlocksUpdateEvent(instance);
        event.setPhase(Phase.PRE);
        XaeroPlus.EVENT_BUS.call(event);
        original.call(instance, mutableBlockPos);
        event.setPhase(Phase.POST);
        XaeroPlus.EVENT_BUS.call(event);
    }

    @WrapOperation(method = "handleBlockUpdate", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V"
    ))
    public void onBlockUpdate(final ClientLevel instance, final BlockPos pos, final BlockState state, final int flags, final Operation<Void> original,
                              @Local(argsOnly = true) final ClientboundBlockUpdatePacket packet) {
        var event = new ChunkBlockUpdateEvent(packet);
        event.setPhase(Phase.PRE);
        XaeroPlus.EVENT_BUS.call(event);
        original.call(instance, pos, state, flags);
        event.setPhase(Phase.POST);
        XaeroPlus.EVENT_BUS.call(event);
    }

    @Inject(method = "close", at = @At(
        "RETURN" // after session is closed, including xaero session closes and mc level ref unset
    ))
    public void onClientSessionClose(final CallbackInfo ci) {
        XaeroPlus.EVENT_BUS.call(ClientPlaySessionFinalizedEvent.INSTANCE);
    }
}
