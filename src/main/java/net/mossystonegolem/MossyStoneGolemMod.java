package net.mossystonegolem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.mossystonegolem.entity.MossyStoneGolemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MossyStoneGolemMod implements ModInitializer {
    public static final String MOD_ID = "mossystonegolem";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final EntityType<MossyStoneGolemEntity> MOSSY_STONE_GOLEM = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "mossy_stone_golem"),
            EntityType.Builder.create(MossyStoneGolemEntity::new, SpawnGroup.CREATURE)
                    .dimensions(1.95f, 2.2f)
                    .eyeHeight(1.8f)
                    .maxTrackingRange(10)
                    .build()
    );

    public static final Item MOSSY_STONE_GOLEM_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "mossy_stone_golem_spawn_egg"),
            new SpawnEggItem(MOSSY_STONE_GOLEM, 0x4e5848, 0x30a891, new Item.Settings())
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Mossy Stone Golem Mod for Minecraft 1.21.1");
        FabricDefaultAttributeRegistry.register(MOSSY_STONE_GOLEM, MossyStoneGolemEntity.createAttributes());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(MOSSY_STONE_GOLEM_SPAWN_EGG);
        });
    }
}
