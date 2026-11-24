package dev.beddeak.mobhide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Import ALL three arenas from the shipped travel datapack, not just config.yml's last arena. */
final class LegacyMapImport {
    private static final Pattern TP=Pattern.compile("^tp @s ([-.0-9]+) ([-.0-9]+) ([-.0-9]+) ([-.0-9]+) ([-.0-9]+)$");
    static int merge(YamlConfiguration data, Path worldFolder, String worldName) throws IOException {
        Path pack=worldFolder.resolve("datapacks/mobhide_travel");
        if(!Files.exists(pack.resolve("pack.mcmeta"))) return 0;
        int added=0;
        String[][] arenas={{"farm","농장"},{"cave","수정빛 폐광"},{"village","노을빛 마을"}};
        for(String[] arena:arenas) {
            String id=arena[0];
            if(data.contains("maps."+id)) continue;
            Path route=pack.resolve("data/mh/function/go/"+id+".mcfunction");
            if(!Files.isRegularFile(route)) continue;
            for(String line:Files.readAllLines(route)) {
                var match=TP.matcher(line.strip());
                if(!match.matches()) continue;
                double x=Double.parseDouble(match.group(1)),y=Double.parseDouble(match.group(2)),z=Double.parseDouble(match.group(3));
                if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) throw new IOException("Invalid legacy map: "+id);
                // This datapack's three 96x96 maps have their entry at offset 47.5,64,86.5.
                int minX=(int)Math.floor(x-47.5),minZ=(int)Math.floor(z-86.5);
                ConfigurationSection s=data.createSection("maps."+id);
                s.set("name",arena[1]); s.set("world",worldName);
                s.set("x",x); s.set("y",y); s.set("z",z); s.set("yaw",Double.parseDouble(match.group(4)));
                s.set("shape","cuboid"); s.set("min",List.of(minX,64,minZ)); s.set("max",List.of(minX+96,94,minZ+96));
                s.set("spawn-min-y",(int)y); s.set("spawn-max-y",(int)y);
                added++; break;
            }
        }
        return added;
    }
}
