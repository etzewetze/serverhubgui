package de.serverhub.common;

import java.util.*;

public record NewsCommand(boolean start,String target,String message) {
    public static NewsCommand parse(String[] args,String current){
        if(args.length<2||!args[0].equalsIgnoreCase("news")||!Set.of("start","stop").contains(args[1].toLowerCase(Locale.ROOT)))throw usage();
        boolean start=args[1].equalsIgnoreCase("start");int index=2;String target=current;
        if(args.length>index&&args[index].equalsIgnoreCase("--server")){if(args.length<=index+1)throw usage();target=args[index+1];index+=2;}
        if(!Wire.validTarget(target))throw new IllegalArgumentException("Ziel mit --server <Velocity-Name> angeben.");String message=String.join(" ",Arrays.copyOfRange(args,index,args.length));
        if(start&&(message.isBlank()||message.length()>NewsStore.MAX_MESSAGE)||!start&&!message.isBlank())throw usage();return new NewsCommand(start,target,message);
    }
    private static IllegalArgumentException usage(){return new IllegalArgumentException("/sh news start [--server Ziel] <Nachricht> | /sh news stop [--server Ziel]");}
    public String[] wire(){var result=new ArrayList<String>();result.add(start?"start":"stop");result.add(target);for(int i=0;i<message.length();i+=240)result.add(message.substring(i,Math.min(i+240,message.length())));return result.toArray(String[]::new);}
    public static NewsCommand decode(List<String> fields){if(fields.size()<2||!Wire.validTarget(fields.get(1))||!Set.of("start","stop").contains(fields.get(0)))throw usage();String message=String.join("",fields.subList(2,fields.size()));boolean start=fields.get(0).equals("start");if(start&&(message.isBlank()||message.length()>NewsStore.MAX_MESSAGE)||!start&&!message.isBlank())throw usage();return new NewsCommand(start,fields.get(1),message);}
    public static String[] content(String target,String text){var chunks=new ArrayList<String>();chunks.add(target);for(int i=0;i<text.length();i+=240)chunks.add(text.substring(i,Math.min(i+240,text.length())));return chunks.toArray(String[]::new);}
}
