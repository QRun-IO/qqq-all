/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.math.BigDecimal;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.AuthScope;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.security.FieldSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enterprise profile's columnStats process (core's step, QRun-IO/qqq#1367)
 * refuses statistics for a field the caller cannot read in full and tags its
 * results with the analysed table.
 */
class EnterpriseColumnStatsAccessTest
{
   private static final String KEY = "salaryViewer";

   private QInstance instance;

   @BeforeEach
   void setUp() throws QException
   {
      MemoryRecordStore.fullReset();
      instance = new QInstance();
      instance.registerAuthenticationProvider(AuthScope.instanceDefault(), new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK));
      instance.addBackend(new QBackendMetaData().withName("memory").withBackendType(MemoryBackendModule.class));
      instance.addSecurityKeyType(new QSecurityKeyType().withName(KEY));
      instance.addTable(new QTableMetaData().withName("employee").withBackendName("memory").withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("salary", QFieldType.DECIMAL).withLabel("Salary")
            .withFieldSecurityLock(new FieldSecurityLock().withSecurityKeyType(KEY)
               .withDefaultBehavior(FieldSecurityLock.Behavior.MASK).withOverrideValues(List.of(true)))));
      EnterpriseFeatures.enableColumnStatistics(instance);

      QContext.init(instance, new QSystemUserSession());
      new InsertAction().execute(new InsertInput("employee").withRecords(List.of(
         new QRecord().withValue("name", "Ada").withValue("salary", new BigDecimal("100")),
         new QRecord().withValue("name", "Grace").withValue("salary", new BigDecimal("200")))));
   }

   @AfterEach
   void tearDown()
   {
      QContext.clear();
      MemoryRecordStore.fullReset();
   }

   @Test
   void restrictedFieldIsRefusedWithoutTheTier() throws QException
   {
      QContext.init(instance, new QSession());
      QPermissionDeniedException denied = assertThrows(QPermissionDeniedException.class, () -> run("salary"));
      assertTrue(denied.getMessage() != null && !denied.getMessage().isBlank());
   }

   @Test
   void restrictedFieldIsAllowedWithTheTier() throws QException
   {
      QContext.init(instance, new QSession().withSecurityKeyValue(KEY, true));
      RunProcessOutput output = run("salary");
      assertTrue(output.getException().isEmpty(), () -> output.getException().get().getMessage());
      QRecord stats = (QRecord) output.getValues().get("statsRecord");
      assertEquals(2, stats.getValueInteger("count"));
      assertEquals("employee", stats.getTableName());
   }

   @Test
   void unrestrictedFieldIsAllowed() throws QException
   {
      QContext.init(instance, new QSession());
      RunProcessOutput output = run("name");
      assertTrue(output.getException().isEmpty(), () -> output.getException().get().getMessage());
   }

   private RunProcessOutput run(String fieldName) throws QException
   {
      RunProcessInput input = new RunProcessInput();
      input.setProcessName("columnStats");
      input.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
      input.addValue("tableName", "employee");
      input.addValue("fieldName", fieldName);
      return (new RunProcessAction().execute(input));
   }
}
