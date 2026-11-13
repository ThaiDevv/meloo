package com.meloo.identity.web;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.meloo.IntegrationTest;
import com.meloo.identity.api.UserAccounts;
import com.meloo.identity.persistence.*;
import com.meloo.shared.api.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class ProfileSetupIntegrationTest extends IntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired AppUserRepository users;
  @Autowired UserAccounts accounts;

  @Test
  void invalidSetupNeverChangesNicknameAvatarOrCompletionAndCannotTargetAnotherUser()
      throws Exception {
    AppUser user = pending(), other = pending();
    for (String body :
        List.of(
            "{\"nickname\":\"Valid Name\",\"avatarId\":\"invalid\"}",
            "{\"nickname\":\"ab\",\"avatarId\":\"guitar\"}",
            "{\"nickname\":\"địt\",\"avatarId\":\"guitar\"}",
            "{\"nickname\":\"                     \",\"avatarId\":\"guitar\"}")) {
      update(user, body).andExpect(status().isBadRequest());
      assertPending(user);
    }
    assertThatThrownBy(() -> accounts.updateProfile(user.getId(), "Valid Name", "invalid"))
        .isInstanceOf(ApiException.class);
    assertPending(user);
    update(user, "{\"nickname\":\"Chosen\",\"avatarId\":\"disc\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user.getId().toString()))
        .andExpect(jsonPath("$.profileCompleted").value(true));
    // Resource identity is taken only from JWT; adding another user's ID in the URL cannot edit it.
    mvc.perform(
            patch("/api/v1/me/profile/" + other.getId())
                .with(jwt().jwt(j -> j.subject(user.getId().toString())))
                .contentType("application/json")
                .content("{\"nickname\":\"Attacker\",\"avatarId\":\"mic\"}"))
        .andExpect(status().is4xxClientError());
    assertPending(other);
    assertThat(accounts.get(user.getId()).avatarId()).isEqualTo("disc");
  }

  @Test
  void partialUpdatesPreserveCompletionAndOtherFieldsWhileInitialSetupRequiresBoth()
      throws Exception {
    AppUser user = pending();
    update(user, "{\"nickname\":\"Chosen Name\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileCompleted").value(false));
    update(user, "{\"nickname\":\"Chosen Name\",\"avatarId\":\"headphones\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profileCompleted").value(true));
    update(user, "{\"avatarId\":\"radio\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("Chosen Name"))
        .andExpect(jsonPath("$.avatarId").value("radio"))
        .andExpect(jsonPath("$.profileCompleted").value(true));
    update(user, "{\"nickname\":\"New Name\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.avatarId").value("radio"))
        .andExpect(jsonPath("$.profileCompleted").value(true));
  }

  private ResultActions update(AppUser user, String body) throws Exception {
    return mvc.perform(
        patch("/api/v1/me/profile")
            .with(jwt().jwt(j -> j.subject(user.getId().toString())))
            .contentType("application/json")
            .content(body));
  }

  private AppUser pending() {
    return users.saveAndFlush(
        AppUser.builder()
            .id(UUID.randomUUID())
            .kind(UserKind.REGISTERED)
            .nickname("Player_Original")
            .profileCompleted(false)
            .build());
  }

  private void assertPending(AppUser user) {
    AppUser actual = users.findById(user.getId()).orElseThrow();
    assertThat(actual.getNickname()).isEqualTo("Player_Original");
    assertThat(actual.getAvatarId()).isNull();
    assertThat(actual.isProfileCompleted()).isFalse();
  }
}
