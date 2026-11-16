package com.meloo.progression.achievement;

import com.meloo.daily.events.DailyAttemptFinished;
import com.meloo.game.events.MatchFinished;
import com.meloo.identity.events.GuestMerged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AchievementListeners {
  private final AchievementService service;

  public AchievementListeners(AchievementService service) {
    this.service = service;
  }

  @ApplicationModuleListener
  public void matchFinished(MatchFinished event) {
    service.matchFinished(event);
  }

  @ApplicationModuleListener
  public void dailyFinished(DailyAttemptFinished event) {
    service.dailyFinished(event);
  }

  @ApplicationModuleListener
  public void guestMerged(GuestMerged event) {
    service.guestMerged(event);
  }
}
