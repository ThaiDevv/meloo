package com.meloo.catalog.api;

import org.jspecify.annotations.Nullable;

/**
 * Before and after value of a write, returned so the admin module can write the audit log (BR-28).
 * The catalog module never writes audit entries itself.
 *
 * @param before state before the write, null when the write created the object
 * @param after state after the write, null when the write deleted the object
 */
public record AdminChange<T>(@Nullable T before, @Nullable T after) {}
