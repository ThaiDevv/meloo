package com.meloo.room.api.message;

/** Command confirming round audio preload is complete (for PREPARE phase). */
public record PreparedCommand(int roundNo) {}
