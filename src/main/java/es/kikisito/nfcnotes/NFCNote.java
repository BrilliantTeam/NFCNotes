/*
 * Copyright (C) 2020  Kikisito (Kyllian)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package es.kikisito.nfcnotes;

import es.kikisito.nfcnotes.enums.NFCConfig;
import es.kikisito.nfcnotes.utils.Utils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NFCNote {

    // PDC key used to store the note value (primary storage, 1.14+)
    private static NamespacedKey NOTE_VALUE_KEY;

    public static void init(JavaPlugin plugin) {
        NOTE_VALUE_KEY = new NamespacedKey(plugin, "note_value");
    }

    private final ItemStack itemStack;
    private final String name;
    private final List<String> lore;
    private final Double value;

    public NFCNote(ItemStack itemStack) {
        this.itemStack = itemStack;
        ItemMeta im = itemStack.getItemMeta();
        this.name = im.getDisplayName();
        this.lore = im.getLore();
        this.value = readValue(im);
    }

    private static double readValue(ItemMeta im) {
        // PDC (new format)
        if (NOTE_VALUE_KEY != null && im.getPersistentDataContainer().has(NOTE_VALUE_KEY, PersistentDataType.DOUBLE)) {
            return im.getPersistentDataContainer().get(NOTE_VALUE_KEY, PersistentDataType.DOUBLE);
        }
        // Legacy: attribute modifier (pre-1.21 notes)
        return readValueFromAttribute(im);
    }

    private static double readValueFromAttribute(ItemMeta im) {
        try {
            Collection<AttributeModifier> mods = im.getAttributeModifiers(Attribute.GENERIC_LUCK);
            if (mods != null && !mods.isEmpty()) {
                return mods.iterator().next().getAmount();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public ItemStack getItemStack() { return this.itemStack; }

    public String getDisplayName() { return this.name; }

    public List<String> getLore() { return this.lore; }

    public Double getValue() { return this.value; }

    public static ItemStack[] createNFCNoteItem(String identifier, String name, List<String> lore, String material, String playername, DecimalFormat decimalFormat, Double money, Integer amount) {
        String formattedMoney = decimalFormat.format(money);

        Material mat = Material.valueOf(material.toUpperCase());
        int maxStackSize = mat.getMaxStackSize();

        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStackSize);
            ItemStack is = new ItemStack(mat, stackSize);
            ItemMeta im = is.getItemMeta();

            // Note display name
            im.setDisplayName(Utils.parseMessage(name).replace("{money}", formattedMoney).replace("{issuer}", playername));

            // Parse lore
            List<String> loreList = new ArrayList<>();
            for (String s : lore) {
                loreList.add(Utils.parseMessage(s).replace("{money}", formattedMoney).replace("{issuer}", playername));
            }
            im.setLore(loreList);

            // Store note value in PDC (replaces legacy attribute approach)
            if (NOTE_VALUE_KEY != null) {
                im.getPersistentDataContainer().set(NOTE_VALUE_KEY, PersistentDataType.DOUBLE, money);
            }
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            // Glint
            if (NFCConfig.NOTE_GLINT_ENABLED.getBoolean()) {
                Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(NFCConfig.NOTE_GLINT_ENCHANTMENT.getString().toLowerCase()));
                int enchantLevel = NFCConfig.NOTE_GLINT_ENCHANTMENT_LEVEL.getInt();
                im.addEnchant(enchant, enchantLevel, true);

                if (NFCConfig.NOTE_GLINT_HIDE_ENCHANTMENT_FLAG.getBoolean()) {
                    im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            }

            // Custom Model Data for texture packs
            im.setCustomModelData(NFCConfig.NOTE_CUSTOM_MODEL_DATA_INTEGER.getInt());

            is.setItemMeta(im);
            stacks.add(is);
            remaining -= stackSize;
        }

        return stacks.toArray(new ItemStack[0]);
    }

    public static boolean isNFCNote(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) return false;
        ItemMeta im = itemStack.getItemMeta();

        // Check PDC (new format — notes created with the updated plugin)
        if (NOTE_VALUE_KEY != null && im.getPersistentDataContainer().has(NOTE_VALUE_KEY, PersistentDataType.DOUBLE)) return true;

        // Legacy check for notes created before the 1.21 fix
        return isLegacyNote(im);
    }

    private static boolean isLegacyNote(ItemMeta im) {
        try {
            if (!im.hasAttributeModifiers()) return false;
            Collection<AttributeModifier> mods = im.getAttributeModifiers(Attribute.GENERIC_LUCK);
            if (mods == null || mods.isEmpty()) return false;
            AttributeModifier mod = mods.iterator().next();
            // Pre-1.21: getName() returned the original name string "noteValue"
            if (mod.getName().equalsIgnoreCase("noteValue")) return true;
            // 1.21+: NBT was converted to NamespacedKey format; the key part holds the original UUID
            return mod.getKey().getKey().equalsIgnoreCase(NFCConfig.NOTE_UUID.getString());
        } catch (Exception e) {
            return false;
        }
    }
}
