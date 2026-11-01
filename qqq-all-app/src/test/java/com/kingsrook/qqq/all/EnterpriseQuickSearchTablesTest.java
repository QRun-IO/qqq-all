/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.kingsrook.qbits.quicksearch.SearchableFieldConfig;
import com.kingsrook.qbits.quicksearch.SearchableTableConfig;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quick Search covers the demo and sample tables, with their text fields only. */
class EnterpriseQuickSearchTablesTest
{
   @TempDir Path dataDirectory;

   @Test
   void demoAndSampleTablesWithTheirTextFields() throws Exception
   {
      QInstance instance = new QqqAllApplication(dataDirectory, "tcp://127.0.0.1:61616").defineQInstance();
      ///////////////////////////////////////////////////////////////////////
      // a stand-in for a sample-app table: labelled, on the sample's      //
      // backend, with fields OpenSearch must not index as text            //
      ///////////////////////////////////////////////////////////////////////
      instance.addBackend(new QBackendMetaData().withName("rdbms").withBackendType(MemoryBackendModule.class));
      instance.addTable(new QTableMetaData().withName("countryState").withBackendName("rdbms").withPrimaryKeyField("id")
         .withRecordLabelFormat("%s").withRecordLabelFields("name")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("description", QFieldType.STRING))
         .withField(new QFieldMetaData("code", QFieldType.INTEGER))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME)));

      Map<String, List<String>> tables = EnterpriseFeatures.searchableTables(instance).stream()
         .collect(Collectors.toMap(SearchableTableConfig::getTableName,
            table -> table.getFields().stream().map(SearchableFieldConfig::getFieldName).toList()));

      assertEquals(List.of("name", "email"), tables.get("customer").stream().sorted((a, b) -> a.equals("name") ? -1 : 1).toList());
      assertTrue(tables.get("shipping_stateProvince").contains("name"));
      assertEquals(List.of("name", "description"), tables.get("countryState"));
      assertFalse(tables.containsKey("webhook"), "QBit admin tables on h2 stay out");
      assertFalse(tables.containsKey("product"), "the filesystem product table is not a demo search table");
   }
}
