package com.meloo.catalog.api;

import java.util.List;

/** One page of admin results. */
public record AdminPage<T>(List<T> items, int page, int size, long total) {}
