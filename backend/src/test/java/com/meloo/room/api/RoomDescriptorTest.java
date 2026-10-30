package com.meloo.room.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoomDescriptorTest {

  @Test
  void descriptorKeepsAnImmutableMembershipSnapshot() {
    UUID memberId = UUID.randomUUID();
    Set<UUID> members = new HashSet<>(Set.of(memberId));

    RoomDescriptor descriptor = new RoomDescriptor(UUID.randomUUID(), members, true);
    members.clear();

    assertThat(descriptor.memberIds()).containsExactly(memberId);
    assertThat(descriptor.hasMember(memberId)).isTrue();
    assertThat(descriptor.privateRoom()).isTrue();
  }
}
