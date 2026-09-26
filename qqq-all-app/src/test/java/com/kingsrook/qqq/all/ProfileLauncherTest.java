/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The executable profile selector must choose the right startup path. */
class ProfileLauncherTest
{
   @Test
   void selectsCoreByDefaultAndFullWhenRequested()
   {
      assertEquals("core", ProfileLauncher.profile(Map.of()));
      assertEquals("full", ProfileLauncher.profile(Map.of("QQQ_ALL_PROFILE", "full")));
      assertThrows(IllegalArgumentException.class,
         () -> ProfileLauncher.profile(Map.of("QQQ_ALL_PROFILE", "typo")));
   }

   @Test
   void fullFailsForMissingConfigurationBeforeOpeningServices()
   {
      assertThrows(IllegalArgumentException.class,
         () -> ProfileLauncher.launch(Map.of("QQQ_ALL_PROFILE", "full")));
   }

   @Test
   void fullRequiresAnExplicitHttpBindAddress()
   {
      Map<String, String> environment = new HashMap<>(FullProfileTest.environment());
      environment.remove("QQQ_ALL_BIND_HOST");
      assertEquals("Missing required environment variable QQQ_ALL_BIND_HOST",
         assertThrows(IllegalArgumentException.class,
            () -> FullProfileSettings.from(environment)).getMessage());
   }
}
