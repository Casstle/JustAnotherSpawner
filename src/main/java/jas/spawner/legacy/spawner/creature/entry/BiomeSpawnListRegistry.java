package jas.spawner.legacy.spawner.creature.entry;

import jas.common.JASLog;
import jas.common.global.ImportedSpawnList;
import jas.common.helper.FileUtilities;
import jas.common.helper.GsonHelper;
import jas.spawner.legacy.spawner.biome.group.BiomeGroupRegistry;
import jas.spawner.legacy.spawner.biome.group.BiomeHelper;
import jas.spawner.legacy.spawner.biome.group.BiomeGroupRegistry.BiomeGroup;
import jas.spawner.legacy.spawner.biome.structure.StructureHandlerRegistry;
import jas.spawner.legacy.spawner.creature.entry.BiomeSpawnsSaveObject.BiomeSpawnsSaveObjectSerializer;
import jas.spawner.legacy.spawner.creature.handler.LivingGroupRegistry;
import jas.spawner.legacy.spawner.creature.handler.LivingHandler;
import jas.spawner.legacy.spawner.creature.handler.LivingHandlerRegistry;
import jas.spawner.legacy.spawner.creature.handler.LivingGroupRegistry.LivingGroup;
import jas.spawner.legacy.spawner.creature.type.CreatureType;
import jas.spawner.legacy.spawner.creature.type.CreatureTypeRegistry;
import jas.spawner.legacy.world.WorldProperties;
import jas.spawner.modern.DefaultProps;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import net.minecraft.entity.EntityLiving;
import net.minecraft.util.WeightedRandom;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import org.apache.logging.log4j.Level;

import com.google.common.base.CharMatcher;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.gson.Gson;

public final class BiomeSpawnListRegistry {

    /* Contains Mapping between BiomeGroupID, LivingType to valid SpawnListEntry */
    private ImmutableTable<String, String, Set<SpawnListEntry>> validSpawnListEntries;
    /* Contains Mapping Between BiomeGroupID, LivingType to invalid SpawnListEntry i.e. spawnWeight <=0 etc. */
    private ImmutableTable<String, String, Set<SpawnListEntry>> invalidSpawnListEntries;

    private boolean addSpawn(SpawnListEntry spawnListEntry,
            Table<String, String, Set<SpawnListEntry>> validSpawnListEntries,
            Table<String, String, Set<SpawnListEntry>> invalidSpawnListEntries) {
        LivingHandler handler = livingHandlerRegistry.getLivingHandler(spawnListEntry.livingGroupID);
        if (spawnListEntry.itemWeight > 0
                && livingHandlerRegistry.getLivingHandler(spawnListEntry.livingGroupID).shouldSpawn) {
            logSpawning(spawnListEntry, handler, true);
            Set<SpawnListEntry> spawnList = validSpawnListEntries.get(spawnListEntry.locationGroup,
                    handler.creatureTypeID);
            if (spawnList == null) {
                spawnList = new HashSet<SpawnListEntry>();
                validSpawnListEntries.put(spawnListEntry.locationGroup, handler.creatureTypeID, spawnList);
            }
            return spawnList.add(spawnListEntry);
        } else {
            logSpawning(spawnListEntry, handler, false);
            Set<SpawnListEntry> spawnList = invalidSpawnListEntries.get(spawnListEntry.locationGroup,
                    handler.creatureTypeID);
            if (spawnList == null) {
                spawnList = new HashSet<SpawnListEntry>();
                invalidSpawnListEntries.put(spawnListEntry.locationGroup, handler.creatureTypeID, spawnList);
            }
            return spawnList.add(spawnListEntry);
        }
    }

    private void logSpawning(SpawnListEntry spawnListEntry, LivingHandler handler, boolean success) {
        if (success) {
            JASLog.log().info("Adding SpawnListEntry %s of type %s to BiomeGroup %s", spawnListEntry.livingGroupID,
                    handler.creatureTypeID, spawnListEntry.locationGroup);
        } else {
            JASLog.log().debug(Level.INFO,
                    "Not adding Generated SpawnListEntry of %s due to Weight %s or ShouldSpawn %s, BiomeGroup: %s",
                    spawnListEntry.livingGroupID, spawnListEntry.itemWeight, handler, spawnListEntry.locationGroup);
        }
    }

