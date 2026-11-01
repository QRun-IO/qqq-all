/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import com.kingsrook.qqq.backend.core.model.audits.AuditsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The enterprise profile offers column statistics wherever the backend can aggregate. */
class EnterpriseColumnStatsTest
{
   @TempDir Path dataDirectory;

   @Test
   void queryStatsOnAggregatingBackendsOnly() throws Exception
   {
      QInstance instance = new QqqAllApplication(dataDirectory, "tcp://127.0.0.1:61616").defineQInstance();
      for(String tableName : new String[] {"customer", "order", "orderLine", "demoNote"})
      {
         assertFalse(hasQueryStats(instance, tableName), tableName + " before");
      }

      EnterpriseFeatures.enableColumnStatistics(instance);
      assertNotNull(instance.getProcess("columnStats"));
      assertNotNull(instance.getTable(instance.getProcess("columnStats").getTableName()));

      ///////////////////////////////////////////////////////////////////
      // H2 (customer, order), SQLite (orderLine) and memory (demoNote) //
      ///////////////////////////////////////////////////////////////////
      for(String tableName : new String[] {"customer", "order", "orderLine", "demoNote"})
      {
         assertTrue(hasQueryStats(instance, tableName), tableName);
         assertTrue(hasCapability(instance, tableName, Capability.TABLE_COUNT), tableName);
         assertTrue(hasCapability(instance, tableName, Capability.TABLE_EXPORT), tableName);
      }

      ////////////////////////////////////////////////////////////////
      // the filesystem (product) and API (apiCatalog) backends do  //
      // not aggregate, so they stay without statistics             //
      ////////////////////////////////////////////////////////////////
      assertFalse(hasQueryStats(instance, "product"));
      assertFalse(hasQueryStats(instance, "apiCatalog"));
   }

   @Test
   void dataTablesAreAuditedWhenAuditsAreInstalled() throws Exception
   {
      QInstance instance = new QqqAllApplication(dataDirectory, "tcp://127.0.0.1:61616").defineQInstance();
      new AuditsMetaDataProvider().defineAll(instance, "h2", null);
      EnterpriseFeatures.auditDataTables(instance);
      for(String tableName : new String[] {"customer", "order", "orderLine", "address"})
      {
         assertEquals(AuditLevel.FIELD, instance.getTable(tableName).getAuditRules().getAuditLevel(), tableName);
      }
   }

   private static boolean hasQueryStats(QInstance instance, String tableName)
   {
      return hasCapability(instance, tableName, Capability.QUERY_STATS);
   }

   private static boolean hasCapability(QInstance instance, String tableName, Capability capability)
   {
      QTableMetaData table = instance.getTable(tableName);
      QBackendMetaData backend = instance.getBackendForTable(tableName);
      return table.isCapabilityEnabled(backend, capability);
   }
}
