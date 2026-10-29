package com.meloo.room.api.message;

/** Command to toggle or set ready status in lobby. */
public record ReadyCommand(boolean ready) {}
