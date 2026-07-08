package de.dustplanet.silkspawners.compat.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.base.CaseFormat;

/**
 * Paper/Bukkit API-only implementation of NMSProvider.
 * No version-specific NMS code required!
 */
public class PaperNMSProvider implements NMSProvider {
    private static final NamespacedKey SILK_SPAWNERS_KEY = new NamespacedKey("silkspawners", "entity");
    private final Collection<Material> spawnEggs = Arrays.stream(Material.values())
            .filter(material -> material.name().endsWith("_SPAWN_EGG"))
            .collect(Collectors.toList());

    @Override
    public void spawnEntity(final World world, final String entityID, final double x, final double y, final double z,
            final Player player) {
        // Use Paper's spawning API
        final EntityType entityType = getEntityTypeFromID(entityID);
        if (entityType == null) {
            Bukkit.getLogger().warning("[SilkSpawners] Failed to spawn: Unknown entity type '" + entityID + "'");
            return;
        }

        world.spawnEntity(new org.bukkit.Location(world, x, y, z), entityType);
    }

    @Override
    public List<String> rawEntityMap() {
        final List<String> entities = new ArrayList<>();
        for (final EntityType type : Registry.ENTITY_TYPE) {
            if (type.isSpawnable() && type.isAlive()) {
                entities.add(type.getKey().getKey());
            }
        }
        return entities;
    }

    @Override
    public String getMobNameOfSpawner(final BlockState blockState) {
        if (!(blockState instanceof CreatureSpawner)) {
            return "";
        }
        
        final CreatureSpawner spawner = (CreatureSpawner) blockState;
        final EntityType spawnedType = spawner.getSpawnedType();
        
        if (spawnedType == null) {
            return "";
        }
        
        return spawnedType.getKey().getKey();
    }

    @Override
    public boolean setMobNameOfSpawner(final BlockState blockState, final String mobID) {
        if (!(blockState instanceof CreatureSpawner)) {
            return false;
        }

        final EntityType entityType = getEntityTypeFromID(mobID);
        if (entityType == null) {
            return false;
        }

        final CreatureSpawner spawner = (CreatureSpawner) blockState;
        spawner.setSpawnedType(entityType);
        spawner.update(true, false);
        return true;
    }

    @Override
    public void setSpawnersUnstackable() {
        // In modern Minecraft, this is handled by the Material's max stack size
        // which cannot be changed at runtime without NMS or mixins
        // This is now a no-op as spawners are properly handled by the game
    }

    @Override
    public ItemStack setNBTEntityID(final ItemStack item, final String entity) {
        if (item == null || item.getType() != Material.SPAWNER) {
            Bukkit.getLogger().warning("[SilkSpawners] Skipping invalid spawner to set entity data on.");
            return null;
        }

        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Use PersistentDataContainer for our custom data
        final PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(SILK_SPAWNERS_KEY, PersistentDataType.STRING, entity);

        // Also set the BlockStateMeta for the spawner
        if (meta instanceof BlockStateMeta) {
            final BlockStateMeta bsm = (BlockStateMeta) meta;
            final BlockState state = bsm.getBlockState();
            
            if (state instanceof CreatureSpawner) {
                final CreatureSpawner spawner = (CreatureSpawner) state;
                final EntityType entityType = getEntityTypeFromID(entity);
                
                if (entityType != null) {
                    spawner.setSpawnedType(entityType);
                    bsm.setBlockState(spawner);
                }
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    @Override
    @Nullable
    public String getSilkSpawnersNBTEntityID(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        final ItemMeta meta = item.getItemMeta();
        final PersistentDataContainer container = meta.getPersistentDataContainer();
        
        if (container.has(SILK_SPAWNERS_KEY, PersistentDataType.STRING)) {
            return container.get(SILK_SPAWNERS_KEY, PersistentDataType.STRING);
        }

        return null;
    }

    @Override
    @Nullable
    public String getVanillaNBTEntityID(final ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getType() != Material.SPAWNER) {
            return null;
        }

        final ItemMeta meta = item.getItemMeta();
        
        // Check BlockStateMeta for spawner type
        if (meta instanceof BlockStateMeta) {
            final BlockStateMeta bsm = (BlockStateMeta) meta;
            final BlockState state = bsm.getBlockState();
            
            if (state instanceof CreatureSpawner) {
                final CreatureSpawner spawner = (CreatureSpawner) state;
                final EntityType type = spawner.getSpawnedType();
                
                if (type != null) {
                    return type.getKey().getKey();
                }
            }
        }

        return null;
    }

    @Override
    public Block getSpawnerFacing(final Player player, final int distance) {
        final Block block = player.getTargetBlockExact(distance);
        if (block == null || block.getType() != Material.SPAWNER) {
            return null;
        }
        return block;
    }

    @Override
    public ItemStack newEggItem(final String entityID, final int amount, final String displayName) {
        // Modern Minecraft has specific spawn egg materials
        final String eggMaterialName = entityID.toUpperCase(Locale.ENGLISH) + "_SPAWN_EGG";
        Material spawnEgg = Material.matchMaterial(eggMaterialName);
        
        if (spawnEgg == null) {
            Bukkit.getLogger().warning("[SilkSpawners] Could not find spawn egg for: " + entityID);
            return null;
        }

        final ItemStack item = new ItemStack(spawnEgg, amount);
        
        if (displayName != null) {
            final ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                item.setItemMeta(meta);
            }
        }

        // Store our custom entity ID in persistent data
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(SILK_SPAWNERS_KEY, PersistentDataType.STRING, entityID);
            item.setItemMeta(meta);
        }

        return item;
    }

