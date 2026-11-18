package com.shardbound.tools;

import com.shardbound.api.dto.ChoiceView;
import com.shardbound.api.dto.CombatView;
import com.shardbound.api.dto.GameView;
import com.shardbound.api.dto.ToastView;
import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.config.AppConfig;
import com.shardbound.config.Database;
import com.shardbound.content.ContentAssembler;
import com.shardbound.content.GameContent;
import com.shardbound.content.JsonContentReader;
import com.shardbound.content.RawContent;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.exception.GameException;
import com.shardbound.persistence.ContentRepository;
import com.shardbound.persistence.MetaRepository;
import com.shardbound.persistence.PlayerRepository;
import com.shardbound.persistence.SaveRepository;
import com.shardbound.story.Choice;
import com.shardbound.story.StoryGraph;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/**
 * Plays Shardbound in the terminal (Phase 2) using the same GameEngine as
 * the web API — proof that every rule lives in Java.
 *
 * <pre>
 * .\mvnw.cmd exec:java -Dexec.mainClass=com.shardbound.tools.ConsoleRunner                       (content from DB)
 * .\mvnw.cmd exec:java -Dexec.mainClass=com.shardbound.tools.ConsoleRunner -Dexec.args="--json"  (offline, no DB)
 * .\mvnw.cmd exec:java -Dexec.mainClass=com.shardbound.tools.ConsoleRunner -Dexec.args="--json --auto-quick"
 * </pre>
 * {@code --auto-quick} plays the quick route with REAL conditions and a fixed
 * seed and exits 0 on success (spec §8.4 integration check). {@code --demo}
 * turns on Demo Mode.
 */
public class ConsoleRunner {

    /** The quick ending route (spec §7.1). */
    public static final List<String> QUICK_ROUTE = List.of(
            "p01", "p02", "p03", "p04m", "p05m", "p06m", "p07m", "p08m", "p09m", "p10m", "END_QUICK");
    public static final long AUTO_SEED = 42L;

    public static void main(String[] args) {
        boolean json = has(args, "--json");
        boolean auto = has(args, "--auto-quick");
        boolean demo = has(args, "--demo");

        GameEngine engine = json ? offlineEngine(defaultContentDir()) : databaseEngine();
        if (auto) {
            // With a database the ending is recorded in player_ending, which needs a REAL
            // player row (FK); offline nothing is persisted, so id 0 is fine.
            int botPlayer = json ? 0 : profileFor("auto-quick");
            boolean ok = playQuickRoute(engine, System.out, botPlayer);
            System.exit(ok ? 0 : 1);
        }
        int playerId = json ? 0 : askProfile();
        GameState state = engine.newGame(playerId, demo);
        new ConsoleRunner().play(engine, state);
        engine.shutdown();
    }

    // ---------------------------------------------------------------------
    // engine setup
    // ---------------------------------------------------------------------

    /** Content straight from content/*.json; no saves, nothing persisted. */
    public static GameEngine offlineEngine(File contentDir) {
        RawContent raw = new JsonContentReader(contentDir).read();
        GameContent content = new ContentAssembler().assemble(raw);
        content.prepareForPlay();
        return new GameEngine(content, null, null);
    }

    private static GameEngine databaseEngine() {
        AppConfig config = AppConfig.load();
        Database db = Database.from(config);
        GameContent content = new ContentAssembler().assemble(new ContentRepository(db).loadAll());
        content.prepareForPlay();
        return new GameEngine(content, new SaveRepository(db, config.simulateSaveCrash()), new MetaRepository(db));
    }

    private static int askProfile() {
        System.out.print("Username: ");
        String name = new Scanner(System.in).nextLine().trim();
        return profileFor(name.isEmpty() ? "console" : name);
    }

    /** Creates (or finds) the player row for a username in the database. */
    private static int profileFor(String username) {
        Database db = Database.from(AppConfig.load());
        return new PlayerRepository(db).findOrCreate(username);
    }

    private static File defaultContentDir() {
        File sibling = new File("../content");
        return sibling.isDirectory() ? sibling : new File("content");
    }

    // ---------------------------------------------------------------------
    // interactive play
    // ---------------------------------------------------------------------

