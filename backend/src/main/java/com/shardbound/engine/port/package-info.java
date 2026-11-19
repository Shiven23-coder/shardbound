/**
 * Interfaces the engine needs from the outside world (saves, meta progress,
 * durable runs). The persistence layer implements them; the engine never sees
 * SQL or Spring.
 */
package com.shardbound.engine.port;
