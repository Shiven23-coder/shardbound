package com.shardbound.persistence;

/**
 * Wraps an unexpected SQLException from a repository (not a save transaction,
 * which uses SaveFailedException). Unchecked so callers are not forced to
 * catch errors they cannot handle; ErrorHandler maps it to HTTP 500.
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message, Throwable cause) {
        super(message + ": " + cause.getMessage(), cause);
    }
}
