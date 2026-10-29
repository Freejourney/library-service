package com.elibrary.common;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

public final class Ids {
    private Ids() {}
    public static UUID parse(String value) {
        try { return UUID.fromString(value); }
        catch (Exception e) { throw new ApiException(400, "INVALID_ID", "Invalid identifier"); }
    }
    public static UUID v7() {
        long millis = Instant.now().toEpochMilli();
        long msb = ((millis & 0xffffffffffffL) << 16) | 0x7000 | (UUID.randomUUID().getLeastSignificantBits() & 0x0fff);
        long lsb = (UUID.randomUUID().getLeastSignificantBits() & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
    public static byte[] bytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    public static UUID fromBytes(byte[] bytes) { ByteBuffer b=ByteBuffer.wrap(bytes); return new UUID(b.getLong(), b.getLong()); }
}
