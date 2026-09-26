/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;

/** Fixed identities for local mock authentication; the session cookie selects one. */
public class DemoUsers implements QAuthenticationModuleCustomizerInterface
{
   public static final String ADMIN_SESSION = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
   public static final String DEMO_SESSION  = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";

   @Override
   public void customizeSession(QInstance instance, QSession session, Map<String, Object> context)
   {
      Boolean admin = ADMIN_SESSION.equals(session.getUuid());
      String name = admin ? "Admin" : "Demo User";
      String userId = admin ? "core:admin" : "core:demo";
      session.getUser().setIdReference(userId);
      session.getUser().setFullName(name);
      session.withValueForFrontend("user", new LinkedHashMap<>(Map.of("name", name, "email", userId + "@example.test")));
   }
}
