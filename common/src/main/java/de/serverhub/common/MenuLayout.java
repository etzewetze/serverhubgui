package de.serverhub.common;

public final class MenuLayout {
    public static final int PAGE_SIZE = 6;
    private MenuLayout() {}
    public static int pages(int entries) { return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE); }
    public static int clampPage(int requested, int entries) { return Math.clamp(requested, 0, pages(entries)-1); }
    /** Two columns and three rows; the central inventory column is a divider. */
    public static int entryAt(int rawSlot) {
        if (rawSlot < 0 || rawSlot >= 45 || rawSlot % 9 == 4) return -1;
        return (rawSlot / 9 / 2) * 2 + (rawSlot % 9 < 4 ? 0 : 1);
    }
}
