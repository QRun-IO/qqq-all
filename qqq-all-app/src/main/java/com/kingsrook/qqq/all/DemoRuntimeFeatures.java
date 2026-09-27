/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFunctionInputMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFunctionOutputMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.module.api.model.AuthorizationType;
import com.kingsrook.qqq.backend.module.api.model.metadata.APIBackendMetaData;
import com.kingsrook.qqq.backend.module.api.model.metadata.APITableBackendDetails;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;

/** Shared, local-only demonstrations included in both profiles. */
final class DemoRuntimeFeatures
{
   private DemoRuntimeFeatures()
   {
   }

   static void add(QInstance instance, int port)
   {
      instance.addBackend(new APIBackendMetaData().withName("demoApi")
         .withBaseUrl("http://127.0.0.1:" + port + "/demo")
         .withAuthorizationType(AuthorizationType.NONE).withContentType("application/json")
         .withActionUtil(new QCodeReference(DemoCatalogApi.class)));
      instance.addTable(new QTableMetaData().withName("apiCatalog").withLabel("API Catalog")
         .withBackendName("demoApi").withPrimaryKeyField("id")
         .withBackendDetails(new APITableBackendDetails().withTablePath("/catalog"))
         .withCapabilities(Capability.TABLE_QUERY, Capability.TABLE_GET, Capability.TABLE_COUNT)
         .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("unitPriceCents", QFieldType.INTEGER)));
      instance.addBackend(new QBackendMetaData().withName("transientMemory").withBackendType(MemoryBackendModule.class));
      instance.addTable(new QTableMetaData().withName("demoNote").withLabel("Demo Note (Transient)")
         .withBackendName("transientMemory").withPrimaryKeyField("id")
         .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("text", QFieldType.STRING)));
      instance.withRuntimeService(new QCodeReference(TransientNotesService.class));
      instance.addProcess(new QProcessMetaData().withName("calculateOrderTotal").withLabel("Calculate Order Total (JavaScript)")
         .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.HAS_ACCESS_PERMISSION))
         .withStep(new QBackendStepMetaData().withName("calculate")
            .withCode(new QCodeReference(CalculateOrderTotalStep.class))
            .withInputData(new QFunctionInputMetaData().withFieldList(List.of(
               new QFieldMetaData("quantity", QFieldType.INTEGER),
               new QFieldMetaData("unitPriceCents", QFieldType.INTEGER))))
            .withOutputMetaData(new QFunctionOutputMetaData().withFieldList(List.of(
               new QFieldMetaData("totalCents", QFieldType.INTEGER))))));
      QJavalinMetaData.ofOrWithNew(instance).withAdditionalRouteProviderReference(new QCodeReference(DemoCatalogRoutes.class));
   }
}
