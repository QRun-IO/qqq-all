/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.GetAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.esb.connection.EsbConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercise the assembled demo against its real local HTTP backend. */
@Timeout(45)
class RuntimeFeaturesTest
{
   @TempDir
   Path directory;

   @AfterEach
   void closeOwnedBrokerConnections()
   {
      EsbConnectionManager.getInstance().closeAll();
   }

   @Test
   void apiCatalogQueriesGetsAndPaginates() throws Exception
   {
      try(CoreRuntime runtime = CoreRuntime.launch(directory, freePort()))
      {
         assertNotNull(runtime.getLauncher().getQInstance().getTable("apiCatalog"));
         QContext.init(runtime.getLauncher().getQInstance(), new QSystemUserSession());
         try
         {
            assertEquals(List.of(101, 102, 103), new QueryAction().execute(new QueryInput("apiCatalog")).getRecords()
               .stream().map(record -> record.getValueInteger("id")).toList());
            assertEquals(102, new QueryAction().execute(new QueryInput("apiCatalog")
               .withFilter(new QQueryFilter().withSkip(1).withLimit(1))).getRecords().getFirst().getValueInteger("id"));
            assertEquals("Shipping Box", GetAction.execute("apiCatalog", 103).getValueString("name"));
            assertNull(GetAction.execute("apiCatalog", 999));
            assertEquals(3, new CountAction().execute(new CountInput("apiCatalog")).getCount());
            var exactId = new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, 103));
            assertEquals(1, new CountAction().execute(new CountInput("apiCatalog").withFilter(exactId)).getCount());
            assertEquals(List.of(103), new QueryAction().execute(new QueryInput("apiCatalog").withFilter(exactId)).getRecords()
               .stream().map(record -> record.getValueInteger("id")).toList());
            assertThrows(QException.class, () -> new QueryAction().execute(new QueryInput("apiCatalog")
               .withFilter(new QQueryFilter(new QFilterCriteria("name", QCriteriaOperator.EQUALS, "Starter Kit")))));
         }
         finally
         {
            QContext.clear();
         }
      }
   }

   @Test
   void calculationUsesRealJavaScriptAndRequiresPermission() throws Exception
   {
      int port = freePort();
      try(CoreRuntime runtime = CoreRuntime.launch(directory, port); HttpClient client = HttpClient.newHttpClient())
      {
         assertNotNull(runtime.getLauncher().getQInstance().getProcess("calculateOrderTotal"));
         HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
            + "/processes/calculateOrderTotal/run?quantity=3&unitPriceCents=1250"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"));
         assertEquals(403, client.send(request.header("Cookie", "sessionId=" + DemoUsers.DEMO_SESSION).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
         var allowed = client.send(request.setHeader("Cookie", "sessionId=" + DemoUsers.ADMIN_SESSION).build(), HttpResponse.BodyHandlers.ofString());
         assertEquals(200, allowed.statusCode(), allowed.body());
         assertEquals(3750, new org.json.JSONObject(allowed.body()).getJSONObject("values").getInt("totalCents"));
      }
   }

   @Test
   void calculationRejectsMissingNegativeAndExcessiveInputs()
   {
      for(Integer quantity : java.util.Arrays.asList(null, 0, -1, 1001))
      {
         var input = new RunBackendStepInput().addValue("quantity", quantity).addValue("unitPriceCents", 1250);
         assertThrows(QException.class, () -> new CalculateOrderTotalStep().run(input, new RunBackendStepOutput()));
      }
      for(Integer price : java.util.Arrays.asList(null, -1, 1_000_001))
      {
         var input = new RunBackendStepInput().addValue("quantity", 3).addValue("unitPriceCents", price);
         assertThrows(QException.class, () -> new CalculateOrderTotalStep().run(input, new RunBackendStepOutput()));
      }
   }

   @Test
   void memoryNotesResetAcrossRuntimeRestart() throws Exception
   {
      try(CoreRuntime runtime = CoreRuntime.launch(directory, freePort()))
      {
         assertNotNull(runtime.getLauncher().getQInstance().getTable("demoNote"));
         QContext.init(runtime.getLauncher().getQInstance(), new QSystemUserSession());
         try
         {
            assertEquals("Transient demo note", GetAction.execute("demoNote", 1).getValueString("text"));
            new UpdateAction().execute(new UpdateInput("demoNote").withRecords(List.of(new QRecord().withValue("id", 1).withValue("text", "Only this run"))));
            assertEquals("Only this run", GetAction.execute("demoNote", 1).getValueString("text"));
         }
         finally
         {
            QContext.clear();
         }
      }
      closeOwnedBrokerConnections();
      try(CoreRuntime runtime = CoreRuntime.launch(directory, freePort()))
      {
         QContext.init(runtime.getLauncher().getQInstance(), new QSystemUserSession());
         try
         {
            assertEquals("Transient demo note", GetAction.execute("demoNote", 1).getValueString("text"));
         }
         finally
         {
            QContext.clear();
         }
      }
   }

   static int freePort() throws Exception
   {
      try(ServerSocket socket = new ServerSocket(0))
      {
         return socket.getLocalPort();
      }
   }
}
