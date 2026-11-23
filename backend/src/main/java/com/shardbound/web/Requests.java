package com.shardbound.web;

import com.shardbound.battle.BattleAction;
import com.shardbound.progression.Attribute;
import com.shardbound.world.Facing;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** JSON request bodies, validated before they reach the engine. */
public final class Requests {

    public static final int MAX_USERNAME = 30;
    public static final int MAX_ID = 64;

    private Requests() {
    }

    public record ProfileRequest(@NotBlank @Size(max = MAX_USERNAME) String username) { }

    public record NewGameRequest(@Positive int playerId, boolean demoMode) { }

    public record ChooseRequest(@Positive int choiceId) { }

    /** The player's current pixel position on the area map and facing (§4.11). */
    public record ExitRequest(@NotBlank @Size(max = MAX_ID) String exitId, @PositiveOrZero int x,
                              @PositiveOrZero int y, @NotNull Facing facing) { }

    public record InteractRequest(@NotBlank @Size(max = MAX_ID) String targetId, @PositiveOrZero int x,
                                  @PositiveOrZero int y, @NotNull Facing facing) { }

    /** A creep came within its aggro radius: its object name and the player's position (V2_SPEC §4.2). */
    public record EncounterRequest(@NotBlank @Size(max = MAX_ID) String creepId, @PositiveOrZero int x,
                                   @PositiveOrZero int y, @NotNull Facing facing) { }

    /** A battle round (V2_SPEC §7): {@code slot} (0–3) for SKILL, {@code itemId} for ITEM. */
    public record BattleRequest(@NotNull BattleAction.Kind kind, @Min(0) @Max(3) int slot,
                                @Nullable @Size(max = MAX_ID) String itemId) { }

    public record ItemRequest(@NotBlank @Size(max = MAX_ID) String itemId) { }

    /** The player stepped within a tile of their Lost Shards: their position (V2_SPEC §4.4, P-62). */
    public record RecoverRequest(@PositiveOrZero int x, @PositiveOrZero int y, @NotNull Facing facing) { }

    /** Points to add per attribute at a Lantern (P-61); attributes not named stay as they are. */
    public record LevelUpRequest(@NotNull Map<Attribute, @NotNull @Min(0) @Max(98) Integer> raises) { }

    /** The four skill slots, each a known skill id or null (P-61). */
    public record LoadoutRequest(@NotNull @Size(min = 4, max = 4) List<@Nullable @Size(max = MAX_ID) String> slots) { }
}
