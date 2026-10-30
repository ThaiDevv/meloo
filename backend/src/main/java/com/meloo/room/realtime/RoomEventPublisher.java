package com.meloo.room.realtime;

import com.meloo.room.api.message.RoomMessage;
import java.util.Objects;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Typed publisher that keeps server events on AsyncAPI destinations and requires serverTime. */
@Component
public class RoomEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public RoomEventPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  public void publish(UUID roomId, RoomTopic topic, RoomMessage event) {
    requireServerTime(event);
    messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/" + topic.path(), event);
  }

  public void sendPrivate(UUID userId, PrivateQueue queue, RoomMessage event) {
    requireServerTime(event);
    messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/" + queue.path(), event);
  }

  private static void requireServerTime(RoomMessage event) {
    Objects.requireNonNull(event, "event");
    Objects.requireNonNull(event.serverTime(), "Room messages must include serverTime");
  }

  public enum RoomTopic {
    LOBBY("lobby"),
    ROUND("round"),
    SCOREBOARD("scoreboard"),
    CHAT("chat"),
    RTC("rtc");

    private final String path;

    RoomTopic(String path) {
      this.path = path;
    }

    String path() {
      return path;
    }
  }

  public enum PrivateQueue {
    ERRORS("errors"),
    PRIVATE("private");

    private final String path;

    PrivateQueue(String path) {
      this.path = path;
    }

    String path() {
      return path;
    }
  }
}
