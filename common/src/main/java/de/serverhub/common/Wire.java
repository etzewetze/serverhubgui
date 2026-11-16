package de.serverhub.common;

import java.io.*;
import java.util.*;

/** Private backend/proxy channel. Never forward this channel to clients. */
public final class Wire {
    public static final String CHANNEL = "serverhub:main";
    public static final int MAX_BYTES = 32700;
    public static final int MAX_SERVERS = 256;
    private static final int MAGIC = 0x53483201;
    public enum Type { HELLO, STATE, CONNECT, RESULT, CATALOG }
    public record Packet(Type type, UUID nonce, List<String> values) {
        public Packet { values = List.copyOf(values); }
        public String value(int index) { return values.get(index); }
    }
    private Wire() {}
    public static byte[] encode(Type type, UUID nonce, String... values) {
        int expected = type == Type.HELLO ? 0 : 1;
        if (type == Type.CATALOG ? values.length > MAX_SERVERS : values.length != expected)
            throw new IllegalArgumentException("Invalid field count");
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeByte(type.ordinal());
            out.writeLong(nonce.getMostSignificantBits()); out.writeLong(nonce.getLeastSignificantBits());
            if (type == Type.CATALOG) out.writeShort(values.length);
            for (String value : values) {
                if (value.length() > 256) throw new IllegalArgumentException("Field too long");
                if (type == Type.CATALOG && !validTarget(value)) throw new IllegalArgumentException("Invalid server name");
                out.writeUTF(value);
            }
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Packet too large");
            return bytes.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static Packet decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Packet too large");
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != MAGIC) throw new IOException("Wrong protocol");
        int ordinal = in.readUnsignedByte();
        if (ordinal >= Type.values().length) throw new IOException("Unknown message");
        Type type = Type.values()[ordinal];
        UUID nonce = new UUID(in.readLong(), in.readLong());
        int count = type == Type.CATALOG ? in.readUnsignedShort() : type == Type.HELLO ? 0 : 1;
        if (count > MAX_SERVERS) throw new IOException("Too many servers");
        var fields = new ArrayList<String>();
        for (int i=0;i<count;i++) {
            String value = in.readUTF();
            if (value.length() > 256) throw new IOException("Field too long");
            if (type == Type.CATALOG && !validTarget(value)) throw new IOException("Invalid server name");
            fields.add(value);
        }
        if (in.available() != 0) throw new IOException("Trailing data");
        if (type == Type.STATE && !Set.of("true", "false").contains(fields.getFirst())) throw new IOException("Invalid state");
        return new Packet(type, nonce, fields);
    }
    public static boolean validTarget(String name) { return name != null && name.matches("[A-Za-z0-9_.-]{1,64}"); }
}