    /**
     * Returns an Immutable copy of every BiomeGroup SpawnList applicable to the provided creature type and biome.
     * 
     * @param creatureType the entity category ID i.e. MONSTER, AMBIENT See {@link CreatureTypeRegistry}
     * @param biomePackageName Package name of the applicable Biome. See
     *            {@link BiomeHelper#getPackageName(BiomeGenBase)}
     * @return Immutable copy of Collection of SpawnListEntries
     */
    public ImmutableCollection<SpawnListEntry> getSpawnListFor(String creatureType, String biomePackageName) {
        ImmutableCollection<String> groupIDList = biomeGroupRegistry.packgNameToGroupIDs().get(biomePackageName);
        ArrayList<SpawnListEntry> biomeSpawnList = new ArrayList<SpawnListEntry>(30);
        for (String groupID : groupIDList) {
            Collection<SpawnListEntry> spawnList = validSpawnListEntries.get(groupID, creatureType);
            if (spawnList != null) {
                biomeSpawnList.addAll(spawnList);
            }
        }
        return ImmutableList.copyOf(biomeSpawnList);
    }

    /**
     * Returns an Immutable copy of every BiomeGroup SpawnList applicable to the provided biome.
     * 
     * @param biomePackageName Package name of the applicable Biome. See
     *            {@link BiomeHelper#getPackageName(BiomeGenBase)}
     * @return Immutable copy of Collection of SpawnListEntries
     */
    public ImmutableCollection<SpawnListEntry> getSpawnListFor(String biomePackageName) {
        ImmutableCollection<String> groupIDList = biomeGroupRegistry.packgNameToGroupIDs().get(biomePackageName);
        ArrayList<SpawnListEntry> biomeSpawnList = new ArrayList<SpawnListEntry>(30);
        for (String groupID : groupIDList) {
            for (Set<SpawnListEntry> biomeIDtoSpawnList : validSpawnListEntries.row(groupID).values()) {
                biomeSpawnList.addAll(biomeIDtoSpawnList);
            }
        }
        return ImmutableList.copyOf(biomeSpawnList);
    }

    private WorldProperties worldProperties;
    private BiomeGroupRegistry biomeGroupRegistry;
    private LivingGroupRegistry livingGroupRegistry;
    private LivingHandlerRegistry livingHandlerRegistry;
    private StructureHandlerRegistry structureHandlerRegistry;

    public BiomeSpawnListRegistry(WorldProperties worldProperties, BiomeGroupRegistry biomeGroupRegistry,
            LivingGroupRegistry livingGroupRegistry, CreatureTypeRegistry creatureTypeRegistry,
            LivingHandlerRegistry livingHandlerRegistry, StructureHandlerRegistry structureHandlerRegistry) {
        this.worldProperties = worldProperties;
        this.livingHandlerRegistry = livingHandlerRegistry;
        this.biomeGroupRegistry = biomeGroupRegistry;
        this.livingGroupRegistry = livingGroupRegistry;
        this.structureHandlerRegistry = structureHandlerRegistry;
    }

    /**
     * Called by customSpawner to get a random spawnListEntry entity
     * 
     * @param world
     * @param xCoord, yCoord, zCoord Random Coordinates nearby to Where Creature will spawn
     * @return Creature to Spawn
     */
    public SpawnListEntry getSpawnListEntryToSpawn(World world, CreatureType creatureType, int xCoord, int yCoord,
            int zCoord) {
        Collection<SpawnListEntry> structureSpawnList = structureHandlerRegistry.getSpawnListAt(world, xCoord, yCoord,
                zCoord);
        if (!structureSpawnList.isEmpty()) {
            JASLog.log().debug(Level.INFO, "Structure SpawnListEntry found for ChunkSpawning at %s, %s, %s", xCoord, yCoord,
                    zCoord);
            SpawnListEntry spawnListEntry = (SpawnListEntry) WeightedRandom.getRandomItem(world.rand,
                    structureSpawnList);
            return creatureType.isEntityOfType(livingHandlerRegistry, spawnListEntry.livingGroupID) ? spawnListEntry
                    : null;
        }
        ImmutableCollection<String> groupIDList = biomeGroupRegistry.packgNameToGroupIDs().get(
                BiomeHelper.getPackageName(world.getBiomeGenForCoords(xCoord, zCoord)));
        return getRandomValidEntry(world.rand, groupIDList, creatureType.typeID);
    }

    private SpawnListEntry getRandomValidEntry(Random random, ImmutableCollection<String> groupIDList,
            String creatureType) {
        int totalWeight = 0;

        for (String groupID : groupIDList) {
            Collection<SpawnListEntry> spawnList = validSpawnListEntries.get(groupID, creatureType);
            if (spawnList == null) {
                continue;
            }

            for (SpawnListEntry spawnListEntry : spawnList) {
                totalWeight += spawnListEntry.itemWeight;
            }
        }

        if (totalWeight <= 0) {
            return null;
        } else {
            int selectedWeight = random.nextInt(totalWeight) + 1;
            SpawnListEntry resultEntry = null;

            for (String groupID : groupIDList) {
                Collection<SpawnListEntry> spawnList = validSpawnListEntries.get(groupID, creatureType);
                if (spawnList == null) {
                    continue;
                }

                for (SpawnListEntry spawnListEntry : spawnList) {
                    resultEntry = spawnListEntry;
                    selectedWeight -= spawnListEntry.itemWeight;
                    if (selectedWeight <= 0) {
                        return resultEntry;
                    }
                }
            }
            return resultEntry;
        }
    }

