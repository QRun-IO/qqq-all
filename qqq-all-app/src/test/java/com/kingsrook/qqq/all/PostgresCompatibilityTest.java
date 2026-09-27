/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in native PostgreSQL oracle; owns only its uniquely named table. */
@EnabledIfEnvironmentVariable(named = "QQQ_ALL_TEST_POSTGRES_PORT", matches = "[0-9]+")
@Timeout(30)
class PostgresCompatibilityTest
{
   @TempDir
   Path directory;

   @Test
   void nativeStrategyPreservesGeneratedIdsNullsAndUtcTimestamps() throws Exception
   {
      var environment = FullProfileTest.environment();
      environment.put("QQQ_ALL_POSTGRES_HOST", "127.0.0.1");
      environment.put("QQQ_ALL_POSTGRES_PORT", System.getenv("QQQ_ALL_TEST_POSTGRES_PORT"));
      environment.put("QQQ_ALL_POSTGRES_DATABASE", "qqq");
      environment.put("QQQ_ALL_POSTGRES_USER", "qqq");
      environment.put("QQQ_ALL_POSTGRES_PASSWORD", System.getenv("QQQ_ALL_TEST_POSTGRES_PASSWORD"));
      var instance = new FullProfileApplication(directory, FullProfileSettings.from(environment)).defineQInstance();
      var backend = (RDBMSBackendMetaData) instance.getBackend("postgres");
      String table = "compat_" + UUID.randomUUID().toString().replace("-", "");
      instance.addTable(new QTableMetaData().withName("compatibility").withBackendName("postgres")
         .withBackendDetails(new RDBMSTableBackendDetails().withTableName(table)).withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("value", QFieldType.STRING))
         .withField(new QFieldMetaData("created", QFieldType.DATE_TIME)));
      instance.addTable(new QTableMetaData().withName("compatibilityDefault").withBackendName("postgres")
         .withBackendDetails(new RDBMSTableBackendDetails().withTableName(table)).withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER)));
      var previous = QContext.capture();
      try(var connection = DriverManager.getConnection(ConnectionManager.getJdbcUrl(backend), backend.getUsername(), backend.getPassword());
          var statement = connection.createStatement())
      {
         statement.execute("CREATE TABLE " + table + " (id SERIAL PRIMARY KEY, value TEXT, created TIMESTAMP)");
         try
         {
            QContext.init(instance, new QSystemUserSession());
            var empty = new InsertAction().execute(new InsertInput("compatibilityDefault").withRecords(List.of(new QRecord()))).getRecords().getFirst();
            assertNotNull(empty.getValueInteger("id"));
            Instant timestamp = Instant.parse("2026-09-25T12:34:56Z");
            var row = new InsertAction().execute(new InsertInput("compatibility").withRecords(List.of(
               new QRecord().withValue("value", "demo").withValue("created", timestamp)))).getRecords().getFirst();
            assertTrue(row.getValueInteger("id") > empty.getValueInteger("id"));
            new UpdateAction().execute(new UpdateInput("compatibility").withRecords(List.of(
               new QRecord().withValue("id", row.getValueInteger("id")).withValue("value", null))));
            assertNull(GetAction.execute("compatibility", row.getValueInteger("id")).getValue("value"));
            try(var result = statement.executeQuery("SELECT value, created FROM " + table + " WHERE id = " + row.getValueInteger("id")))
            {
               assertTrue(result.next());
               assertNull(result.getString(1));
               assertEquals(timestamp, result.getTimestamp(2, java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))).toInstant());
               assertFalse(result.next());
            }
         }
         finally
         {
            statement.execute("DROP TABLE " + table);
         }
      }
      finally
      {
         QContext.init(previous);
      }
   }
}
