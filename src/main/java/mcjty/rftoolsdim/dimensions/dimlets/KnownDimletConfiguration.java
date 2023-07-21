package mcjty.rftoolsdim.dimensions.dimlets;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import mcjty.lib.varia.EntityTools;
import mcjty.lib.varia.Logging;
import mcjty.rftoolsdim.RFToolsDim;
import mcjty.rftoolsdim.config.DimletRules;
import mcjty.rftoolsdim.config.Filter;
import mcjty.rftoolsdim.config.GeneralConfiguration;
import mcjty.rftoolsdim.config.MobConfiguration;
import mcjty.rftoolsdim.config.Settings;
import mcjty.rftoolsdim.dimensions.DimensionInformation;
import mcjty.rftoolsdim.dimensions.description.SkyDescriptor;
import mcjty.rftoolsdim.dimensions.dimlets.types.DimletType;
import mcjty.rftoolsdim.dimensions.types.*;
import mcjty.rftoolsdim.dimensions.world.BiomeControllerMapping;
import mcjty.rftoolsdim.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;

import java.lang.reflect.Modifier;
import java.util.*;

public class KnownDimletConfiguration {

    private static Set<DimletKey> craftableDimlets = new HashSet<>();
    private static SortedMap<DimletKey, Settings> knownDimlets = new TreeMap<>();

    public static Settings getSettings(DimletKey key) {
        initDimlets();
        return knownDimlets.get(key);
    }

    public static SortedMap<DimletKey, Settings> getKnownDimlets() {
        initDimlets();
        return knownDimlets;
    }

    public static void init() {
        knownDimlets.clear();
        craftableDimlets.clear();
    }

    private static void initDimlets() {
        if (!knownDimlets.isEmpty()) {
            return;
        }

        for (int i = 0 ; i <= 9 ; i++) {
            initDimlet(new DimletKey(DimletType.DIMLET_DIGIT, Integer.toString(i)), RFToolsDim.MODID);
        }

        if (GeneralConfiguration.voidOnly) {
            initDimlet(new DimletKey(DimletType.DIMLET_TERRAIN, TerrainType.TERRAIN_VOID.getId()), RFToolsDim.MODID);
        } else {
            Arrays.stream(TerrainType.values()).forEach(t -> initDimlet(new DimletKey(DimletType.DIMLET_TERRAIN, t.getId()), RFToolsDim.MODID));
        }

        Arrays.stream(ControllerType.values()).forEach(t -> initDimlet(new DimletKey(DimletType.DIMLET_CONTROLLER, t.getId()), RFToolsDim.MODID));
        Arrays.stream(FeatureType.values()).forEach(t -> initDimlet(new DimletKey(DimletType.DIMLET_FEATURE, t.getId()), RFToolsDim.MODID));
        Arrays.stream(EffectType.values()).forEach(t -> initDimlet(new DimletKey(DimletType.DIMLET_EFFECT, t.getId()), RFToolsDim.MODID));
        Arrays.stream(StructureType.values()).forEach(t -> initDimlet(new DimletKey(DimletType.DIMLET_STRUCTURE, t.getId()), RFToolsDim.MODID));
        Arrays.stream(SpecialType.values()).forEach(t -> initDimlet(new DimletKey(DimletType.DIMLET_SPECIAL, t.getId()), RFToolsDim.MODID));

        Biome.REGISTRY.iterator().forEachRemaining(KnownDimletConfiguration::initBiomeDimlet);

        MobConfiguration.mobClasses.keySet().forEach(KnownDimletConfiguration::initMobDimlet);
        initMobDimlet(DimletObjectMapping.DEFAULT_ID);

        FluidRegistry.getRegisteredFluids().entrySet().stream().forEach(KnownDimletConfiguration::initFluidDimlet);
        Block.REGISTRY.forEach(KnownDimletConfiguration::initMaterialDimlet);

        initDimlet(new DimletKey(DimletType.DIMLET_MATERIAL, Blocks.STONE.getRegistryName() + "@0"), "minecraft");
        initDimlet(new DimletKey(DimletType.DIMLET_LIQUID, Blocks.WATER.getRegistryName() + "@0"), "minecraft");

        initDimlet(new DimletKey(DimletType.DIMLET_TIME, "Normal"), RFToolsDim.MODID);
        initDimlet(new DimletKey(DimletType.DIMLET_TIME, "Noon"), RFToolsDim.MODID);
        initDimlet(new DimletKey(DimletType.DIMLET_TIME, "Midnight"), RFToolsDim.MODID);
        initDimlet(new DimletKey(DimletType.DIMLET_TIME, "Morning"), RFToolsDim.MODID);
        initDimlet(new DimletKey(DimletType.DIMLET_TIME, "Evening"), RFToolsDim.MODID);
        initDimlet(new DimletKey(DimletType.DIMLET_TIME, "Fast"), RFToolsDim.MODID);
        initDimlet(new DimletKey(DimletType.DIMLET_TIME, "Slow"), RFToolsDim.MODID);
//        addExtraInformation(keyTimeNormal, "With this normal dimlet you will get", "default day/night timing");

        initSkyDimlets();
        initWeatherDimlets();

        BiomeControllerMapping.setupControllerBiomes();
    }