    public void loadFromConfig(File configDirectory, ImportedSpawnList spawnList) {
        /* Contains Mapping between BiomeGroupID, LivingType to valid SpawnListEntry */
        Table<String, String, Set<SpawnListEntry>> validEntriesBuilder = HashBasedTable.create();
        /* Contains Mapping Between BiomeGroupID, LivingType to invalid SpawnListEntry i.e. spawnWeight <=0 etc. */
        Table<String, String, Set<SpawnListEntry>> invalidEntriesBuilder = HashBasedTable.create();
        Gson gson = GsonHelper.createGson(true, new Type[] { BiomeSpawnsSaveObject.class },
                new Object[] { new BiomeSpawnsSaveObjectSerializer(
                        worldProperties.getFolderConfiguration().sortCreatureByBiome) });
        HashSet<String> saveFilesProcessed = new HashSet<String>();

        File entriesDir = BiomeSpawnListRegistry.getFile(configDirectory,
                worldProperties.getFolderConfiguration().saveName, "");
        File[] files = FileUtilities.getFileInDirectory(entriesDir, ".cfg");
        for (File entriesFile : files) {
            BiomeSpawnsSaveObject saveObject = GsonHelper.readOrCreateFromGson(
                    FileUtilities.createReader(entriesFile, false), BiomeSpawnsSaveObject.class, gson,
                    worldProperties.getFolderConfiguration().sortCreatureByBiome);
            Set<SpawnListEntryBuilder> builders = saveObject.getBuilders();
            for (SpawnListEntryBuilder builder : builders) {
                // isBiomeGroup&EntityGroupValid(SpawnListBuidler) ? addSpawn(build()) : ignore
                if (biomeGroupRegistry.getBiomeGroup(builder.getLocationGroupId()) == null) {
                    JASLog.log().severe("BiomeGroup %s does not exist. Entry will be ignored [%s].",
                            builder.getLocationGroupId(), builder);
                } else if (livingGroupRegistry.getLivingGroup(builder.getLivingGroupId()) == null) {
                    JASLog.log().severe("LivingGroup %s does not exist. Entry will be ignored [%s].",
                            builder.getLivingGroupId(), builder);
                } else {
                    SpawnListEntry spawnListEntry = null;
                    try {
                        spawnListEntry = builder.build();
                    } catch (Exception e) {
                        JASLog.log().severe("Error building %s. Entry will be ignored [%s].", builder);
                    }
                    if (spawnListEntry != null) {
                        saveFilesProcessed.add(getSaveFileName(spawnListEntry.livingGroupID));
                        addSpawn(spawnListEntry, validEntriesBuilder, invalidEntriesBuilder);
                    }
                }
            }
        }

        /* Any files that were not Present/Processable should be created */
        Collection<LivingHandler> livingHandlers = livingHandlerRegistry.getLivingHandlers();
        for (LivingHandler handler : livingHandlers) {
            if (saveFilesProcessed.contains(getSaveFileName(handler.groupID))) {
                continue;
            }
            // String groupID = handler.groupID;
            if (handler.creatureTypeID.equalsIgnoreCase(CreatureTypeRegistry.NONE)) {
                JASLog.log().debug(Level.INFO,
                        "Not Generating SpawnList entries for %s as it does not have CreatureType. CreatureTypeID: %s",
                        handler.groupID, handler.creatureTypeID);
                continue;
            }
            
            for (BiomeGroup group : biomeGroupRegistry.iDToGroup().values()) {
                LivingGroup livGroup = livingGroupRegistry.getLivingGroup(handler.groupID);
                SpawnListEntry spawnListEntry = findVanillaSpawnListEntry(group, livGroup, spawnList);
                addSpawn(spawnListEntry, validEntriesBuilder, invalidEntriesBuilder);
            }
        }

        this.validSpawnListEntries = ImmutableTable.<String, String, Set<SpawnListEntry>> builder()
                .putAll(validEntriesBuilder).build();
        this.invalidSpawnListEntries = ImmutableTable.<String, String, Set<SpawnListEntry>> builder()
                .putAll(invalidEntriesBuilder).build();
    }

