package com.meloo.progression.service;

import com.meloo.game.events.MatchFinished;
import com.meloo.identity.events.GuestMerged;
import com.meloo.progression.events.RankPointsApplied;
import com.meloo.room.api.RoomRankPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class ProgressionListeners {
  private final ProgressionWriter writer;
  private final RoomRankPublisher ranks;

  public ProgressionListeners(ProgressionWriter writer, RoomRankPublisher ranks) {
    this.writer = writer;
    this.ranks = ranks;
  }

  @ApplicationModuleListener
  public void matchFinished(MatchFinished event) {
    writer.record(event);
  }

  @ApplicationModuleListener
  public void guestMerged(GuestMerged event) {
    writer.merge(event);
  }

  @ApplicationModuleListener
  public void rankPointsApplied(RankPointsApplied event) {
    ranks.publish(event.roomId(), event.matchId(), event.changes(), event.appliedAt()).join();
  }
}