    private static void initWeatherDimlets() {
        initWeatherDimlet("Default", WeatherType.WEATHER_DEFAULT);
        initWeatherDimlet("no.rain", WeatherType.WEATHER_NORAIN);
        initWeatherDimlet("light.rain", WeatherType.WEATHER_LIGHTRAIN);
        initWeatherDimlet("hard.rain", WeatherType.WEATHER_HARDRAIN);
        initWeatherDimlet("no.thunder", WeatherType.WEATHER_NOTHUNDER);
        initWeatherDimlet("light.thunder", WeatherType.WEATHER_LIGHTTHUNDER);
        initWeatherDimlet("hard.thunder", WeatherType.WEATHER_HARDTHUNDER);
    }

    private static void initWeatherDimlet(String id, WeatherType weatherType) {
        DimletKey key = new DimletKey(DimletType.DIMLET_WEATHER, id);
        initDimlet(key, RFToolsDim.MODID);
        WeatherRegistry.registerWeather(key, weatherType);
    }

    private static void initSkyDimlets() {
        // Day brightness
        initSkyDimlet("normal.day", new SkyDescriptor.Builder().sunBrightnessFactor(1.0f).build(), false);
        initSkyDimlet("dark.day", new SkyDescriptor.Builder().sunBrightnessFactor(0.4f).skyColorFactor(0.6f, 0.6f, 0.6f).build(), false);

        // Night brightness
        initSkyDimlet("normal.night", new SkyDescriptor.Builder().starBrightnessFactor(1.0f).build(), false);
        initSkyDimlet("bright.night", new SkyDescriptor.Builder().starBrightnessFactor(1.5f).build(), false);
        initSkyDimlet("dark.night", new SkyDescriptor.Builder().starBrightnessFactor(0.4f).build(), false);

        // Sky color
        initSkyDimlet("red", new SkyDescriptor.Builder().skyColorFactor(1.0f, 0.2f, 0.2f).build(), false);
        initSkyDimlet("dark.red", new SkyDescriptor.Builder().skyColorFactor(0.6f, 0.0f, 0.0f).build(), false);
        initSkyDimlet("green", new SkyDescriptor.Builder().skyColorFactor(0.2f, 1.0f, 0.2f).build(), false);
        initSkyDimlet("dark.green", new SkyDescriptor.Builder().skyColorFactor(0f, 0.6f, 0f).build(), false);
        initSkyDimlet("blue", new SkyDescriptor.Builder().skyColorFactor(0.2f, 0.2f, 1.0f).build(), false);
        initSkyDimlet("dark.blue", new SkyDescriptor.Builder().skyColorFactor(0.0f, 0.0f, 0.6f).build(), false);
        initSkyDimlet("yellow", new SkyDescriptor.Builder().skyColorFactor(1.0f, 1.0f, 0.2f).build(), false);
        initSkyDimlet("cyan", new SkyDescriptor.Builder().skyColorFactor(0.2f, 1.0f, 1.0f).build(), false);
        initSkyDimlet("dark.cyan", new SkyDescriptor.Builder().skyColorFactor(0.0f, 0.6f, 0.6f).build(), false);
        initSkyDimlet("purple", new SkyDescriptor.Builder().skyColorFactor(1.0f, 0.2f, 1.0f).build(), false);
        initSkyDimlet("dark.purple", new SkyDescriptor.Builder().skyColorFactor(0.6f, 0, 0.6f).build(), false);
        initSkyDimlet("black", new SkyDescriptor.Builder().skyColorFactor(0.0f, 0.0f, 0.0f).build(), false);
        initSkyDimlet("gold", new SkyDescriptor.Builder().skyColorFactor(1.0f, 0.6f, 0.0f).build(), false);
        initSkyDimlet("white", new SkyDescriptor.Builder().skyColorFactor(1.0f, 1.0f, 1.0f).build(), false);

        // Fog color
        initSkyDimlet("normal.fog", new SkyDescriptor.Builder().fogColorFactor(1.0f, 1.0f, 1.0f).build(), false);
        initSkyDimlet("black.fog", new SkyDescriptor.Builder().fogColorFactor(0.0f, 0.0f, 0.0f).build(), false);
        initSkyDimlet("red.fog", new SkyDescriptor.Builder().fogColorFactor(1.0f, 0.2f, 0.2f).build(), false);
        initSkyDimlet("green.fog", new SkyDescriptor.Builder().fogColorFactor(0.2f, 1.0f, 0.2f).build(), false);
        initSkyDimlet("blue.fog", new SkyDescriptor.Builder().fogColorFactor(0.2f, 0.2f, 1.0f).build(), false);
        initSkyDimlet("yellow.fog", new SkyDescriptor.Builder().fogColorFactor(1.0f, 1.0f, 0.2f).build(), false);
        initSkyDimlet("cyan.fog", new SkyDescriptor.Builder().fogColorFactor(0.2f, 1.0f, 1.0f).build(), false);
        initSkyDimlet("purple.fog", new SkyDescriptor.Builder().fogColorFactor(1.0f, 0.2f, 1.0f).build(), false);

        // Sky type
        initSkyDimlet("normal", new SkyDescriptor.Builder().skyType(SkyType.SKY_NORMAL).build(), false);
        initSkyDimlet("ender", new SkyDescriptor.Builder().skyType(SkyType.SKY_ENDER).build(), false);
        initSkyDimlet("inferno", new SkyDescriptor.Builder().skyType(SkyType.SKY_INFERNO).build(), false);
        initSkyDimlet("stars1", new SkyDescriptor.Builder().skyType(SkyType.SKY_STARS1).build(), false);
        initSkyDimlet("stars2", new SkyDescriptor.Builder().skyType(SkyType.SKY_STARS2).build(), false);

        // Celestial bodies
        initSkyDimlet("body.normal", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_NORMAL).build(), false);   // False because we don't want to select this randomly.
        initSkyDimlet("body.none", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_NONE).build(), false);   // False because we don't want to select this randomly.
        initSkyDimlet("body.sun", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_SUN).build(), true);
        initSkyDimlet("body.large.sun", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_LARGESUN).build(), true);
        initSkyDimlet("body.small.sun", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_SMALLSUN).build(), true);
        initSkyDimlet("body.red.sun", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_REDSUN).build(), true);
        initSkyDimlet("body.moon", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_MOON).build(), true);
        initSkyDimlet("body.large.moon", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_LARGEMOON).build(), true);
        initSkyDimlet("body.small.moon", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_SMALLMOON).build(), true);
        initSkyDimlet("body.red.moon", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_REDMOON).build(), true);
        initSkyDimlet("body.planet", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_PLANET).build(), true);
        initSkyDimlet("body.large.planet", new SkyDescriptor.Builder().addBody(CelestialBodyType.BODY_LARGEPLANET).build(), true);

        // Cloud color
        initSkyDimlet("normal.clouds", new SkyDescriptor.Builder().cloudColorFactor(1.0f, 1.0f, 1.0f).build(), false);
        initSkyDimlet("black.clouds", new SkyDescriptor.Builder().cloudColorFactor(0.0f, 0.0f, 0.0f).build(), false);
        initSkyDimlet("red.clouds", new SkyDescriptor.Builder().cloudColorFactor(1.0f, 0.2f, 0.2f).build(), false);
        initSkyDimlet("green.clouds", new SkyDescriptor.Builder().cloudColorFactor(0.2f, 1.0f, 0.2f).build(), false);
        initSkyDimlet("blue.clouds", new SkyDescriptor.Builder().cloudColorFactor(0.2f, 0.2f, 1.0f).build(), false);
        initSkyDimlet("yellow.clouds", new SkyDescriptor.Builder().cloudColorFactor(1.0f, 1.0f, 0.2f).build(), false);
        initSkyDimlet("cyan.clouds", new SkyDescriptor.Builder().cloudColorFactor(0.2f, 1.0f, 1.0f).build(), false);
        initSkyDimlet("purple.clouds", new SkyDescriptor.Builder().cloudColorFactor(1.0f, 0.2f, 1.0f).build(), false);
    }

    private static void initSkyDimlet(String id, SkyDescriptor descriptor, boolean body) {
        DimletKey key = new DimletKey(DimletType.DIMLET_SKY, id);
        initDimlet(key, RFToolsDim.MODID);
        SkyRegistry.registerSky(key, descriptor, body);
    }

    private static void initBiomeDimlet(Biome biome) {
        ResourceLocation registryName = biome.getRegistryName();
        if (registryName != null) {
            DimletKey key = new DimletKey(DimletType.DIMLET_BIOME, registryName.toString());
            initDimlet(key, biome.getRegistryName().getNamespace());
        }
    }

    private static void initMobDimlet(String id) {
        if (DimletObjectMapping.DEFAULT_ID.equals(id)) {
            DimletKey key = new DimletKey(DimletType.DIMLET_MOB, id);
            initDimlet(key, RFToolsDim.MODID);
        } else {
            EntityEntry entry = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(id));
            if (entry != null) {
                DimletKey key = new DimletKey(DimletType.DIMLET_MOB, id);
                initDimlet(key, entry.getRegistryName().getNamespace());
            }
        }
    }

    private static void initFluidDimlet(Map.Entry<String, Fluid> me) {
        if (me.getValue().canBePlacedInWorld()) {
            String name = me.getKey();
            if (name != null && !name.isEmpty()) {
                Block block = me.getValue().getBlock();
                if (block != null) {
                    ResourceLocation nameForObject = Block.REGISTRY.getNameForObject(block);
                    if (nameForObject != null) {
                        String mod = nameForObject.getNamespace();
                        DimletKey key = new DimletKey(DimletType.DIMLET_LIQUID, block.getRegistryName() + "@0");
                        initDimlet(key, mod);
                    }
                }
            }
        }
    }

    private static void initDimlet(DimletKey key, String mod) {
        Settings settings = DimletRules.getSettings(key, mod);
        if (!settings.isBlacklisted()) {
            knownDimlets.put(key, settings);
        }
    }

    private static void initMaterialDimlet(Block block) {
        if (block instanceof BlockLiquid || block == Blocks.LIT_REDSTONE_ORE) {
            return;
        }

        Set<Filter.Feature> features = getBlockFeatures(block);

        ResourceLocation nameForObject = Block.REGISTRY.getNameForObject(block);
        String mod = nameForObject.getNamespace();

        if (Item.getItemFromBlock(block) != null) {      // Protection
            for (IBlockState state : block.getBlockState().getValidStates()) {
                List<IProperty<?>> propertyNames = new ArrayList<>(state.getPropertyKeys());
                propertyNames.sort(Comparator.comparing(IProperty::getName));

                ImmutableMap<IProperty<?>, Comparable<?>> properties = state.getProperties();
                Map<String, String> props = new HashMap<>();
                for (Map.Entry<IProperty<?>, Comparable<?>> entry : properties.entrySet()) {
                    props.put(entry.getKey().getName(), entry.getValue().toString());
                }
                DimletKey key = new DimletKey(DimletType.DIMLET_MATERIAL, block.getRegistryName() + "@" + block.getMetaFromState(state));
                Settings settings = DimletRules.getSettings(key, mod, features, props);
                if (!settings.isBlacklisted()) {
                    knownDimlets.put(key, settings);
                }
            }
        }
    }

    public static Set<Filter.Feature> getBlockFeatures(Block block) {
        Set<Filter.Feature> features = EnumSet.noneOf(Filter.Feature.class);

        ItemStack stack = null;
        try {
            stack = new ItemStack(block, 1, OreDictionary.WILDCARD_VALUE);
        } catch (Exception e) {
            Logging.getLogger().log(Level.ERROR, "Failed to create a dimlet for block " + block.getRegistryName() +
                    "! Please report to the correct mod!", e);
            return features;
        }
        int[] iDs = null;
        if (!stack.isEmpty() && stack.getItem() != null) {
            iDs = OreDictionary.getOreIDs(stack);
        }
        if (iDs != null && iDs.length > 0) {
            features.add(Filter.Feature.OREDICT);
        }
        if (block instanceof BlockFalling) {
            features.add(Filter.Feature.FALLING);
        }
        if (block.hasTileEntity(block.getDefaultState())) {
            features.add(Filter.Feature.TILEENTITY);
        }
        if (block instanceof IPlantable) {
            features.add(Filter.Feature.PLANTABLE);
        }
        if (!block.isFullBlock(block.getDefaultState())) {
            features.add(Filter.Feature.NOFULLBLOCK);
        }
        return features;
    }

    public static void dumpMobs() {
        Set<ResourceLocation> keys = ForgeRegistries.ENTITIES.getKeys();
        keys.stream().map(ResourceLocation::toString).forEach(KnownDimletConfiguration::dumpMob);
    }

    private static void dumpMob(String id) {
        EntityEntry entry = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(id));
        Class<? extends Entity> entityClass = entry == null ? null : entry.getEntityClass();
        if (entry != null) {
            DimletKey key = new DimletKey(DimletType.DIMLET_MOB, id);
            String mod = entry.getRegistryName().getNamespace();
            Settings settings = DimletRules.getSettings(key, mod);

            String resourceName = EntityTools.findEntityIdByClass(entityClass);
            String readableName = EntityTools.findEntityLocNameByClass(entityClass);
            Logging.log(resourceName + " (" + resourceName + ", " + readableName + "): " + settings.toString());
        }
    }

    private static boolean isValidMobClass(Class<? extends Entity> entityClass) {
        if (!EntityLivingBase.class.isAssignableFrom(entityClass)) {
            return false;
        }
        if (Modifier.isAbstract(entityClass.getModifiers())) {
            return false;
        }
        return true;
    }


    public static ItemStack getDimletStack(DimletKey key) {
        ItemStack stack = new ItemStack(ModItems.knownDimletItem, 1, key.getType().ordinal());
        NBTTagCompound compound = new NBTTagCompound();
        compound.setString("dkey", key.getId());
        stack.setTagCompound(compound);
        return stack;
    }

    public static ItemStack getDimletStack(DimletType type, String id) {
        return getDimletStack(new DimletKey(type, id));
    }

    public static DimletKey getDimletKey(ItemStack dimletStack) {
        DimletType type = DimletType.values()[dimletStack.getItemDamage()];
        NBTTagCompound tagCompound = dimletStack.getTagCompound();
        if (tagCompound != null && tagCompound.hasKey("dkey")) {
            return new DimletKey(type, tagCompound.getString("dkey"));
        } else {
            return new DimletKey(type, null);
        }
    }

    public static boolean isBlacklisted(DimletKey key) {
        return KnownDimletConfiguration.getSettings(key) == null;
    }

    public static boolean isCraftable(DimletKey key) {
        if (craftableDimlets.isEmpty()) {
            registerCraftables();
        }
        return craftableDimlets.contains(key);
    }

    private static void registerCraftables() {
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_EFFECT, "None"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_FEATURE, "None"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_STRUCTURE, "None"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_TERRAIN, "Void"));
        if (!GeneralConfiguration.voidOnly) {
            craftableDimlets.add(new DimletKey(DimletType.DIMLET_TERRAIN, "Flat"));
        }
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_CONTROLLER, DimletObjectMapping.DEFAULT_ID));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_CONTROLLER, "Single"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_MATERIAL, Blocks.STONE.getRegistryName() + "@0"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_LIQUID, Blocks.WATER.getRegistryName() + "@0"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_SKY, "normal"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_SKY, "normal.day"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_SKY, "normal.night"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_MOB, DimletObjectMapping.DEFAULT_ID));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_TIME, "Normal"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_WEATHER, DimletObjectMapping.DEFAULT_ID));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "0"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "1"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "2"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "3"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "4"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "5"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "6"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "7"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "8"));
        craftableDimlets.add(new DimletKey(DimletType.DIMLET_DIGIT, "9"));
    }

    public static Set<DimletKey> getCraftableDimlets() {
        if (craftableDimlets.isEmpty()) {
            registerCraftables();
        }
        return craftableDimlets;
    }

    public static boolean isSeedDimlet(DimletKey key) {
        return key.getType() == DimletType.DIMLET_SPECIAL && "Seed".equals(key.getId());
    }

    public static String getDisplayName(DimletKey key) {
        switch (key.getType()) {
            case DIMLET_BIOME:
                Biome biome = Biome.REGISTRY.getObject(new ResourceLocation(key.getId()));
                return biome == null ? "<invalid>" : biome.biomeName;
            case DIMLET_LIQUID:
                return DimensionInformation.getDisplayName(DimletObjectMapping.getFluid(key));
            case DIMLET_MATERIAL:
                return DimensionInformation.getDisplayName(DimletObjectMapping.getBlock(key));
            case DIMLET_MOB:
                if (DimletObjectMapping.DEFAULT_ID.equals(key.getId())) {
                    return key.getId();
                }
                EntityEntry entry = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(EntityTools.fixEntityId(key.getId())));
                Class<? extends Entity> entityClass = entry == null ? null : entry.getEntityClass();
                if (entityClass == null) {
                    return "<Unknown>";
                }
                return EntityTools.findEntityLocNameByClass(entityClass);
            case DIMLET_SKY:
                return StringUtils.capitalize(StringUtils.join(StringUtils.split(key.getId(), '.'), ' '));
            case DIMLET_STRUCTURE:
                return key.getId();
            case DIMLET_TERRAIN:
                return key.getId();
            case DIMLET_FEATURE:
                return key.getId();
            case DIMLET_TIME:
                return key.getId();
            case DIMLET_DIGIT:
                return key.getId();
            case DIMLET_EFFECT:
                return key.getId();
            case DIMLET_SPECIAL:
                return key.getId();
            case DIMLET_CONTROLLER:
                return key.getId();
            case DIMLET_WEATHER:
                return StringUtils.capitalize(StringUtils.join(StringUtils.split(key.getId(), '.'), ' '));
            case DIMLET_PATREON:
                return key.getId();
        }
        return "Unknown";
    }

    private static List<List<ItemStack>> randomPartLists = null;

    public static List<List<ItemStack>> getRandomPartLists() {
        if (randomPartLists == null) {
            randomPartLists = new ArrayList<>();
            randomPartLists.add(Lists.newArrayList(new ItemStack(ModItems.dimletBaseItem), new ItemStack(ModItems.dimletControlCircuitItem, 1, 0), new ItemStack(ModItems.dimletEnergyModuleItem)));
            randomPartLists.add(Lists.newArrayList(new ItemStack(ModItems.dimletControlCircuitItem, 1, 1), new ItemStack(ModItems.dimletEnergyModuleItem, 1, 0), new ItemStack(ModItems.dimletMemoryUnitItem, 1, 0)));
            randomPartLists.add(Lists.newArrayList(new ItemStack(ModItems.dimletControlCircuitItem, 1, 2)));
            ArrayList<ItemStack> list3 = Lists.newArrayList(new ItemStack(ModItems.dimletControlCircuitItem, 1, 3), new ItemStack(ModItems.dimletEnergyModuleItem, 1, 1), new ItemStack(ModItems.dimletMemoryUnitItem, 1, 1));
            for (DimletType type : DimletType.values()) {
                list3.add(new ItemStack(ModItems.dimletTypeControllerItem, 1, type.ordinal()));
            }
            randomPartLists.add(list3);
            randomPartLists.add(Lists.newArrayList(new ItemStack(ModItems.dimletControlCircuitItem, 1, 4)));
            randomPartLists.add(Lists.newArrayList(new ItemStack(ModItems.dimletControlCircuitItem, 1, 5), new ItemStack(ModItems.dimletEnergyModuleItem, 1, 2), new ItemStack(ModItems.dimletMemoryUnitItem, 1, 2)));
            randomPartLists.add(Lists.newArrayList(new ItemStack(ModItems.dimletControlCircuitItem, 1, 6)));
        }
        return randomPartLists;
    }

}
