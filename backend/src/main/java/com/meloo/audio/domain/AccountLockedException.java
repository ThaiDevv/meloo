package com.meloo.audio.domain;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends AudioException {

  public AccountLockedException() {
    super(
        HttpStatus.FORBIDDEN,
        "ACCOUNT_LOCKED",
        "Account locked",
        "The account associated with this session is locked (BR-28).");
  }
}
