package de.serverhub.common;

public final class MenuLayout {
    public static final int PAGE_SIZE = 6;
    private MenuLayout() {}
    public static int pages(int entries) { return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE); }
    public static int clampPage(int requested, int entries) { return Math.clamp(requested, 0, pages(entries)-1); }
}
