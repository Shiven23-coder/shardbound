/**
 * PostgreSQL repositories: Spring {@code JdbcClient} with hand-written SQL, no
 * ORM (P-03). They implement the engine's ports ({@code engine.port}) and hold
 * player data only; game content never reaches the database (P-04).
 */
package com.shardbound.persistence;
