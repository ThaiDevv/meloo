package com.meloo.room.api.message;

import java.util.UUID;

/** Command to submit track guess (BR-05, BR-07, BR-08). */
public record GuessCommand(int roundNo, UUID trackId) {}
