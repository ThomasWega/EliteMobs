package com.magmaguy.elitemobs.dungeons;

import com.magmaguy.elitemobs.api.internal.RemovalReason;
import com.magmaguy.elitemobs.config.custombosses.CustomBossesConfig;
import com.magmaguy.elitemobs.config.custombosses.CustomBossesConfigFields;
import com.magmaguy.elitemobs.config.customtreasurechests.CustomTreasureChestConfigFields;
import com.magmaguy.elitemobs.config.customtreasurechests.CustomTreasureChestsConfig;
import com.magmaguy.elitemobs.config.dungeonpackager.DungeonPackagerConfigFields;
import com.magmaguy.elitemobs.dungeons.utility.DungeonUtils;
import com.magmaguy.elitemobs.mobconstructor.custombosses.RegionalBossEntity;
import com.magmaguy.elitemobs.treasurechest.TreasureChest;
import com.magmaguy.elitemobs.utils.WarningMessage;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class SchematicDungeonPackage extends SchematicPackage implements Dungeon {
    private final HashMap<String, Vector> rawSpawnLocations = new HashMap<>();
    private final HashMap<String, Vector> rawChestLocations = new HashMap<>();
    private int lowestLevel;
    private int highestLevel;

    public SchematicDungeonPackage(DungeonPackagerConfigFields dungeonPackagerConfigFields) {
        super(dungeonPackagerConfigFields);
        for (String string : dungeonPackagerConfigFields.getRelativeBossLocations()) {
            try {
                Vector vector = getVectorFromConfig(string.split(":")[1]);
                if (vector == null) continue;
                rawSpawnLocations.put(string.split(":")[0], vector);
            } catch (Exception ex) {
                new WarningMessage("Failed to correctly read entry " + string + " in schematic dungeon " + dungeonPackagerConfigFields.getFilename());
                continue;
            }
        }
        for (String string : dungeonPackagerConfigFields.getRelativeTreasureChestLocations()) {
            try {
                Vector vector = getVectorFromConfig(string.split(":")[1]);
                if (vector == null) continue;
                rawChestLocations.put(string.split(":")[0], vector);
            } catch (Exception ex) {
                new WarningMessage("Failed to correctly read entry " + string + " in schematic dungeon " + dungeonPackagerConfigFields.getFilename());
                continue;
            }
        }
    }

    private Vector getVectorFromConfig(String string) {
        try {
            String[] rawValues = string.split(",");
            double x = Double.parseDouble(rawValues[0]);
            double y = Double.parseDouble(rawValues[1]);
            double z = Double.parseDouble(rawValues[2]);
            return new Vector(x, y, z);
        } catch (Exception ex) {
            new WarningMessage("Failed to retrieve valid vector from " + string + " in configuration file " + dungeonPackagerConfigFields.getFilename());
            return null;
        }
    }

    @Override
    public void baseInitialization() {
        super.baseInitialization();
        for (String string : dungeonPackagerConfigFields.getRelativeBossLocations())
            if (!string.isEmpty()) {
                String parsedString = string.split(":")[0];
                if (!parsedString.isEmpty())
                    content.put(parsedString, this);
            }

        for (String string : dungeonPackagerConfigFields.getRelativeTreasureChestLocations())
            if (!string.isEmpty()) {
                String parsedString = string.split(":")[0];
                if (!parsedString.isEmpty())
                    content.put(parsedString, this);
            }
    }

    @Override
    public void initializeContent() {
        super.initializeContent();
        if (isInstalled) {
            getEntities();
            qualifyEntities();
            getChests();
        }
    }

    private void getEntities() {
        //Get the real spawn locations
        HashMap<String, Location> parsedBossLocations = new HashMap<>();
        for (Map.Entry<String, Vector> entry : rawSpawnLocations.entrySet())
            parsedBossLocations.put(entry.getKey(), dungeonPackagerConfigFields.getAnchorPoint().clone().add(entry.getValue()));
        //A bit dirty but this should get every boss in the dungeon
        for (RegionalBossEntity regionalBossEntity : RegionalBossEntity.getRegionalBossEntitySet())
            for (Map.Entry<String, Location> entry : parsedBossLocations.entrySet())
                if (regionalBossEntity.getCustomBossesConfigFields().getFilename().equals(entry.getKey()))
                    customBossEntityList.add(regionalBossEntity);
    }

    private void qualifyEntities() {
        //Initialize data related to the highest and lowest levels for informational purposes
        Pair<Integer, Integer> lowestAndHighestValues = DungeonUtils.getLowestAndHighestLevels(customBossEntityList);
        this.lowestLevel = lowestAndHighestValues.getKey();
        this.highestLevel = lowestAndHighestValues.getValue();
    }

    private void getChests() {
        //Get the real chest locations
        HashMap<String, Location> parsedChestLocations = new HashMap<>();
        for (Map.Entry<String, Vector> entry : rawChestLocations.entrySet())
            parsedChestLocations.put(entry.getKey(), dungeonPackagerConfigFields.getAnchorPoint().clone().add(entry.getValue()));
        for (TreasureChest treasureChest : TreasureChest.getTreasureChestHashMap().values())
            for (Map.Entry<String, Location> entry : parsedChestLocations.entrySet())
                if (treasureChest.getCustomTreasureChestConfigFields().getFilename().equals(entry.getKey()))
                    treasureChestList.add(treasureChest);
    }

    @Override
    public boolean install(Player player, boolean paste) {
        if (!super.install(player, paste)) return false;
        installBosses();
        installChests();
        return true;
    }

    private void installBosses() {
        for (Map.Entry<String, Vector> entry : rawSpawnLocations.entrySet()) {
            CustomBossesConfigFields customBossesConfigFields = CustomBossesConfig.getCustomBoss(entry.getKey());
            if (customBossesConfigFields == null) {
                new WarningMessage("Failed to get Regional Boss " + entry.getKey() + " in schematic dungeon " + dungeonPackagerConfigFields.getFilename() + " !");
                continue;
            }
            Location bossLocation = toRealPosition(entry.getValue());
            RegionalBossEntity regionalBossEntity = new RegionalBossEntity(customBossesConfigFields, bossLocation, true);
            regionalBossEntity.setEmPackage(this);
            regionalBossEntity.spawn(false);
            customBossEntityList.add(regionalBossEntity);
        }
    }

    private void installChests() {
        for (Map.Entry<String, Vector> entry : rawChestLocations.entrySet()) {
            CustomTreasureChestConfigFields customTreasureChestConfigFields = CustomTreasureChestsConfig.getCustomTreasureChestConfigFields().get(entry.getKey());
            if (customTreasureChestConfigFields == null) {
                new WarningMessage("Failed to get Treasure Chest " + entry.getKey() + " in schematic dungeon " + dungeonPackagerConfigFields.getFilename() + " !");
                continue;
            }
            Location chestLocation = toRealPosition(entry.getValue());
            //todo: this doesn't rotate the treasure chest orientations for now. That will be added later
            TreasureChest treasureChest = customTreasureChestConfigFields.addTreasureChest(chestLocation, 0);
            treasureChest.setEmPackage(this);
            treasureChestList.add(treasureChest);
        }
    }

    @Override
    public boolean uninstall(Player player) {
        if (!super.uninstall(player)) return false;
        customBossEntityList.forEach(customBossEntity -> customBossEntity.remove(RemovalReason.REMOVE_COMMAND));
        treasureChestList.forEach(TreasureChest::removeTreasureChest);
        customBossEntityList.clear();
        treasureChestList.clear();
        return true;
    }

    @Override
    public int getLowestLevel() {
        return lowestLevel;
    }

    @Override
    public int getHighestLevel() {
        return highestLevel;
    }

    public void removeBoss(RegionalBossEntity regionalBossEntity) {
        Vector bossVector = regionalBossEntity.getSpawnLocation().clone().subtract(dungeonPackagerConfigFields.getAnchorPoint()).toVector().rotateAroundY(dungeonPackagerConfigFields.getCalculatedRotation());
        getDungeonPackagerConfigFields().removeRelativeBossLocation(regionalBossEntity.getCustomBossesConfigFields(), bossVector);
    }

    public void addBoss(CustomBossesConfigFields customBossesConfigFields, Location location) {
        getDungeonPackagerConfigFields().addRelativeBossLocation(customBossesConfigFields, toRelativePosition(location));
    }

    public void addChest(String treasureChestFilename, Location location) {
        getDungeonPackagerConfigFields().addRelativeTreasureChests(treasureChestFilename, toRelativePosition(location));
    }
}