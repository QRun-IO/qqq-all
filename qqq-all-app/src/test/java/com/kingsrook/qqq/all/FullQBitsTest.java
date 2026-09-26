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
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
      assertEquals(PermissionLevel.NOT_PROTECTED, instance.getTable("webhook").getPermissionRules().getLevel());
      assertEquals(PermissionLevel.NOT_PROTECTED, instance.getProcess("SendWebhookEvent").getPermissionRules().getLevel());
      QuickSearchQBitConfig search = (QuickSearchQBitConfig) instance.getQBits().values().stream()
         .filter(qbit -> "quick-search".equals(qbit.getArtifactId())).findFirst().orElseThrow().getConfig();
      assertEquals("customer", search.getSearchableTables().getFirst().getTableName());
      assertEquals(Set.of("name", "email"), search.getSearchableTables().getFirst().getFields().stream()
         .map(field -> field.getFieldName()).collect(Collectors.toSet()));
   }
}
