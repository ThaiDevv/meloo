package com.meloo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

  @Test
  void moduleStructureIsValid() {
    ApplicationModules.of(MelooApplication.class).verify();
  }
}
