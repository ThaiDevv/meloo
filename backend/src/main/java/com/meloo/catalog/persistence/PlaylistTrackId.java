package com.meloo.catalog.persistence;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PlaylistTrackId implements Serializable {

  private UUID playlistId;
  private UUID trackId;
}
