package com.example.balancenametag.net;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server -> client balance update on channel "balancenametag:balances".
 *
 * Wire format (big-endian, same as java.io.DataOutputStream):
 *   byte   version (=1)
 *   byte   flags   (bit 0: 1 = replace all known balances with this list)
 *   int    count   (max 2048)
 *   count x { long uuidMost, long uuidLeast, double balance }   (NaN = forget this player)
 */
public record BalanceSyncPayload(boolean replaceAll, Map<UUID, Double> balances) implements CustomPayload {
    public static final CustomPayload.Id<BalanceSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("balancenametag", "balances"));

    public static final PacketCodec<PacketByteBuf, BalanceSyncPayload> CODEC =
            PacketCodec.of(BalanceSyncPayload::write, BalanceSyncPayload::read);

    private static final int VERSION = 1;
    private static final int MAX_ENTRIES = 2048;
    private static final int ENTRY_BYTES = 16 + 8;

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(PacketByteBuf buf) {
        buf.writeByte(VERSION);
        buf.writeByte(replaceAll ? 1 : 0);
        buf.writeInt(balances.size());
        for (Map.Entry<UUID, Double> e : balances.entrySet()) {
            buf.writeUuid(e.getKey());
            buf.writeDouble(e.getValue());
        }
    }

    private static BalanceSyncPayload read(PacketByteBuf buf) {
        // Never throw on malformed data (that could disconnect the player): consume and ignore.
        try {
            if (buf.readableBytes() < 6) {
                return empty(buf);
            }
            int version = buf.readUnsignedByte();
            int flags = buf.readUnsignedByte();
            int count = buf.readInt();
            if (version != VERSION || count < 0 || count > MAX_ENTRIES
                    || (long) count * ENTRY_BYTES > buf.readableBytes()) {
                return empty(buf);
            }
            Map<UUID, Double> map = new HashMap<>();
            for (int i = 0; i < count; i++) {
                UUID id = buf.readUuid();
                map.put(id, buf.readDouble());
            }
            buf.skipBytes(buf.readableBytes());
            return new BalanceSyncPayload((flags & 1) != 0, map);
        } catch (RuntimeException e) {
            return empty(buf);
        }
    }

    private static BalanceSyncPayload empty(PacketByteBuf buf) {
        buf.skipBytes(buf.readableBytes());
        return new BalanceSyncPayload(false, Map.of());
    }
}
