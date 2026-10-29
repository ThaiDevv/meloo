package com.meloo.room.api.message;

/** Command to send chat message in room (BR-16, BR-17). */
public record ChatCommand(String content) {}
