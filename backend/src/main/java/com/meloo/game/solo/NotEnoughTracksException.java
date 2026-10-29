package com.meloo.game.solo;

/** Ngoại lệ khi bộ nhạc hoặc bộ lọc không đủ bài hợp lệ cho số vòng (BR-09, HTTP 422). */
public class NotEnoughTracksException extends RuntimeException {

  private final int missingTracks;

  public NotEnoughTracksException(int missingTracks) {
    super("The playlist or filter has too few valid tracks (missing " + missingTracks + ").");
    this.missingTracks = missingTracks;
  }

  public int missingTracks() {
    return missingTracks;
  }
}
