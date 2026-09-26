/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.userrolepermissions.model.Role;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.AuthScope;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Keycloak realm roles must flow through the role permission resolver on login and resume. */
class FullOidcSessionCustomizerTest
{
   @Test
   void resolvesRolesBeforeAnAuthenticationSessionExists()
   {
      QInstance instance = new QInstance();
      instance.registerAuthenticationProvider(AuthScope.instanceDefault(),
         new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK));
      instance.addBackend(new QBackendMetaData().withName("memory")
         .withBackendType(MemoryBackendModule.class));
      instance.addTable(new QTableMetaData().withName(Role.TABLE_NAME).withBackendName("memory")
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING)));

      QContext.init(instance, null);
      try
      {
         QSession session = new QSession();
         new FullOidcSessionCustomizer().customizeSession(instance, session,
            Map.of("idToken", new JSONObject("{\"realm_access\":{\"roles\":[\"viewer\"]}}")));
         assertEquals(Set.of(), session.getPermissions());
         assertNull(QContext.getQSession());
      }
      finally
      {
         QContext.clear();
      }
   }

   @Test
   void mapsIdTokenRolesToSessionPermissions()
   {
      RecordingCustomizer customizer = new RecordingCustomizer();
      QSession session = new QSession();
      customizer.customizeSession(null, session, Map.of("idToken", new JSONObject("{\"realm_access\":{\"roles\":[\"admin\",\"viewer\"]}}")));
      assertEquals(Set.of("admin", "viewer"), customizer.requestedRoles);
      assertEquals(Set.of("customer.read"), session.getPermissions());
   }

   @Test
   void usesAccessTokenRolesOnSessionResumeAndDoesNotGrantUnknownRoles()
   {
      RecordingCustomizer customizer = new RecordingCustomizer();
      QSession session = new QSession();
      customizer.customizeSession(null, session, Map.of("jwtPayloadJsonObject", new JSONObject("{\"realm_access\":{\"roles\":[\"viewer\"]}}")));
      assertEquals(Set.of("viewer"), customizer.requestedRoles);

      QSession noRoles = new QSession();
      customizer.customizeSession(null, noRoles, Map.of("jwtPayloadJsonObject", new JSONObject("{}")));
      assertEquals(Set.of(), noRoles.getPermissions());
   }

   @Test
   void usesAccessTokenRolesWhenIdTokenHasNoRealmRoles()
   {
      RecordingCustomizer customizer = new RecordingCustomizer();
      QSession session = new QSession();
      customizer.customizeSession(null, session, Map.of(
         "idToken", new JSONObject("{\"email\":\"demo@example.test\"}"),
         "jwtPayloadJsonObject", new JSONObject("{\"realm_access\":{\"roles\":[\"viewer\"]}}")));
      assertEquals(Set.of("viewer"), customizer.requestedRoles);
   }

   private static class RecordingCustomizer extends FullOidcSessionCustomizer
   {
      private Set<String> requestedRoles;

      @Override
      protected Set<String> permissionsForRoles(Set<String> roles)
      {
         requestedRoles = roles;
         return roles.isEmpty() ? Set.of() : Set.of("customer.read");
      }
   }
}
