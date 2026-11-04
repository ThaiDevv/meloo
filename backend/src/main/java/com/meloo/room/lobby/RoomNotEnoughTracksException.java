package com.meloo.room.lobby;

/** Ngoại lệ khi bộ nhạc hoặc bộ lọc không đủ bài hát cho số vòng đã chọn (BR-09, HTTP 422). */
public class RoomNotEnoughTracksException extends RuntimeException {

  private final int missingTracks;

  public RoomNotEnoughTracksException(int missingTracks) {
    super("The playlist or filter has too few valid tracks for the chosen round count");
    this.missingTracks = missingTracks;
  }

  public int missingTracks() {
    return missingTracks;
  }
}
