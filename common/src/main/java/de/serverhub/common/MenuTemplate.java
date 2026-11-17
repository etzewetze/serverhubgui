package de.serverhub.common;

import java.util.*;

public final class MenuTemplate {
    private MenuTemplate(){}
    public static String render(String template,String name,String target,MenuSnapshot.Status status,int players,int max){
        String state=switch(status){case ONLINE->"Online";case OFFLINE->"Offline";case SLEEPING->"Schläft";case STARTING->"Startet";case UNKNOWN->"Unbekannt";};
        return template.replace("{name}",name).replace("{target}",target).replace("{status}",state).replace("{players}",players<0?"?":Integer.toString(players)).replace("{online}",players<0?"?":Integer.toString(players)).replace("{max_players}",max<0?"?":Integer.toString(max)).replace("{max}",max<0?"?":Integer.toString(max));
    }
}
