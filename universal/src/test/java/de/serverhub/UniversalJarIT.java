package de.serverhub;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class UniversalJarIT {
    final Path jar=Path.of(System.getProperty("universal.jar"));
    @org.junit.jupiter.api.io.TempDir Path directory;
    static final class PlatformLoader extends URLClassLoader {
        final String forbidden;
        PlatformLoader(URL url,String forbidden){super(new URL[]{url},UniversalJarIT.class.getClassLoader());this.forbidden=forbidden;}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(name.startsWith(forbidden))throw new ClassNotFoundException("Other platform intentionally unavailable: "+name);
            if(!name.startsWith("de.serverhub."))return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){
                Class<?> result=findLoadedClass(name);if(result==null)result=findClass(name);if(resolve)resolveClass(result);return result;
            }
        }
    }
    @Test void paperCanLoadItsEntrypointWithoutVelocity()throws Exception{
        try(var loader=new PlatformLoader(jar.toUri().toURL(),"com.velocitypowered.")){
            Class<?> entry=Class.forName("de.serverhub.paper.ServerHubPaper",false,loader);
            assertTrue(entry.getDeclaredMethods().length>0);assertEquals(1,entry.getDeclaredConstructors().length);
            assertThrows(ClassNotFoundException.class,()->loader.loadClass("com.velocitypowered.api.proxy.Player"));
        }
    }
    @Test void velocityCanLoadItsEntrypointWithoutBukkit()throws Exception{
        try(var loader=new PlatformLoader(jar.toUri().toURL(),"org.bukkit.")){
            Class<?> entry=Class.forName("de.serverhub.velocity.ServerHubVelocity",false,loader);
            assertTrue(entry.getDeclaredMethods().length>0);assertEquals(1,entry.getDeclaredConstructors().length);
            assertThrows(ClassNotFoundException.class,()->loader.loadClass("org.bukkit.entity.Player"));
        }
    }
    @Test void packagedProxyConfigurationCanLoadItsPrivateYamlParserWithoutBukkit()throws Exception{
        try(var loader=new PlatformLoader(jar.toUri().toURL(),"org.bukkit.")){
            Class<?> config=loader.loadClass("de.serverhub.common.CentralMenuConfig");
            var constructor=config.getDeclaredConstructor(Path.class);constructor.setAccessible(true);
            Object instance=constructor.newInstance(directory);
            var load=config.getDeclaredMethod("load",Collection.class);load.setAccessible(true);load.invoke(instance,List.of("lobby","survival"));
            String yaml=java.nio.file.Files.readString(directory.resolve("servers.yml"));
            assertTrue(yaml.contains("lobby:"));assertTrue(yaml.contains("survival:"));
            assertNotNull(loader.loadClass("de.serverhub.libs.snakeyaml.Yaml"));
        }
    }
    @Test void theShadedPackBuilderRunsWithoutEitherServerApi()throws Exception{
        try(var loader=new PlatformLoader(jar.toUri().toURL(),"org.bukkit.");var archive=new JarFile(jar.toFile())){
            byte[] bundled=archive.getInputStream(archive.getJarEntry("serverhub-pack.zip")).readAllBytes();
            Class<?> options=loader.loadClass("de.serverhub.common.MenuOptions");Object defaults=options.getMethod("defaults").invoke(null);
            Object built=loader.loadClass("de.serverhub.common.PackWorkspace").getMethod("prepare",Path.class,byte[].class,options).invoke(null,directory,bundled,defaults);
            assertArrayEquals(bundled,(byte[])built.getClass().getMethod("bytes").invoke(built));
            assertNotNull(built.getClass().getMethod("fonts").invoke(built));
            assertNotNull(built.getClass().getMethod("ui").invoke(built));
            assertNotNull(loader.loadClass("de.serverhub.libs.gson.JsonParser"));
        }
    }
    @Test void jarIncludesBothDescriptorsAndTheExactPackButNoServerApis()throws Exception{
        try(var archive=new JarFile(jar.toFile())){
            for(String required:List.of("plugin.yml","velocity-plugin.json","config.yml","config.properties","servers.yml","font-widths.properties","serverhub-pack.zip","de/serverhub/common/Wire.class"))assertNotNull(archive.getEntry(required),required);
            assertFalse(archive.stream().anyMatch(e->e.getName().startsWith("org/bukkit/")||e.getName().startsWith("com/velocitypowered/api/")));
            assertNotNull(archive.getEntry("de/serverhub/paper/DialogMenu.class"));
            assertNull(archive.getEntry("de/serverhub/paper/ServerHubPaper$Menu.class"));
            assertArrayEquals(java.nio.file.Files.readAllBytes(jar.getParent().getParent().getParent().resolve("resource-pack-output/serverhub-pack.zip")),archive.getInputStream(archive.getEntry("serverhub-pack.zip")).readAllBytes());
        }
    }
}
