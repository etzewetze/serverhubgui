package de.serverhub.common;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** One complete, nonce-bound configuration; malformed snapshots never replace live settings. */
public record MenuSnapshot(UUID revision,MenuOptions options,List<Entry> entries,FontBook fonts) {
    public MenuSnapshot(UUID revision,MenuOptions options,List<Entry> entries){this(revision,options,entries,FontBook.empty());}
    public enum Status { UNKNOWN, ONLINE, OFFLINE, SLEEPING, STARTING }
    public record Entry(String target,String name,String color,int size,String permission,List<String> description,Status status,String family) {
        public Entry(String target,String name,String color,int size,String permission,List<String> description,Status status){this(target,name,color,size,permission,description,status,"");}
        public Entry {
            if(!Wire.validTarget(target)||name==null||name.isBlank()||name.length()>100||permission==null||permission.length()>128)
                throw new IllegalArgumentException("Ungültiger Servereintrag");
            MenuOptions.color(color);MenuOptions.font(size);Objects.requireNonNull(status);
            if(family==null||!family.isBlank()&&!FontBook.FAMILIES.contains(family))throw new IllegalArgumentException("Unbekannte Schriftart");
            description=List.copyOf(description);
            if(description.size()>5||description.stream().anyMatch(s->s.length()>128))throw new IllegalArgumentException("Maximal fünf Beschreibungszeilen mit je 128 Zeichen");
        }
    }
    public MenuSnapshot { Objects.requireNonNull(revision);Objects.requireNonNull(options);Objects.requireNonNull(fonts);entries=List.copyOf(entries);if(entries.size()>Wire.MAX_SERVERS)throw new IllegalArgumentException("Zu viele Server"); }
    public String[] encode(){
        try{
            var bytes=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(new GZIPOutputStream(bytes))){
                out.writeInt(2);out.writeLong(revision.getMostSignificantBits());out.writeLong(revision.getLeastSignificantBits());
                out.writeShort(options.values().size());
                for(var e:options.values().entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
                fonts.write(out);out.writeShort(entries.size());
                for(var e:entries){
                    out.writeUTF(e.target);out.writeUTF(e.name);out.writeUTF(e.color);out.writeByte(e.size);
                    out.writeUTF(e.permission);out.writeByte(e.description.size());
                    for(String line:e.description)out.writeUTF(line);
                    out.writeByte(e.status.ordinal());out.writeUTF(e.family);
                }
            }
            String b64=Base64.getEncoder().encodeToString(bytes.toByteArray());
            if(b64.length()>32000)throw new IllegalArgumentException("Menükonfiguration ist zu groß für den Plugin-Kanal");
            var parts=new ArrayList<String>();for(int i=0;i<b64.length();i+=240)parts.add(b64.substring(i,Math.min(i+240,b64.length())));
            String[] result=parts.toArray(String[]::new);Wire.encode(Wire.Type.MENU,new UUID(0,0),result);return result;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static MenuSnapshot decode(List<String> chunks)throws IOException {
        try{
            String joined=String.join("",chunks);if(joined.length()>32000)throw new IOException("Snapshot zu groß");
            byte[] raw;
            try(var zip=new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(joined)))){
                raw=zip.readNBytes(512*1024+1);if(raw.length>512*1024)throw new IOException("Snapshot entpackt zu groß");
            }
            var in=new DataInputStream(new ByteArrayInputStream(raw));
            if(in.readInt()!=2)throw new IOException("Unbekannte Snapshot-Version; Proxy und Backend gemeinsam aktualisieren");
            UUID revision=new UUID(in.readLong(),in.readLong());int count=in.readUnsignedShort();
            if(count>MenuOptions.DEFAULTS.size())throw new IOException("Zu viele Einstellungen");
            var values=new LinkedHashMap<String,String>();
            for(int i=0;i<count;i++)if(values.put(in.readUTF(),in.readUTF())!=null)throw new IOException("Doppelte Einstellung");
            var options=new MenuOptions(values);FontBook fonts=FontBook.read(in);int servers=in.readUnsignedShort();
            if(servers>Wire.MAX_SERVERS)throw new IOException("Zu viele Server");
            var entries=new ArrayList<Entry>();var seen=new HashSet<String>();
            for(int i=0;i<servers;i++){
                String target=in.readUTF(),name=in.readUTF(),color=in.readUTF();int size=in.readUnsignedByte();String permission=in.readUTF();
                int lines=in.readUnsignedByte();if(lines>5)throw new IOException("Zu viele Beschreibungszeilen");
                var description=new ArrayList<String>();for(int l=0;l<lines;l++)description.add(in.readUTF());
                int state=in.readUnsignedByte();if(state>=Status.values().length)throw new IOException("Ungültiger Serverstatus");
                if(!seen.add(target.toLowerCase(Locale.ROOT)))throw new IOException("Doppeltes Ziel");
                entries.add(new Entry(target,name,color,size,permission,description,Status.values()[state],in.readUTF()));
            }
            if(in.available()!=0)throw new IOException("Daten nach Snapshot-Ende");
            return new MenuSnapshot(revision,options,entries,fonts);
        }catch(IllegalArgumentException e){throw new IOException("Ungültige zentrale Menükonfiguration",e);}
    }
}
