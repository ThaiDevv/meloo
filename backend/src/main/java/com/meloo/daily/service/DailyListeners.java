package com.meloo.daily.service;

import com.meloo.audio.api.AudioService;
import com.meloo.daily.events.DailyAttemptFinished;
import com.meloo.identity.events.GuestMerged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DailyListeners {
  private final DailyOperations operations;
  private final AudioService audio;

  public DailyListeners(DailyOperations operations, AudioService audio) {
    this.operations = operations;
    this.audio = audio;
  }

  @ApplicationModuleListener
  public void onGuestMerged(GuestMerged event) {
    operations.merge(event);
  }

  @TransactionalEventListener
  public void onFinished(DailyAttemptFinished event) {
    audio.releaseRoundAudio(event.attemptId());
  }
}
