package de.serverhub.common;

/** Version-field metadata keeps the ordinary MOTD and protocol version untouched. */
public final class SleepSignal {
    private SleepSignal(){}
    public static String version(String original,boolean paused){return original.replaceAll("\\s*\\[serverhub:paused=[01]]","")+" [serverhub:paused="+(paused?1:0)+"]";}
    public static boolean paused(String version){return version!=null&&version.contains("[serverhub:paused=1]");}
}
