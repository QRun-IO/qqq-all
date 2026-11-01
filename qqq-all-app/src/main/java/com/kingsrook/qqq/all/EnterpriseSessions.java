/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qqq.backend.core.modules.authentication.QSessionStoreHelper;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;

/**
 * Records mock sign-ins in the session store (qbit-session-store) for the
 * enterprise profile.
 *
 * Only the OAuth2 module writes to the session store; MOCK authentication
 * builds a fresh session on every request and stores nothing, so the
 * storedSession table stayed empty and "who is online" could not count
 * terminals. This is the smallest hook: the mock module's session customizer
 * calls {@link #record(QSession)}, which stores one row per session uuid
 * (user id, a lean copy of the session, expiresAt) the first time this JVM
 * sees it and touches it (sliding expiry) at most once a minute after that.
 */
final class EnterpriseSessions
{
   static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

   private static final Map<String, Long> LAST_SEEN = new ConcurrentHashMap<>();

   private EnterpriseSessions()
   {
   }

   static void record(QSession session)
   {
      String uuid = session == null ? null : session.getUuid();
      if(uuid == null || !QSessionStoreHelper.isSessionStoreAvailable())
      {
         return;
      }
      long now = System.currentTimeMillis();
      Long last = LAST_SEEN.get(uuid);
      if(last != null && now - last < TOUCH_INTERVAL.toMillis())
      {
         return;
      }
      LAST_SEEN.put(uuid, now);
      if(last == null && QSessionStoreHelper.loadSession(uuid).isEmpty())
      {
         QSessionStoreHelper.storeSession(uuid, lean(session), QSessionStoreHelper.getDefaultTtl());
      }
      else
      {
         QSessionStoreHelper.touchSession(uuid);
      }
   }

   /** The identity only: the admin session's thousand permissions do not belong in a row. */
   static QSession lean(QSession session)
   {
      QSession lean = new QSession();
      lean.setUuid(session.getUuid());
      lean.setIdReference(session.getUuid());
      if(session.getUser() != null)
      {
         QUser user = new QUser();
         user.setIdReference(session.getUser().getIdReference());
         user.setFullName(session.getUser().getFullName());
         lean.setUser(user);
      }
      return (lean);
   }

   /** Tests start from a clean slate. */
   static void forget()
   {
      LAST_SEEN.clear();
   }
}
