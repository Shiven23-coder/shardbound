/**
 * Content pipeline shared by the database path and the JSON path:
 * raw definitions (RawContent), the JSON reader, and ContentAssembler, which
 * turns raw rows into the immutable in-memory GameContent used by the engine.
 */
package com.shardbound.content;
