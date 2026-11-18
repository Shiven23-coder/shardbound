package com.shardbound;

import com.shardbound.api.CombatController;
import com.shardbound.api.ErrorHandler;
import com.shardbound.api.GameController;
import com.shardbound.api.MetaController;
import com.shardbound.api.ProfileController;
import com.shardbound.api.SaveController;
import com.shardbound.config.AppConfig;
import com.shardbound.config.Database;
import com.shardbound.content.ContentAssembler;
import com.shardbound.content.GameContent;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.SessionStore;
import com.shardbound.persistence.ContentRepository;
import com.shardbound.persistence.MetaRepository;
import com.shardbound.persistence.PlayerRepository;
import com.shardbound.persistence.SaveRepository;
import io.javalin.Javalin;

/**
 * Starts the backend (spec §2):
 * <ol>
 *   <li>read db.properties</li>
 *   <li>load ALL content from PostgreSQL once and build the in-memory objects</li>
 *   <li>compute the quest order with Kahn's algorithm (fails fast on a cycle)</li>
 *   <li>register the routes and start Javalin on app.port (default 7070)</li>
 * </ol>
 * Run: {@code .\mvnw.cmd exec:java -Dexec.mainClass=com.shardbound.App}
 */
public class App {

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        Database db = Database.from(config);

        GameContent content = new ContentAssembler().assemble(new ContentRepository(db).loadAll());
        content.prepareForPlay();

        PlayerRepository players = new PlayerRepository(db);
        MetaRepository meta = new MetaRepository(db);
        SaveRepository saves = new SaveRepository(db, config.simulateSaveCrash());
        GameEngine engine = new GameEngine(content, saves, meta);
        SessionStore store = new SessionStore();

        Javalin app = Javalin.create(javalin -> javalin.showJavalinBanner = false);
        ErrorHandler.register(app);
        new ProfileController(players).register(app);
        new GameController(engine, store, players).register(app);
        new CombatController(engine, store).register(app);
        new SaveController(engine, store).register(app);
        new MetaController(engine, store, meta).register(app);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engine.shutdown();
            app.stop();
        }));
        app.start(config.port());
        System.out.println("Shardbound API on http://localhost:" + config.port()
                + "  (" + content.graph().nodes().size() + " nodes loaded"
                + (config.simulateSaveCrash() ? ", SAVE CRASH SIMULATION ON" : "") + ")");
    }
}
