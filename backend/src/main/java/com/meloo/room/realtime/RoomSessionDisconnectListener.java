package com.meloo.room.realtime;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class RoomSessionDisconnectListener {

  private final RoomRegistry roomRegistry;

  public RoomSessionDisconnectListener(RoomRegistry roomRegistry) {
    this.roomRegistry = roomRegistry;
  }

  @EventListener
  public void onSessionDisconnect(SessionDisconnectEvent event) {
    roomRegistry.disconnectSession(event.getSessionId());
  }
}
