package com.meloo.catalog.api;

import java.util.UUID;

/** Public playlist details exposed to other modules without leaking the persistence entity. */
public record PlaylistInfo(
    UUID id, String code, String name, boolean enabled, int enabledTrackCount) {}
