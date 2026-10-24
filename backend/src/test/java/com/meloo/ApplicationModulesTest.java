package com.meloo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

  @Test
  void moduleStructureIsValid() {
    ApplicationModules modules = ApplicationModules.of(MelooApplication.class);
    modules.verify();

    assertThat(modules.getModuleByName("generated"))
        .hasValueSatisfying(
            generatedModule -> {
              assertThat(generatedModule.isOpen()).isTrue();
            });
  }
}
