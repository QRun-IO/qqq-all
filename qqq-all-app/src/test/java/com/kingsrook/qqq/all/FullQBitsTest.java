/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchStartupMode;
import com.kingsrook.qbits.quicksearch.listeners.QuickSearchRecordChangeListener;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullQBitsTest
{
   private static final Path    QUICK_SEARCH_GRANTS = Path.of("..", "infra", "postgres", "06-quick-search-permissions.sql");
   private static final Pattern PERMISSION_NAME     = Pattern.compile("'(quickSearch[A-Za-z]*[.](?:hasAccess|read|write))'");

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
      assertEquals("scheduled_job", ((RDBMSTableBackendDetails) instance.getTable("scheduledJob")
         .getBackendDetails()).getTableName());
      assertEquals("create_date", instance.getTable("scheduledJob").getField("createDate").getBackendName());
      assertEquals(QFieldType.STRING, instance.getTable("processTrace").getField("userId").getType());
      assertTrue(DemoQBitData.postgresSchemaStatements(instance).stream()
         .anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS \"scheduled_job\"")
            && sql.contains("\"create_date\" TIMESTAMP")));
      assertTrue(DemoQBitData.postgresSchemaStatements(instance).stream()
         .anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS \"webhook_subscription\"")
            && sql.contains("\"webhook_event_type_name\" VARCHAR(4000)")));
      assertFalse(DemoQBitData.postgresSchemaStatements(instance).stream()
         .anyMatch(sql -> sql.contains("\"user_session\"") || sql.contains("\"warehouse_customer\"")));
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
      QuickSearchQBitConfig search = quickSearch(instance);
      assertEquals("customer", search.getSearchableTables().getFirst().getTableName());
      assertEquals(Set.of("name", "email"), search.getSearchableTables().getFirst().getFields().stream()
         .map(field -> field.getFieldName()).collect(Collectors.toSet()));
   }

   @Test
   void quickSearchRegistersItselfAndRunsOnTheDemoScheduler() throws Exception
   {
      QInstance instance = new FullProfileApplication(dataDirectory,
         FullProfileSettings.from(FullProfileTest.environment())).defineQInstance();
      new QInstanceValidator().validate(instance);

      List<QBitMetaData> registered = instance.getQBits().values().stream()
         .filter(qbit -> "quick-search".equals(qbit.getArtifactId())).toList();
      assertEquals(1, registered.size());
      assertEquals("com.kingsrook.qbits", registered.getFirst().getGroupId());
      assertNotNull(registered.getFirst().getVersion());

      QuickSearchQBitConfig search = quickSearch(instance);
      assertEquals("http://127.0.0.1:1", search.getOpensearchUrl());
      assertEquals(QuickSearchStartupMode.DEGRADED, search.getStartupMode());
      assertNull(search.getOpensearchUsername());
      assertNull(search.getOpensearchPassword());
      assertEquals("demoScheduler", search.getSchedulerName());
      assertNotNull(instance.getScheduler("demoScheduler"));
      assertEquals("demoScheduler", instance.getProcess("quickSearchBasepullIndex").getSchedule().getSchedulerName());
      assertNotNull(instance.getTable("quickSearchFailedEvent"));
      assertTrue(instance.getRecordChangeListeners().stream()
         .anyMatch(listener -> QuickSearchRecordChangeListener.class.getName().equals(listener.getName())));
   }

   @Test
   void quickSearchKeepsOpenSearchCredentialsAsEnvironmentReferences() throws Exception
   {
      Map<String, String> environment = new HashMap<>(FullProfileTest.environment());
      environment.put("QQQ_ALL_OPENSEARCH_SSL", "true");
      environment.put("QQQ_ALL_OPENSEARCH_USER", "fixture-user");
      environment.put("QQQ_ALL_OPENSEARCH_PASSWORD", "fixture-password");
      QuickSearchQBitConfig search = quickSearch(new FullProfileApplication(dataDirectory,
         FullProfileSettings.from(environment)).defineQInstance());

      assertEquals("https://127.0.0.1:1", search.getOpensearchUrl());
      assertEquals("${env.QQQ_ALL_OPENSEARCH_USER}", search.getOpensearchUsername());
      assertEquals("${env.QQQ_ALL_OPENSEARCH_PASSWORD}", search.getOpensearchPassword());
   }

   @Test
   void quickSearchBracketsAnIpv6OpenSearchHost() throws Exception
   {
      Map<String, String> environment = new HashMap<>(FullProfileTest.environment());
      environment.put("QQQ_ALL_OPENSEARCH_HOST", "::1");
      QuickSearchQBitConfig search = quickSearch(new FullProfileApplication(dataDirectory,
         FullProfileSettings.from(environment)).defineQInstance());

      assertEquals("http://[::1]:1", search.getOpensearchUrl());
   }

   @Test
   void quickSearchFailsFastWithoutOpenSearchUnlessDegraded() throws Exception
   {
      Map<String, String> environment = new HashMap<>(FullProfileTest.environment());
      environment.remove("QQQ_ALL_OPENSEARCH_STARTUP_MODE");
      QException failFast = assertThrows(QException.class, () -> new FullProfileApplication(dataDirectory,
         FullProfileSettings.from(environment)).defineQInstance());
      assertTrue(failFast.getMessage().contains("[qqq-all-customers]"), failFast.getMessage());
      assertTrue(failFast.getMessage().contains("startupMode=DEGRADED"), failFast.getMessage());

      environment.put("QQQ_ALL_OPENSEARCH_STARTUP_MODE", " degraded ");
      assertEquals(QuickSearchStartupMode.DEGRADED, quickSearch(new FullProfileApplication(dataDirectory,
         FullProfileSettings.from(environment)).defineQInstance()).getStartupMode());

      environment.put("QQQ_ALL_OPENSEARCH_STARTUP_MODE", "SOMETIMES");
      IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class, () -> new FullProfileApplication(dataDirectory,
         FullProfileSettings.from(environment)).defineQInstance());
      assertEquals("QQQ_ALL_OPENSEARCH_STARTUP_MODE must be FAIL_FAST or DEGRADED; found [SOMETIMES]", invalid.getMessage());
      assertNotNull(invalid.getCause());
   }

   @Test
   void onlyQuickSearchAdminsCanRunTheReindexAndEditItsTables() throws Exception
   {
      QInstance instance = new FullProfileApplication(dataDirectory,
         FullProfileSettings.from(FullProfileTest.environment())).defineQInstance();
      QContext.init(instance, new QSession().withPermissions("customer.read"));
      try
      {
         assertThrows(QPermissionDeniedException.class,
            () -> PermissionsHelper.checkProcessPermissionThrowing(new RunProcessInput(), "quickSearchFullReindex"));
         assertFalse(PermissionsHelper.hasTablePermission(new QueryInput("quickSearchIndex"),
            "quickSearchIndex", TablePermissionSubType.READ));
      }
      finally
      {
         QContext.clear();
      }

      // Exactly the permissions the grant file creates and gives the admin role.
      String grants = Files.readString(QUICK_SEARCH_GRANTS);
      Integer roleGrantStart = grants.indexOf("INSERT INTO role_permission_int");
      Set<String> created = permissionNames(grants.substring(0, roleGrantStart));
      assertEquals(created, permissionNames(grants.substring(roleGrantStart)));
      QContext.init(instance, new QSession().withPermissions(created.toArray(String[]::new)));
      try
      {
         for(String processName : Set.of("quickSearchFullReindex", "quickSearchReconcileIndex", "quickSearchBasepullIndex"))
         {
            PermissionsHelper.checkProcessPermissionThrowing(new RunProcessInput(), processName);
         }
         for(String tableName : Set.of("quickSearchIndex", "quickSearchIndexRun", "quickSearchFailedEvent"))
         {
            assertTrue(PermissionsHelper.hasTablePermission(new QueryInput(tableName),
               tableName, TablePermissionSubType.EDIT), tableName);
         }
      }
      finally
      {
         QContext.clear();
      }
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

   private static Set<String> permissionNames(String sql)
   {
      return PERMISSION_NAME.matcher(sql).results().map(match -> match.group(1)).collect(Collectors.toSet());
   }

   private static QuickSearchQBitConfig quickSearch(QInstance instance)
   {
      return (QuickSearchQBitConfig) instance.getQBits().values().stream()
         .filter(qbit -> "quick-search".equals(qbit.getArtifactId())).findFirst().orElseThrow().getConfig();
   }
}
