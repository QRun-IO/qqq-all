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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.actions.processes.QProcessCallbackFactory;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.MockAuthenticationModule;
import com.kingsrook.qqq.esb.runtime.EsbTriggerState;
import com.kingsrook.qqq.esb.runtime.QEsbRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the assembled core application, including its real broker and backends. */
class CoreApplicationTest
{
   @TempDir
   Path dataDirectory;

   @Test
   void bootsQueriesEveryTableAndProcessesOrderEvents() throws Exception
   {
      Integer port;
      try(ServerSocket socket = new ServerSocket(0))
      {
         port = socket.getLocalPort();
      }

      QqqAllApplication.SyncOrderStep.reset();
      try(CoreRuntime runtime = CoreRuntime.launch(dataDirectory, port))
      {
         HttpClient client = HttpClient.newHttpClient();
         HttpResponse<String> health = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/health")).build(),
            HttpResponse.BodyHandlers.ofString());
         assertEquals(200, health.statusCode());
         assertTrue(health.body().contains("\"UP\""));

         HttpResponse<String> dashboard = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/")).build(),
            HttpResponse.BodyHandlers.ofString());
         assertEquals(200, dashboard.statusCode());
         assertTrue(dashboard.body().contains("html"));

         HttpResponse<String> demoRead = client.send(HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/qqq/v1/table/order/query"))
            .header("Cookie", "sessionId=" + DemoUsers.DEMO_SESSION)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build(), HttpResponse.BodyHandlers.ofString());
         assertEquals(200, demoRead.statusCode(), demoRead.body());
         HttpResponse<String> demoInsert = insertOrder(client, port, DemoUsers.DEMO_SESSION, "ORD-DENIED");
         assertEquals(403, demoInsert.statusCode());
         HttpResponse<String> adminInsert = insertOrder(client, port, DemoUsers.ADMIN_SESSION, "ORD-ADMIN");
         assertEquals(200, adminInsert.statusCode(), adminInsert.body());

         QContext.init(runtime.getLauncher().getQInstance(), new QSystemUserSession());
         try
         {
            MockAuthenticationModule mockAuth = new MockAuthenticationModule();
            assertEquals("core:admin", mockAuth.createSession(runtime.getLauncher().getQInstance(),
               Map.of("sessionId", DemoUsers.ADMIN_SESSION)).getUser().getIdReference());
            assertEquals("core:demo", mockAuth.createSession(runtime.getLauncher().getQInstance(),
               Map.of("sessionId", DemoUsers.DEMO_SESSION)).getUser().getIdReference());

            for(String table : List.of("customer", "order", "orderLine", "product", "address", "shipping_city"))
            {
               assertFalse(new QueryAction().execute(new QueryInput(table)).getRecords().isEmpty(), table);
            }
            assertEquals("Customer Directory", new QueryAction().execute(new QueryInput("TableView"))
               .getRecords().getFirst().getValueString("name"));
            assertEquals("Order Status Review", new QueryAction().execute(new QueryInput("workflow"))
               .getRecords().getFirst().getValueString("name"));

            waitFor(() -> QEsbRuntime.getInstance().getRunner("syncOrder.orderEvents") != null
               && QEsbRuntime.getInstance().getRunner("syncOrder.orderEvents").getState() == EsbTriggerState.RUNNING);
            new InsertAction().execute(new InsertInput("order").withRecords(List.of(
               new QRecord().withValue("orderNo", "ORD-1002")
                  .withValue("customerId", 2).withValue("status", "NEW"))));
            waitFor(() -> QqqAllApplication.SyncOrderStep.getRunCount() > 0);
            waitFor(() -> !new QueryAction().execute(new QueryInput("processTrace")).getRecords().isEmpty());
            waitFor(() -> !new QueryAction().execute(new QueryInput("webhookEvent")).getRecords().isEmpty());

            RunProcessInput sendWebhook = new RunProcessInput();
            sendWebhook.setProcessName("SendWebhookEvent");
            sendWebhook.addValue("webhookId", 1);
            sendWebhook.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
            new RunProcessAction().execute(sendWebhook);
            HttpResponse<String> receipts = client.send(HttpRequest.newBuilder()
               .uri(URI.create("http://127.0.0.1:" + port + "/demo/order-webhook-receipts"))
               .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, receipts.statusCode(), receipts.body());
            assertTrue(receipts.body().contains("ORD-ADMIN"), receipts.body());

            RunProcessInput workflow = new RunProcessInput();
            workflow.setProcessName("RunRecordWorkflow");
            workflow.setCallback(QProcessCallbackFactory.forPrimaryKey("id", 1));
            workflow.addValue("tableName", "order");
            workflow.addValue("workflowId", 1);
            workflow.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP);
            new RunProcessAction().execute(workflow);
            assertFalse(new QueryAction().execute(new QueryInput("workflowRunLog")).getRecords().isEmpty());
         }
         finally
         {
            QContext.clear();
         }
      }
   }

   private static HttpResponse<String> insertOrder(HttpClient client, Integer port, String sessionId, String orderNo)
      throws Exception
   {
      return client.send(HttpRequest.newBuilder()
         .uri(URI.create("http://127.0.0.1:" + port + "/data/order/"))
         .header("Cookie", "sessionId=" + sessionId)
         .header("Content-Type", "application/json")
         .POST(HttpRequest.BodyPublishers.ofString("{\"orderNo\":\"" + orderNo
            + "\",\"customerId\":1,\"status\":\"NEW\"}"))
         .build(), HttpResponse.BodyHandlers.ofString());
   }

   private static void waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception
   {
      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while(!condition.call() && System.nanoTime() < deadline)
      {
         Thread.sleep(50);
      }
      assertTrue(condition.call());
   }
}
