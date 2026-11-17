package com.example.m10practice;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 参加前の持ち物を、プレイヤーごとのYAMLファイルへ退避します。 */
public final class ReindeerInventoryBackup {
    private final JavaPlugin plugin;
    private final File directory;

    public ReindeerInventoryBackup(JavaPlugin plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "reindeer-inventory-backups");
    }

    private File fileFor(UUID uuid) {
        return new File(directory, uuid.toString() + ".yml");
    }

    public boolean hasBackup(UUID uuid) {
        return fileFor(uuid).isFile();
    }

    public boolean save(Player player) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            plugin.getLogger().severe("インベントリバックアップ先を作れません: " + directory);
            return false;
        }
        File destination = fileFor(player.getUniqueId());
        if (destination.exists()) {
            plugin.getLogger().warning("未復元データがあるため上書きしません: " + destination);
            return false;
        }
        ItemStack[] contents = player.getInventory().getContents();
        List<ItemStack> copied = new ArrayList<>(contents.length);
        for (ItemStack item : contents) {
            copied.add(item == null ? null : item.clone());
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("size", contents.length);
        yaml.set("contents", copied);
        yaml.set("selected-slot", player.getInventory().getHeldItemSlot());

        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory.toPath(), "inventory-", ".tmp");
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination.toPath());
            }
            return true;
        } catch (Exception exception) {
            plugin.getLogger().severe("持ち物保存失敗: " + exception.getMessage());
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
            return false;
        }
    }

    /** 復元に成功した場合だけバックアップを削除します。 */
    public boolean restore(Player player) {
        File file = fileFor(player.getUniqueId());
        if (!file.isFile()) {
            return false;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            List<?> raw = yaml.getList("contents");
            int count = player.getInventory().getContents().length;
            if (raw == null || yaml.getInt("size", -1) != count || raw.size() != count) {
                throw new IllegalStateException("バックアップのスロット数が一致しません");
            }
            ItemStack[] items = new ItemStack[count];
            for (int i = 0; i < count; i++) {
                Object value = raw.get(i);
                if (value != null && !(value instanceof ItemStack)) {
                    throw new IllegalStateException("不正なアイテム形式: " + i);
                }
                items[i] = value == null ? null : ((ItemStack) value).clone();
            }
            int selected = yaml.getInt("selected-slot", 0);
            if (selected < 0 || selected > 8) {
                throw new IllegalStateException("不正なホットバー位置");
            }

            player.getInventory().setContents(items);
            player.getInventory().setHeldItemSlot(selected);
            player.updateInventory();
            try {
                Files.delete(file.toPath());
            } catch (IOException deletionError) {
                plugin.getLogger().severe("復元は完了しましたがバックアップ削除に失敗: " + file);
                // バックアップを保護し、管理者が確認できるよう残す。
                player.sendMessage("§e持ち物は復元しましたが、バックアップ削除に失敗しました。管理者に確認してください。");
                return false;
            }
            player.sendMessage("§aゲーム開始前の全インベントリを復元しました。");
            return true;
        } catch (Exception exception) {
            plugin.getLogger().severe("持ち物復元失敗: " + player.getUniqueId() + " / " + exception.getMessage());
            player.sendMessage("§c持ち物の復元に失敗しました。バックアップは保持されています。管理者へ連絡してください。");
            return false;
        }
    }
}
