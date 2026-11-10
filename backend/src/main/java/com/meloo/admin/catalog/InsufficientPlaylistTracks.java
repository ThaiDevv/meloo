package com.meloo.admin.catalog;

public class InsufficientPlaylistTracks extends RuntimeException {
  private final int missing;

  public InsufficientPlaylistTracks(int missing) {
    super("The playlist needs more enabled tracks");
    this.missing = missing;
  }

  public int missing() {
    return missing;
  }
}
