package com.meloo.room.api.message;

/** Command to give up / skip at 30s mark (BR-04). */
public record GiveUpCommand(int roundNo) {}