    @Override
    public String getVanillaEggNBTEntityID(final ItemStack item) {
        if (item == null || !item.getType().name().endsWith("_SPAWN_EGG")) {
            return null;
        }

        // Extract entity ID from the material name
        final String materialName = item.getType().name();
        return materialName.substring(0, materialName.length() - "_SPAWN_EGG".length()).toLowerCase(Locale.ENGLISH);
    }

    @Override
    public void displayBossBar(final String title, final String colorName, final String styleName, final Player player,
            final Plugin plugin, final int period) {
        final BarColor color = BarColor.valueOf(colorName.toUpperCase(Locale.ENGLISH));
        final BarStyle style = BarStyle.valueOf(styleName.toUpperCase(Locale.ENGLISH));
        final BossBar bar = Bukkit.createBossBar(title, color, style);
        
        bar.addPlayer(player);
        bar.setVisible(true);
        
        final double interval = 1.0 / (period * 20L);
        new BukkitRunnable() {
            @Override
            public void run() {
                final double progress = bar.getProgress();
                final double newProgress = progress - interval;
                
                if (progress <= 0.0 || newProgress <= 0.0) {
                    bar.setVisible(false);
                    bar.removeAll();
                    this.cancel();
                } else {
                    bar.setProgress(newProgress);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0, 1L);
    }

    @Override
    public Player getPlayer(final String playerUUIDOrName) {
        try {
            final UUID playerUUID = UUID.fromString(playerUUIDOrName);
            return Bukkit.getPlayer(playerUUID);
        } catch (final IllegalArgumentException e) {
            // Not a UUID, try by name
            for (final Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().equalsIgnoreCase(playerUUIDOrName)) {
                    return onlinePlayer;
                }
            }
        }
        return null;
    }

    @Override
    public ItemStack getItemInHand(final Player player) {
        return player.getInventory().getItemInMainHand();
    }

    @Override
    public void reduceEggs(final Player player) {
        final PlayerInventory inv = player.getInventory();
        final ItemStack mainHand = inv.getItemInMainHand();
        final ItemStack offHand = inv.getItemInOffHand();
        
        ItemStack eggs;
        if (getSpawnEggMaterials().contains(mainHand.getType())) {
            eggs = mainHand;
            if (eggs.getAmount() == 1) {
                inv.setItemInMainHand(null);
            } else {
                eggs.setAmount(eggs.getAmount() - 1);
                inv.setItemInMainHand(eggs);
            }
        } else {
            eggs = offHand;
            if (eggs.getAmount() == 1) {
                inv.setItemInOffHand(null);
            } else {
                eggs.setAmount(eggs.getAmount() - 1);
                inv.setItemInOffHand(eggs);
            }
        }
    }

    @Override
    public ItemStack getSpawnerItemInHand(final Player player) {
        final PlayerInventory inv = player.getInventory();
        final ItemStack mainHand = inv.getItemInMainHand();
        final ItemStack offHand = inv.getItemInOffHand();
        
        final boolean mainIsSpawnerOrEgg = getSpawnEggMaterials().contains(mainHand.getType()) 
                || mainHand.getType() == Material.SPAWNER;
        final boolean offIsSpawnerOrEgg = getSpawnEggMaterials().contains(offHand.getType()) 
                || offHand.getType() == Material.SPAWNER;
        
        if (mainIsSpawnerOrEgg && offIsSpawnerOrEgg) {
            return null; // not determinable
        } else if (mainIsSpawnerOrEgg) {
            return mainHand;
        } else if (offIsSpawnerOrEgg) {
            return offHand;
        }
        
        return null;
    }

    @Override
    public void setSpawnerItemInHand(final Player player, final ItemStack newItem) {
        final PlayerInventory inv = player.getInventory();
        final ItemStack mainHand = inv.getItemInMainHand();
        final ItemStack offHand = inv.getItemInOffHand();
        
        final boolean mainIsSpawnerOrEgg = getSpawnEggMaterials().contains(mainHand.getType()) 
                || mainHand.getType() == Material.SPAWNER;
        final boolean offIsSpawnerOrEgg = getSpawnEggMaterials().contains(offHand.getType()) 
                || offHand.getType() == Material.SPAWNER;
        
        if (mainIsSpawnerOrEgg && offIsSpawnerOrEgg) {
            return; // not determinable
        } else if (mainIsSpawnerOrEgg) {
            inv.setItemInMainHand(newItem);
        } else if (offIsSpawnerOrEgg) {
            inv.setItemInOffHand(newItem);
        }
    }

    @Override
    public Collection<Material> getSpawnEggMaterials() {
        return spawnEggs;
    }

    @Override
    public Player loadPlayer(final OfflinePlayer offline) {
        if (!offline.hasPlayedBefore()) {
            return null;
        }
        
        // Note: This is a simplified version
        // Full player loading without being online requires NMS
        // For most use cases, checking if they're online is sufficient
        return offline.getPlayer();
    }

    @Override
    public boolean isWindCharge(final Entity entity) {
        return entity instanceof AbstractWindCharge;
    }

    /**
     * Helper method to convert entity ID string to EntityType
     */
    @Nullable
    private EntityType getEntityTypeFromID(final String entityID) {
        if (entityID == null) {
            return null;
        }

        // Normalize the entity ID
        final String normalizedID = caseFormatOf(entityID.replace(" ", "_"))
                .to(CaseFormat.LOWER_UNDERSCORE, entityID.replace(" ", "_"))
                .toLowerCase(Locale.ENGLISH);

        // Try to find the entity type
        final NamespacedKey key = NamespacedKey.minecraft(normalizedID);
        
        for (final EntityType type : Registry.ENTITY_TYPE) {
            if (type.getKey().equals(key)) {
                return type;
            }
        }

        // Fallback: try matching by name
        try {
            return EntityType.valueOf(normalizedID.toUpperCase(Locale.ENGLISH));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
