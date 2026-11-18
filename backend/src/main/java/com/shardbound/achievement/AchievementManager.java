package com.shardbound.achievement;

import com.shardbound.engine.GameState;
import com.shardbound.engine.Toast;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.event.GameListener;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.item.Weapon;
import com.shardbound.model.Tier;
import com.shardbound.persistence.MetaRepository;
import com.shardbound.story.StoryGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Observer that unlocks gameplay achievements (spec §6.8).
 *
 * <p>One manager per session (D23), created with that player's already
 * unlocked ids, so no locking beyond the session lock is needed. The rules
 * themselves are stateless and live in one {@code static final EnumMap}
 * shared by every manager: on each event only the rules for that event type
 * are checked, O(rules for that type) instead of O(all rules).
 *
 * <p>{@code ALL_ENDINGS} has no Java rule: a database trigger grants it.
 */
public class AchievementManager implements GameListener {

    public static final String DB_OWNED = "ALL_ENDINGS";
    public static final int SPEEDRUN_SECONDS = 360;

    private static final EnumMap<EventType, List<AchievementRule>> RULES = buildRules();

    private final int playerId;
    private final Set<String> unlocked;
    private final Map<String, Achievement> catalog;
    private final MetaRepository meta;        // null in offline (JSON-only) console mode

    public AchievementManager(int playerId, Set<String> alreadyUnlocked, Map<String, Achievement> catalog,
                              MetaRepository meta) {
        this.playerId = playerId;
        this.unlocked = new HashSet<>(alreadyUnlocked);
        this.catalog = catalog;
        this.meta = meta;
    }

    /**
     * Checks only the rules registered for this event's type. A new unlock is
     * persisted at once (INSERT … ON CONFLICT DO NOTHING) and shown as a toast.
     * Time O(r) for r rules of this type (plus one DB insert per new unlock).
     */
    @Override
    public void onEvent(GameEvent event, GameState state) {
        List<AchievementRule> rules = RULES.getOrDefault(event.type(), List.of());
        for (AchievementRule rule : rules) {
            String id = rule.achievementId();
            if (!unlocked.contains(id) && rule.matches(event, state)) {
                unlock(id, state);
            }
        }
    }

    public Set<String> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    private void unlock(String id, GameState state) {
        unlocked.add(id);
        if (meta != null) {
            meta.unlockAchievement(playerId, id);
        }
        Achievement achievement = catalog.get(id);
        String title = achievement == null ? id : achievement.title();
        state.addToast(new Toast("ACHIEVEMENT", "Achievement unlocked: " + title));
    }

    // =====================================================================
    // The rules (Strategy objects), built once
    // =====================================================================

    private static EnumMap<EventType, List<AchievementRule>> buildRules() {
        List<AchievementRule> all = List.of(
                AchievementRule.of("FIRST_LIGHT", EventType.ITEM_GAINED,
                        (e, s) -> "heartshard".equals(e.text("itemId"))),
                AchievementRule.of("FIRST_BLOOD", EventType.COMBAT_WON,
                        (e, s) -> true),
                AchievementRule.of("SHORT_TALE", EventType.ENDING_REACHED,
                        (e, s) -> StoryGraph.QUICK_ENDING_NODE.equals(e.text("endingId"))),
                AchievementRule.of("SILVER_TONGUE", EventType.LOCKED_CHOICE_PASSED,
                        (e, s) -> s.flag("_locked_passed") >= 3),
                AchievementRule.of("ARMORY", EventType.ITEM_GAINED,
                        (e, s) -> s.inventory().weaponCount() >= 5),
                AchievementRule.of("S_RANK", EventType.ITEM_EQUIPPED,
                        (e, s) -> isSRankOrBetter(s.inventory().equippedWeapon())),
                AchievementRule.of("CRYSTAL_EDGE", EventType.ITEM_GAINED,
                        (e, s) -> "aetherion".equals(e.text("itemId"))),
                AchievementRule.of("SPEEDRUNNER", EventType.ENDING_REACHED,
                        (e, s) -> e.number("seconds", Integer.MAX_VALUE) < SPEEDRUN_SECONDS));

        EnumMap<EventType, List<AchievementRule>> byType = new EnumMap<>(EventType.class);
        for (AchievementRule rule : all) {
            byType.computeIfAbsent(rule.trigger(), t -> new ArrayList<>()).add(rule);
        }
        return byType;
    }

    private static boolean isSRankOrBetter(Weapon weapon) {
        return weapon != null && weapon.tier().atLeast(Tier.S);
    }

    /** Ids that have a Java rule. O(rules). */
    public static Set<String> ruleIds() {
        Set<String> ids = new HashSet<>();
        for (List<AchievementRule> rules : RULES.values()) {
            for (AchievementRule rule : rules) {
                ids.add(rule.achievementId());
            }
        }
        return ids;
    }

    /**
     * Every achievement in content except ALL_ENDINGS must have a Java rule,
     * and every rule must have content (spec §6.8). O(a).
     */
    public static void checkRulesMatch(Set<String> contentIds) {
        Set<String> expected = new HashSet<>(contentIds);
        expected.remove(DB_OWNED);
        Set<String> rules = ruleIds();
        if (!expected.equals(rules)) {
            throw new ContentValidationException("Achievements in content " + expected
                    + " do not match the Java rules " + rules);
        }
    }
}
