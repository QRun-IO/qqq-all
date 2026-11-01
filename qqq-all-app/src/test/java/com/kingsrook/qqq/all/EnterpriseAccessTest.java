/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import java.util.Map;
import com.kingsrook.qbits.collaboration.customfields.FieldSecurity;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The enterprise admin passes qbit-collaboration's Custom Fields checker (qqq-all#47). */
class EnterpriseAccessTest
{
   @TempDir Path dataDirectory;

   @AfterEach
   void tearDown()
   {
      QContext.clear();
   }

   @Test
   void adminCanReadAndManageCustomFields() throws Exception
   {
      QInstance instance = new QqqAllApplication(dataDirectory, "tcp://127.0.0.1:61616").defineQInstance();
      QSession plain = new QSession();
      QContext.init(instance, plain);
      assertThrows(QPermissionDeniedException.class,
         () -> new FieldSecurity().checkPermissionsThrowing(new QueryInput("collaboration_field_definition"), null));

      QSession admin = new QSession();
      DemoUsers.grantEnterpriseAccess(instance, admin, "core:admin");
      QContext.init(instance, admin);
      assertDoesNotThrow(() -> new FieldSecurity().checkPermissionsThrowing(new QueryInput("collaboration_field_definition"), null));
      assertDoesNotThrow(() -> new FieldSecurity().checkPermissionsThrowing(new InsertInput("collaboration_field_definition"), null));
   }

   @Test
   void customizedSessionsStillCarryTheUserForTheFrontend() throws Exception
   {
      QInstance instance = new QqqAllApplication(dataDirectory, "tcp://127.0.0.1:61616").defineQInstance();
      QSession session = new QSession();
      session.setUuid(DemoUsers.ADMIN_SESSION);
      session.setUser(new QUser());
      new DemoUsers().customizeSession(instance, session, Map.of());
      assertEquals("Admin", ((Map<?, ?>) session.getValuesForFrontend().get("user")).get("name"));
   }
}
