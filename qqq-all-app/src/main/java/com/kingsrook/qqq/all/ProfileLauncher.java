/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import java.util.Map;
import com.kingsrook.qqq.middleware.javalin.QApplicationLauncher;
import com.kingsrook.qqq.middleware.javalin.QApplicationLauncherConfig;

/** Selects the standalone or externally backed reference application. */
public final class ProfileLauncher
{
   private ProfileLauncher()
   {
   }

   public static void main(String[] args) throws Exception
   {
      launch(System.getenv());
   }

   static String profile(Map<String, String> environment)
   {
      String selected = environment.getOrDefault("QQQ_ALL_PROFILE", "core");
      if(!"core".equals(selected) && !"full".equals(selected))
      {
         throw new IllegalArgumentException("QQQ_ALL_PROFILE must be core or full");
      }
      return selected;
   }

   static void launch(Map<String, String> environment) throws Exception
   {
      String selected = profile(environment);
      Path dataDirectory = Path.of(environment.getOrDefault("QQQ_ALL_DATA_DIR", "./data"));
      Integer port = Integer.valueOf(environment.getOrDefault("QQQ_ALL_PORT", "8080"));
      if("core".equals(selected))
      {
         CoreRuntime.launch(dataDirectory, port, environment.getOrDefault("QQQ_ALL_BIND_HOST", "127.0.0.1"));
         return;
      }

      FullProfileSettings settings = FullProfileSettings.from(environment);
      CoreData.seed(dataDirectory);
      QApplicationLauncher.run(new FullProfileApplication(dataDirectory, settings),
         new QApplicationLauncherConfig().withRegisterShutdownHook(true)
            .withServerCustomizer(server -> server.withPort(port)
               .withJavalinConfigCustomizer(config -> config.jetty.host = settings.get("BIND_HOST"))));
   }
}
