package de.serverhub.common;

import java.util.UUID;

/** Only a SUCCESSFUL response for our exact pack may unlock this session. */
public final class PackGate {
    public enum State { WAITING, READY, FAILED }
    private final UUID packId;
    private volatile State state = State.WAITING;
    public PackGate(UUID packId) { this.packId = packId; }
    public UUID packId() { return packId; }
    public State state() { return state; }
    public boolean ready() { return state == State.READY; }
    public synchronized boolean accept(UUID id, String status) {
        if (!packId.equals(id) || state == State.FAILED) return false;
        switch (status) {
            case "ACCEPTED", "DOWNLOADED" -> { return false; }
            case "SUCCESSFUL", "SUCCESSFULLY_LOADED" -> state = State.READY;
            case "DECLINED", "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD", "DISCARDED" -> state = State.FAILED;
            default -> { return false; }
        }
        return true;
    }
}
