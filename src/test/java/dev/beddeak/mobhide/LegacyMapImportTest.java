package dev.beddeak.mobhide;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyMapImportTest {
    @TempDir Path world;
    private void pack() throws Exception {
        Path folder=world.resolve("datapacks/mobhide_travel/data/mh/function/go"); Files.createDirectories(folder);
        Files.writeString(world.resolve("datapacks/mobhide_travel/pack.mcmeta"),"{}");
        Files.writeString(folder.resolve("farm.mcfunction"),"tp @s 303.5 64 86.5 180 0\n");
        Files.writeString(folder.resolve("cave.mcfunction"),"tp @s 559.5 64 86.5 180 0\n");
        Files.writeString(folder.resolve("village.mcfunction"),"tp @s 815.5 64 86.5 180 0\n");
    }
    @Test void missingFarmAndMineAreAddedWithoutReplacingExistingMaps() throws Exception {
        pack(); var data=new YamlConfiguration();
        data.set("maps.village.name","내 마을"); data.set("maps.village.x",800);
        data.set("maps.desert.name","사막 시장"); data.set("maps.snow.name","눈 덮인 벌목장");
        assertEquals(2,LegacyMapImport.merge(data,world,"world"));
        assertEquals(5,data.getConfigurationSection("maps").getKeys(false).size());
        assertEquals("내 마을",data.getString("maps.village.name")); assertEquals(800,data.getInt("maps.village.x"));
        assertEquals(List.of(256,64,0),data.getIntegerList("maps.farm.min"));
        assertEquals(List.of(608,94,96),data.getIntegerList("maps.cave.max"));
        assertEquals(303.5,data.getDouble("maps.farm.x")); assertEquals(559.5,data.getDouble("maps.cave.x"));
        String before=data.saveToString(); assertEquals(0,LegacyMapImport.merge(data,world,"world"));
        assertEquals(before,data.saveToString());
    }
    @Test void unrelatedWorldDoesNotReceivePhantomMaps() throws Exception {
        var data=new YamlConfiguration(); assertEquals(0,LegacyMapImport.merge(data,world,"world"));
        assertFalse(data.contains("maps"));
    }
    @Test void freshCatalogueContainsAllThreeOriginalMaps() throws Exception {
        pack(); var data=new YamlConfiguration(); assertEquals(3,LegacyMapImport.merge(data,world,"world"));
        assertEquals("수정빛 폐광",data.getString("maps.cave.name"));
        assertEquals("노을빛 마을",data.getString("maps.village.name"));
    }
}
