package de.serverhub.common;

public final class MenuLayout {
    public static final int PAGE_SIZE = 6;
    private MenuLayout() {}
    public static int pages(int entries) { return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE); }
    public static int pages(int entries,int pageSize) { if(pageSize<1)throw new IllegalArgumentException("pageSize");return Math.max(1,(entries+pageSize-1)/pageSize); }
    public static int clampPage(int requested,int entries,int pageSize){return Math.clamp(requested,0,pages(entries,pageSize)-1);}
    public static int clampPage(int requested, int entries) { return Math.clamp(requested, 0, pages(entries)-1); }
}
