/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.core.modules.authentication.QSessionStoreProviderInterface;
import com.kingsrook.qqq.backend.core.modules.authentication.QSessionStoreRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Mock sign-ins become one stored session per uuid, touched rather than re-stored. */
class EnterpriseSessionsTest
{
   private final RecordingStore store = new RecordingStore();

   @BeforeEach
   void setUp()
   {
      EnterpriseSessions.forget();
      QSessionStoreRegistry.getInstance().clear();
      QSessionStoreRegistry.getInstance().register(store);
   }

   @AfterEach
   void tearDown()
   {
      QSessionStoreRegistry.getInstance().clear();
      EnterpriseSessions.forget();
   }

   @Test
   void oneRowPerSessionUuid()
   {
      EnterpriseSessions.record(session("aaaa", "core:admin"));
      EnterpriseSessions.record(session("aaaa", "core:admin"));
      EnterpriseSessions.record(session("bbbb", "core:admin"));

      assertEquals(List.of("aaaa", "bbbb"), new ArrayList<>(store.sessions.keySet()));
      assertEquals("core:admin", store.sessions.get("bbbb").getUser().getIdReference());
      assertNull(store.sessions.get("aaaa").getPermissions(), "only the identity is stored");
   }

   @Test
   void aKnownSessionIsTouchedNotStoredAgain()
   {
      store.sessions.put("cccc", session("cccc", "core:admin"));
      EnterpriseSessions.record(session("cccc", "core:admin"));
      assertEquals(0, store.stores);
      assertEquals(1, store.touches);
   }

   private static QSession session(String uuid, String userId)
   {
      QSession session = new QSession();
      session.setUuid(uuid);
      QUser user = new QUser();
      user.setIdReference(userId);
      session.setUser(user);
      session.withPermissions("customer.read", "customer.edit");
      return (session);
   }

   /** A session store that keeps what it is given and counts calls. */
   private static class RecordingStore implements QSessionStoreProviderInterface
   {
      private final Map<String, QSession> sessions = new LinkedHashMap<>();
      private int stores;
      private int touches;

      @Override
      public void store(String sessionUuid, QSession session, Duration ttl)
      {
         stores++;
         sessions.put(sessionUuid, session);
      }

      @Override
      public Optional<QSession> load(String sessionUuid)
      {
         return (Optional.ofNullable(sessions.get(sessionUuid)));
      }

      @Override
      public void remove(String sessionUuid)
      {
         sessions.remove(sessionUuid);
      }

      @Override
      public void touch(String sessionUuid)
      {
         touches++;
      }

      @Override
      public Duration getDefaultTtl()
      {
         return (Duration.ofHours(1));
      }
   }
}
