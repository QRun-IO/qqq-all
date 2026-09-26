/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullQBitsTest
{
   @TempDir Path dataDirectory;

   @Test
   void fullAddsCustomerSearchAndSftpImportToTheDemo() throws Exception
   {
      FullProfileSettings settings = FullProfileSettings.from(FullProfileTest.environment());
      QInstance instance = new FullProfileApplication(dataDirectory, settings).defineQInstance();
      new QInstanceValidator().validate(instance);

      Set<String> artifacts = instance.getQBits().values().stream()
         .map(qbit -> qbit.getArtifactId()).collect(Collectors.toSet());
      assertTrue(artifacts.containsAll(Set.of("quick-search", "user-role-permissions",
         "customizable-table-views", "standard-process-trace", "webhooks", "workflows",
         "qbit-geo-data", "sftp-data-integration")));
      assertNotNull(instance.getApp("quickSearchAdmin"));
      assertNotNull(instance.getProcess("quickSearchFullReindex"));
      assertNotNull(instance.getProcess("quickSearchReconcileIndex"));
      assertNotNull(instance.getProcess("SFTPImportFileSyncProcess"));
      assertNotNull(instance.getTable("SFTPImportSourceFileTable"));
      assertEquals("sftp", instance.getTable("SFTPImportSourceFileTable").getBackendName());
      for(String tableName : Set.of("webhook", "webhookSubscription", "webhookEvent",
         "webhookEventContent", "webhookEventSendLog", "scheduledJob", "scheduledJobParameter"))
      {
         assertEquals(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS,
            instance.getTable(tableName).getPermissionRules().getLevel(), tableName);
      }
      for(String processName : Set.of("SendWebhookEvent", "SendTestEventToWebhook", "ManageWebhookHealth"))
      {
         assertEquals(PermissionLevel.HAS_ACCESS_PERMISSION,
            instance.getProcess(processName).getPermissionRules().getLevel(), processName);
      }
      QuickSearchQBitConfig search = (QuickSearchQBitConfig) instance.getQBits().values().stream()
         .filter(qbit -> "quick-search".equals(qbit.getArtifactId())).findFirst().orElseThrow().getConfig();
      assertEquals("customer", search.getSearchableTables().getFirst().getTableName());
      assertEquals(Set.of("name", "email"), search.getSearchableTables().getFirst().getFields().stream()
         .map(field -> field.getFieldName()).collect(Collectors.toSet()));
   }

   @Test
   void viewerPermissionsCannotChangeWebhookDestinationOrSendEvent() throws Exception
   {
      QInstance instance = new FullProfileApplication(dataDirectory,
         FullProfileSettings.from(FullProfileTest.environment())).defineQInstance();
      QSession viewer = new QSession().withPermissions("webhook.read", "webhookSubscription.read");
      QContext.init(instance, viewer);
      try
      {
         assertFalse(PermissionsHelper.hasTablePermission(new QueryInput("webhook"),
            "webhook", TablePermissionSubType.EDIT));
         assertFalse(PermissionsHelper.hasTablePermission(new QueryInput("webhookSubscription"),
            "webhookSubscription", TablePermissionSubType.EDIT));
         assertThrows(QPermissionDeniedException.class,
            () -> PermissionsHelper.checkProcessPermissionThrowing(new RunProcessInput(), "SendWebhookEvent"));
      }
      finally
      {
         QContext.clear();
      }
   }

   @Test
   void adminPermissionsCanChangeWebhookDestinationAndSendEvent() throws Exception
   {
      QInstance instance = new FullProfileApplication(dataDirectory,
         FullProfileSettings.from(FullProfileTest.environment())).defineQInstance();
      QSession admin = new QSession().withPermissions("webhook.edit", "webhookSubscription.edit",
         "SendWebhookEvent.hasAccess");
      QContext.init(instance, admin);
      try
      {
         assertTrue(PermissionsHelper.hasTablePermission(new QueryInput("webhook"),
            "webhook", TablePermissionSubType.EDIT));
         assertTrue(PermissionsHelper.hasTablePermission(new QueryInput("webhookSubscription"),
            "webhookSubscription", TablePermissionSubType.EDIT));
         PermissionsHelper.checkProcessPermissionThrowing(new RunProcessInput(), "SendWebhookEvent");
      }
      finally
      {
         QContext.clear();
      }
   }
}
