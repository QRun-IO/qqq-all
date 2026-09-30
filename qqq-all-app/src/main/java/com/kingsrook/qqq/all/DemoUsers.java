/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;

/** Fixed identities for local mock authentication; the session cookie selects one. */
public class DemoUsers implements QAuthenticationModuleCustomizerInterface
{
   public static final String ADMIN_SESSION = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
   public static final String DEMO_SESSION  = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
   private static final List<String> DEMO_TABLES = List.of("customer", "order", "orderLine", "product", "address");
   private static final List<String> WEBHOOK_TABLES = List.of("webhook", "webhookSubscription",
      "webhookEvent", "webhookEventContent", "webhookEventSendLog", "scheduledJob", "scheduledJobParameter");
   private static final List<String> WEBHOOK_PROCESSES = List.of("SendWebhookEvent",
      "SendTestEventToWebhook", "ManageWebhookHealth");

   static void protectDemoTables(QInstance instance)
   {
      for(String tableName : DEMO_TABLES)
      {
         instance.getTable(tableName).setPermissionRules(new QPermissionRules()
            .withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS));
      }
      protectWebhookResources(instance);
   }

   static void protectWebhookResources(QInstance instance)
   {
      for(String tableName : WEBHOOK_TABLES)
      {
         instance.getTable(tableName).setPermissionRules(new QPermissionRules()
            .withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS));
      }
      for(String processName : WEBHOOK_PROCESSES)
      {
         instance.getProcess(processName).setPermissionRules(new QPermissionRules()
            .withLevel(PermissionLevel.HAS_ACCESS_PERMISSION));
      }
   }

   @Override
   public void customizeSession(QInstance instance, QSession session, Map<String, Object> context)
   {
      session.withPermissions("apiCatalog.read", "demoNote.read");
      Boolean admin = ADMIN_SESSION.equals(session.getUuid());
      String name = admin ? "Admin" : "Demo User";
      String userId = admin ? "core:admin" : "core:demo";
      session.getUser().setIdReference(userId);
      session.getUser().setFullName(name);
      for(String tableName : DEMO_TABLES)
      {
         session.withPermission(tableName + ".read");
         if(admin)
         {
            session.withPermissions(tableName + ".insert", tableName + ".edit", tableName + ".delete");
         }
      }
      for(String tableName : WEBHOOK_TABLES)
      {
         session.withPermission(tableName + ".read");
         if(admin)
         {
            session.withPermissions(tableName + ".insert", tableName + ".edit", tableName + ".delete");
         }
      }
      if(admin)
      {
         session.withPermissions("calculateOrderTotal.hasAccess", "demoNote.insert", "demoNote.edit", "demoNote.delete");
         session.withPermissions("esbView.hasAccess", "esbOperate.hasAccess", "esbDelete.hasAccess");
         for(String processName : WEBHOOK_PROCESSES)
         {
            session.withPermission(processName + ".hasAccess");
         }
      }
      session.withValueForFrontend("user", new LinkedHashMap<>(Map.of("name", name, "email", userId + "@example.test")));
   }
}
