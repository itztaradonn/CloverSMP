package net.cloversmp.cloverblessing.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Marks an inventory as one of this plugin's menus and carries what each clicked slot means.
 * Identifying menus by holder (not title) means renamed titles or other plugins' chests can't confuse it.
 */
public final class MenuHolder implements InventoryHolder {

    public enum Kind { CLOVER, HERBARIUM, LEAF_PICKER, FIELD_GUIDE, ADMIN_ALL, PRESS, GRAFT_LIST, GRAFT_ONE;

        /** Kinds handled by the Pressing Table menu, which lets players place items in its input slots. */
        public boolean isTable() {
            return this == PRESS || this == GRAFT_LIST || this == GRAFT_ONE;
        }
    }

    private final Kind kind;
    /** For LEAF_PICKER: the blessing being equipped. */
    private final UUID subject;
    /** Inventory slot → blessing id (herbarium entries). */
    private final Map<Integer, UUID> blessingSlots = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(Kind kind, UUID subject) {
        this.kind = kind;
        this.subject = subject;
    }

    public Kind kind() { return kind; }
    public UUID subject() { return subject; }
    public Map<Integer, UUID> blessingSlots() { return blessingSlots; }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
