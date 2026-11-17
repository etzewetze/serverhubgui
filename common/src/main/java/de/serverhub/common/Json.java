package de.serverhub.common;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;

public final class Json {
    private static final Gson GSON=new GsonBuilder().disableHtmlEscaping().create();
    private Json(){}
    public static String write(Object value){return GSON.toJson(value);}
    public static JsonElement read(String value)throws IOException {try{return JsonParser.parseString(value);}catch(JsonParseException|StackOverflowError e){throw new IOException("Ungültiges JSON",e);}}
    public static JsonElement unwrap(JsonElement value){if(value.isJsonObject()&&value.getAsJsonObject().size()==1&&value.getAsJsonObject().has("result"))return value.getAsJsonObject().get("result");return value;}
    public static JsonElement get(JsonObject value,String name){for(var entry:value.entrySet())if(entry.getKey().equalsIgnoreCase(name))return entry.getValue();return JsonNull.INSTANCE;}
    public static String string(JsonObject value,String name,String fallback){var item=get(value,name);return item.isJsonPrimitive()?item.getAsString():fallback;}
    public static int integer(JsonObject value,String name,int fallback){try{return get(value,name).getAsInt();}catch(RuntimeException e){return fallback;}}
}
