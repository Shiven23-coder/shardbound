package com.shardbound.save;

import com.shardbound.exception.SaveIncompatibleException;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Applies every upgrade between a stored save version and the current one,
 * in version order. A save from a newer server, or a gap in the chain, is
 * rejected rather than guessed at.
 */
public class SaveUpgrader {

    private final int currentVersion;
    private final SortedMap<Integer, SaveUpgrade> upgrades = new TreeMap<>();

    public SaveUpgrader(int currentVersion, List<SaveUpgrade> upgrades) {
        this.currentVersion = currentVersion;
        for (SaveUpgrade upgrade : upgrades) {
            SaveUpgrade previous = this.upgrades.put(upgrade.fromVersion(), upgrade);
            if (previous != null) {
                throw new IllegalArgumentException("Two save upgrades from version " + upgrade.fromVersion());
            }
        }
    }

    /** The upgrader this server uses. */
    public static SaveUpgrader standard() {
        return new SaveUpgrader(SaveFormat.CURRENT_VERSION, List.of(new WorldPositionUpgrade()));
    }

    public int currentVersion() {
        return currentVersion;
    }

    /** Upgrades {@code document} from {@code storedVersion} to the current version. */
    public ObjectNode upgrade(ObjectNode document, int storedVersion) {
        if (storedVersion > currentVersion) {
            throw new SaveIncompatibleException("This save was made by a newer version of the game (format "
                    + storedVersion + "; this server reads up to " + currentVersion + ").");
        }
        ObjectNode result = document;
        for (int version = storedVersion; version < currentVersion; version++) {
            SaveUpgrade upgrade = upgrades.get(version);
            if (upgrade == null) {
                throw new SaveIncompatibleException("No upgrade path for save format " + version + ".");
            }
            result = upgrade.apply(result);
        }
        return result;
    }
}
