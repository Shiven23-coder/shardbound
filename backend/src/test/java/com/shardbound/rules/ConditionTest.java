package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.engine.TestStates;
import com.shardbound.item.KeyItem;
import com.shardbound.model.Faction;
import com.shardbound.quest.QuestStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Conditions (spec §6.2) and ConditionGroup AND/OR semantics (D4). */
class ConditionTest {

    @Test
    void opComparesAndParses() {
        assertTrue(Op.fromSymbol(">=").compare(5, 5));
        assertFalse(Op.fromSymbol(">").compare(5, 5));
        assertTrue(Op.fromSymbol("!=").compare(1, 2));
        assertThrows(IllegalArgumentException.class, () -> Op.fromSymbol("=>"));
    }

    @Test
    void missingFlagCountsAsZero() {
        GameState s = TestStates.fresh();
        assertTrue(new FlagCondition("met_wardens", Op.EQ, 0).test(s));
        assertFalse(new FlagCondition("met_wardens", Op.EQ, 1).test(s));
        s.setFlag("met_wardens", 1);
        assertTrue(new FlagCondition("met_wardens", Op.EQ, 1).test(s));
    }

    @Test
    void reputationAndAffinity() {
        GameState s = TestStates.fresh();
        RepCondition rep = new RepCondition(Faction.ELDMERE, Op.GE, 20);
        AffinityCondition aff = new AffinityCondition("corvin", "Corvin", Op.GE, 10);
        assertFalse(rep.test(s));
        assertFalse(aff.test(s));
        s.addReputation(Faction.ELDMERE, 25);
        s.addAffinity("corvin", 10);
        assertTrue(rep.test(s));
        assertTrue(aff.test(s));
        assertEquals("Requires Eldmere reputation ≥ 20", rep.describe());
    }

    @Test
    void reputationIsClamped() {
        GameState s = TestStates.fresh();
        assertEquals(100, s.addReputation(Faction.ASHEN, 150));
        assertEquals(100, s.reputation(Faction.ASHEN));
        assertEquals(-200, s.addReputation(Faction.ASHEN, -500));
        assertEquals(-100, s.reputation(Faction.ASHEN));
    }

    @Test
    void itemQuestAndStat() {
        GameState s = TestStates.fresh();
        ItemCondition hasShard = new ItemCondition("heartshard", "Heartshard", Op.GE, 1);
        assertFalse(hasShard.test(s));
        s.inventory().add(new KeyItem("heartshard", "Heartshard", "", 0), 1);
        assertTrue(hasShard.test(s));
        assertEquals("Requires Heartshard", hasShard.describe());

        QuestCondition done = new QuestCondition("q", "Quest", Op.EQ, QuestStatus.COMPLETED);
        assertFalse(done.test(s));
        s.setQuestStatus("q", QuestStatus.COMPLETED);
        assertTrue(done.test(s));

        assertTrue(new StatCondition("level", Op.EQ, 1).test(s));
        assertTrue(new StatCondition("gold", Op.GE, 15).test(s));
    }

    @Test
    void emptyGroupAlwaysPasses() {
        assertTrue(ConditionGroup.empty().test(TestStates.fresh()));
        assertEquals("", ConditionGroup.empty().describe());
    }

    @Test
    void sameGroupIsAndDifferentGroupsAreOr() {               // D4
        ConditionGroup crownAndEldmere = ConditionGroup.builder()
                .add(0, new RepCondition(Faction.CROWN, Op.GE, 30))
                .add(0, new RepCondition(Faction.ELDMERE, Op.GE, 30))
                .build();
        ConditionGroup ashenOrFlag = ConditionGroup.builder()
                .add(0, new RepCondition(Faction.ASHEN, Op.GE, 20))
                .add(1, new FlagCondition("gave_shard_to_cult", Op.EQ, 1))
                .build();
        GameState s = TestStates.fresh();
        s.addReputation(Faction.CROWN, 30);
        assertFalse(crownAndEldmere.test(s), "AND: one of two is not enough");
        s.addReputation(Faction.ELDMERE, 30);
        assertTrue(crownAndEldmere.test(s));

        assertFalse(ashenOrFlag.test(s));
        s.setFlag("gave_shard_to_cult", 1);
        assertTrue(ashenOrFlag.test(s), "OR: the flag group alone is enough");
        assertEquals("Requires Ashen Circle reputation ≥ 20, or flag gave_shard_to_cult = 1", ashenOrFlag.describe());
    }

    @Test
    void groupsAreDescribedInGrpOrderEvenWithGaps() {
        ConditionGroup g = ConditionGroup.builder()
                .add(5, new FlagCondition("b", Op.EQ, 1))
                .add(2, new FlagCondition("a", Op.EQ, 1))
                .build();
        assertEquals("Requires flag a = 1, or flag b = 1", g.describe());
        assertEquals(2, g.groupCount());
    }

    @Test
    void detectsRepOrAffinityForSilverTongue() {              // D15
        assertTrue(ConditionGroup.builder().add(0, new AffinityCondition("x", "X", Op.GE, 1)).build()
                .containsRepOrAffinity());
        assertFalse(ConditionGroup.builder().add(0, new FlagCondition("x", Op.GE, 1)).build()
                .containsRepOrAffinity());
    }
}
