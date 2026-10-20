package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;
import net.cloversmp.cloverblessing.model.BlessingTag;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Set;

/** +15 % speed on natural blocks (grass, dirt, leaves, flowers); never in the Nether. */
public class FieldwalkerHandler implements BlessingHandler {

    private static final Set<Material> PLANTS = Set.of(Material.SHORT_GRASS, Material.TALL_GRASS,
            Material.FERN, Material.LARGE_FERN, Material.MOSS_CARPET);

    @Override
    public BlessingCore createCore() {
        return new BlessingCore("fieldwalker", "Fieldwalker", BlessingCategory.MOBILITY, Set.of(BlessingTag.SPEED),
                "+15% speed on natural blocks: grass, dirt, leaves and flowers.",
                "No bonus on stone or wood, or in the Nether; fight on built ground.",
                0, true, false, List.of("grass", "flower"));
    }

    @Override
    public boolean onSecond(BlessingContext ctx, StatSink stats) {
        if (ctx.player().getWorld().getEnvironment() == World.Environment.NETHER) return false;
        Block feet = ctx.player().getLocation().getBlock();
        if (isNatural(feet) || isNatural(feet.getRelative(0, -1, 0))) {
            stats.speed(0.15 * ctx.strength(BlessingTag.SPEED));
        }
        return false;
    }

    private static boolean isNatural(Block b) {
        Material m = b.getType();
        return Tag.DIRT.isTagged(m) || Tag.LEAVES.isTagged(m) || Tag.FLOWERS.isTagged(m) || PLANTS.contains(m);
    }
}
