package net.cloversmp.cloverblessing.blessing.handler;

import net.cloversmp.cloverblessing.blessing.BlessingEngine;
import net.cloversmp.cloverblessing.model.BlessingCategory;
import net.cloversmp.cloverblessing.model.BlessingCore;

import java.util.List;
import java.util.Set;

/**
 * Copies the blessing in the opposite leaf at 50 % strength. Has no behaviour of its own: the
 * {@link BlessingEngine} runs the opposite core through this slot.
 */
public class EchoLeafHandler implements BlessingHandler {

    @Override
    public BlessingCore createCore() {
        return new BlessingCore(BlessingEngine.ECHO_LEAF, "Echo Leaf", BlessingCategory.EXPERIMENTAL, Set.of(),
                "Copies the blessing in the opposite leaf at 50% strength.",
                "A dead slot if the opposite leaf is empty or not echoable; only one Echo can be equipped.",
                0, false, true, List.of("echo shard", "sculk"));
    }
}