    private void play(GameEngine engine, GameState state) {
        Scanner in = new Scanner(System.in);
        GameView view = engine.view(state);
        while (true) {
            print(view, System.out);
            if ("ENDED".equals(view.mode())) {
                return;
            }
            System.out.print("> ");
            if (!in.hasNextLine()) {
                return;
            }
            String line = in.nextLine().trim();
            if (line.equalsIgnoreCase("q")) {
                return;
            }
            try {
                view = "COMBAT".equals(view.mode())
                        ? engine.combatAction(state, parseCombat(line, view.combat()))
                        : engine.choose(state, pickChoice(line, view.choices()));
            } catch (GameException | IllegalArgumentException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    /** "2" → the id of the 2nd listed choice. */
    private static int pickChoice(String line, List<ChoiceView> choices) {
        int number = Integer.parseInt(line);
        if (number < 1 || number > choices.size()) {
            throw new IllegalArgumentException("Pick 1–" + choices.size());
        }
        return choices.get(number - 1).id();
    }

    /** a = attack, s = skill, d = defend, f = flee, i <itemId> = item. Optional target index after a/s. */
    private static Action parseCombat(String line, CombatView combat) {
        String[] parts = line.split("\\s+");
        int target = parts.length > 1 && parts[1].matches("\\d+") ? Integer.parseInt(parts[1]) : 0;
        return switch (parts[0].toLowerCase()) {
            case "a" -> new Action(ActionType.ATTACK, target, null);
            case "s" -> new Action(ActionType.SKILL, target, null);
            case "d" -> Action.of(ActionType.DEFEND);
            case "f" -> Action.of(ActionType.FLEE);
            case "i" -> new Action(ActionType.ITEM, 0, parts.length > 1 ? parts[1] : "");
            default -> throw new IllegalArgumentException("Combat: a [n], s [n], d, f, i <itemId>");
        };
    }

    // ---------------------------------------------------------------------
    // printing
    // ---------------------------------------------------------------------

    static void print(GameView v, PrintStream out) {
        out.println();
        out.println("== " + v.node().title() + "  [" + v.locationName() + " · " + v.region() + "]");
        var h = v.hud();
        out.printf("   HP %d/%d  EN %d/%d  Lv %d (%d/%d xp)  Gold %d  Weapon: %s%n", h.hp(), h.maxHp(), h.energy(),
                h.maxEnergy(), h.level(), h.xp(), h.xpToNext(), h.gold(),
                h.weapon() == null ? "none" : h.weapon().name() + " [" + h.weapon().tier() + "]");
        for (ToastView t : v.toasts()) {
            out.println("   * " + t.text());
        }
        if (v.combat() != null) {
            printCombat(v.combat(), out);
            return;
        }
        out.println();
        out.println(wrap(v.node().text()));
        if (v.ending() != null) {
            out.printf("%n   THE END — %s (%ds, endings found %d/%d)%n", v.ending().title(), v.ending().seconds(),
                    v.ending().endingsFound(), v.ending().endingsTotal());
            return;
        }
        int n = 1;
        for (ChoiceView c : v.choices()) {
            out.println("  " + n++ + ") " + c.label() + (c.locked() ? "   [LOCKED: " + c.lockReason() + "]" : ""));
        }
    }

    private static void printCombat(CombatView c, PrintStream out) {
        out.println("   -- " + c.encounterName() + ", round " + c.round());
        for (CombatView.EnemyView e : c.enemies()) {
            out.printf("   [%d] %s %d/%d%s%n", e.index(), e.name(), e.hp(), e.maxHp(), e.alive() ? "" : " (down)");
        }
        for (String line : c.log()) {
            out.println("   | " + line);
        }
        out.println("   a [n]=attack  s [n]=Crystal Pulse  d=defend" + (c.canFlee() ? "  f=flee" : "")
                + "  i <item>=use item");
    }

    private static String wrap(String text) {
        StringBuilder sb = new StringBuilder("   ");
        int column = 3;
        for (String word : text.split(" ")) {
            if (column + word.length() > 90) {
                sb.append("\n   ");
                column = 3;
            }
            sb.append(word).append(' ');
            column += word.length() + 1;
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // --auto-quick: integration check with real conditions (spec §8.4)
    // ---------------------------------------------------------------------

    /**
     * Plays {@link #QUICK_ROUTE} through the real engine: at every node it
     * takes the first UNLOCKED choice leading to the next route node, and in
     * combat it attacks until the fight ends. Uses a fixed seed and Demo Mode.
     * Returns true if END_QUICK is reached. Time O(route length · k).
     * Offline variant: player id 0 (nothing is persisted).
     */
    public static boolean playQuickRoute(GameEngine engine, PrintStream out) {
        return playQuickRoute(engine, out, 0);
    }

    /** Same, as an existing player (required with a database: endings and achievements are stored). */
    public static boolean playQuickRoute(GameEngine engine, PrintStream out, int playerId) {
        GameState state = engine.newGame(playerId, true, AUTO_SEED);
        StoryGraph graph = engine.content().graph();
        GameView view = engine.view(state);
        int index = 0;
        int guard = 0;
        while (!"ENDED".equals(view.mode()) && guard++ < 200) {
            if ("COMBAT".equals(view.mode())) {
                view = engine.combatAction(state, Action.of(ActionType.ATTACK));
                continue;
            }
            index = QUICK_ROUTE.indexOf(view.node().id());
            if (index < 0 || index + 1 >= QUICK_ROUTE.size()) {
                out.println("auto-quick: left the route at " + view.node().id());
                return false;
            }
            Choice next = unlockedChoiceTo(graph, state, view.node().id(), QUICK_ROUTE.get(index + 1));
            if (next == null) {
                out.println("auto-quick: no unlocked choice from " + view.node().id() + " to " + QUICK_ROUTE.get(index + 1));
                return false;
            }
            out.println("auto-quick: " + view.node().id() + " → " + next.to() + "  (\"" + next.label() + "\")");
            view = engine.choose(state, next.id());
        }
        boolean ok = view.ending() != null && StoryGraph.QUICK_ENDING_NODE.equals(view.ending().id());
        out.println(ok ? "auto-quick: reached END_QUICK" : "auto-quick: FAILED at " + view.node().id());
        return ok;
    }

    private static Choice unlockedChoiceTo(StoryGraph graph, GameState state, String from, String to) {
        for (Choice choice : graph.choicesFrom(from)) {
            if (choice.to().equals(to) && choice.conditions().test(state)) {
                return choice;
            }
        }
        return null;
    }

    private static boolean has(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }
}
