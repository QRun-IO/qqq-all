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
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qbits.workflows.definition.WorkflowsRegistry;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreQBitsTest
{
   @TempDir Path dataDirectory;

   @Test
   void coreExposesLocalQbitsAndOrderTrace() throws Exception
   {
      QInstance instance = new QqqAllApplication(dataDirectory, "tcp://127.0.0.1:61616").defineQInstance();
      new QInstanceValidator().validate(instance);
      Set<String> artifacts = instance.getQBits().values().stream()
         .map(qbit -> qbit.getArtifactId()).collect(Collectors.toSet());
      assertTrue(artifacts.containsAll(Set.of("customizable-table-views", "standard-process-trace",
         "webhooks", "workflows", "qbit-geo-data")));
      assertNotNull(instance.getTable("TableView"));
      assertNotNull(instance.getTable("processTrace"));
      assertNotNull(instance.getTable("webhook"));
      assertNotNull(instance.getTable("workflow"));
      assertNotNull(instance.getProcess("syncOrder").getProcessTracerCodeReference());
      assertNotNull(WebhooksRegistry.of(instance).getWebhookEventType("orderStored"));
      assertNotNull(WorkflowsRegistry.of(instance).getWorkflowType("RecordWorkflow"));
      assertNotNull(instance.getTable("address").getField("cityId").getPossibleValueSourceName());
      assertTrue(artifacts.stream().noneMatch("quick-search"::equals));
   }
}