    private void extractSaveEntriesFromSpawnList(
            HashMap<String, Table<String, String, Set<SpawnListEntry>>> saveFileToEntries,
            Table<String, String, Set<SpawnListEntry>> spawnList) {
        for (Entry<String, Map<String, Set<SpawnListEntry>>> groupIdToEntry : spawnList.rowMap().entrySet()) {
            String biomeGroupId = groupIdToEntry.getKey();
            for (Entry<String, Set<SpawnListEntry>> livingTypeToEntry : groupIdToEntry.getValue().entrySet()) {
                String livingType = livingTypeToEntry.getKey();
                for (SpawnListEntry spawnListEntry : livingTypeToEntry.getValue()) {
                    String saveFileName = getSaveFileName(spawnListEntry.livingGroupID);
                    Table<String, String, Set<SpawnListEntry>> entryTable = saveFileToEntries.get(saveFileName);
                    if (entryTable == null) {
                        entryTable = HashBasedTable.create();
                        saveFileToEntries.put(saveFileName, entryTable);
                    }
                    Set<SpawnListEntry> entries = entryTable.get(biomeGroupId, livingType);
                    if (entries == null) {
                        entries = new HashSet<SpawnListEntry>();
                        entryTable.put(biomeGroupId, livingType, entries);
                    }
                    entries.add(spawnListEntry);
                }
            }
        }
    }

    private String getSaveFileName(String entityGroupID) {
        boolean universalCFG = worldProperties.getSavedFileConfiguration().universalDirectory;
        if (universalCFG) {
            return "Universal";
        } else {
            String modID;
            String[] mobNameParts = entityGroupID.split("\\.");
            if (mobNameParts.length >= 2) {
                String regexRetain = "qwertyuiopasdfghjklzxcvbnm0QWERTYUIOPASDFGHJKLZXCVBNM123456789";
                modID = CharMatcher.anyOf(regexRetain).retainFrom(mobNameParts[0]);
            } else {
                modID = "Vanilla";
            }
            return modID;
        }
    }

    /**
     * Searches For a Vanilla SpawnListEntry of the provided entity class in one of the biomes in the BiomeGroup.
     * 
     * Generates using defaults values (i.e. spawn rate == 0) if one doesn't exist.
     */
    private SpawnListEntry findVanillaSpawnListEntry(BiomeGroup group, LivingGroup livingGroup,
            ImportedSpawnList importedSpawnList) {
        for (String pckgNames : group.getBiomeNames()) {
            for (Integer biomeID : biomeGroupRegistry.pckgNameToBiomeID().get(pckgNames)) {
                Collection<net.minecraft.world.biome.BiomeGenBase.SpawnListEntry> spawnListEntries = importedSpawnList
                        .getSpawnableCreatureList(biomeID);
                for (String jasName : livingGroup.entityJASNames()) {
                    Class<? extends EntityLiving> livingClass = livingGroupRegistry.JASNametoEntityClass.get(jasName);
                    for (net.minecraft.world.biome.BiomeGenBase.SpawnListEntry spawnListEntry : spawnListEntries) {
                        if (spawnListEntry.entityClass.equals(livingClass)) {
                            return new SpawnListEntryBuilder(livingGroup.groupID, group.groupID)
                                    .setWeight(spawnListEntry.itemWeight).setMinChunkPack(spawnListEntry.minGroupCount)
                                    .setMaxChunkPack(spawnListEntry.maxGroupCount).build();
                        }
                    }
                }
            }
        }
        return new SpawnListEntryBuilder(livingGroup.groupID, group.groupID).build();
    }

    public void saveToConfig(File configDirectory) {
        Gson gson = GsonHelper.createGson(true, new Type[] { BiomeSpawnsSaveObject.class },
                new Object[] { new BiomeSpawnsSaveObjectSerializer(
                        worldProperties.getFolderConfiguration().sortCreatureByBiome) });
        HashMap<String, Table<String, String, Set<SpawnListEntry>>> saveFileToEntries = new HashMap<String, Table<String, String, Set<SpawnListEntry>>>();
        extractSaveEntriesFromSpawnList(saveFileToEntries, validSpawnListEntries);
        extractSaveEntriesFromSpawnList(saveFileToEntries, invalidSpawnListEntries);
        for (Entry<String, Table<String, String, Set<SpawnListEntry>>> entrySet : saveFileToEntries.entrySet()) {
            File saveFile = BiomeSpawnListRegistry.getFile(configDirectory,
                    worldProperties.getFolderConfiguration().saveName, entrySet.getKey());
            boolean sortCreatureByBiome = worldProperties.getFolderConfiguration().sortCreatureByBiome;
            GsonHelper.writeToGson(FileUtilities.createWriter(saveFile, true),
                    new BiomeSpawnsSaveObject(entrySet.getValue(), sortCreatureByBiome), gson);
        }
    }

    public static File getFile(File configDirectory, String saveName, String fileName) {
        String filePath = DefaultProps.WORLDSETTINGSDIR + saveName + "/" + DefaultProps.ENTITYSPAWNRDIR;
        if (fileName != null && !fileName.equals("")) {
            filePath = filePath.concat(fileName).concat(".cfg");
        }
        return new File(configDirectory, filePath);
    }
}