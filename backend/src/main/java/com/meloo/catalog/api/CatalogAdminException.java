package com.meloo.catalog.api;

/** Expected failure of a catalog admin operation; the admin module maps it to an HTTP status. */
public final class CatalogAdminException extends RuntimeException {

  /** Why the operation was refused. */
  public enum Reason {
    NOT_FOUND,
    INVALID_ARGUMENT,
    CONFLICT,
    /** Playlist would have fewer valid tracks than required (BR-09). */
    PLAYLIST_NOT_ENOUGH_TRACKS
  }

  private final Reason reason;

  public CatalogAdminException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
