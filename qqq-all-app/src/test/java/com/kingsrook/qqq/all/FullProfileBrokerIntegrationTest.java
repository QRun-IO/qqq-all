/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.esb.management.ArtemisJolokiaAdapter;
import com.kingsrook.qqq.esb.management.EsbBrokerNames;
import com.kingsrook.qqq.esb.management.RabbitManagementAdapter;
import com.kingsrook.qqq.esb.model.EsbInstanceMetaData;
import com.kingsrook.qqq.esb.model.EsbProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in smoke test against the local full Compose brokers and their management APIs. */
@EnabledIfEnvironmentVariable(named = "QQQ_ALL_COMPOSE_BROKERS", matches = "true")
class FullProfileBrokerIntegrationTest
{
   @TempDir
   Path dataDirectory;

   @Test
   void managesQueuesWithFullProfileCredentials() throws Exception
   {
      Map<String, String> environment = FullProfileTest.environment();
      environment.put("QQQ_ALL_ARTEMIS_USER", System.getenv("ARTEMIS_USER"));
      environment.put("QQQ_ALL_ARTEMIS_PASSWORD", System.getenv("ARTEMIS_PASSWORD"));
      environment.put("QQQ_ALL_ARTEMIS_MANAGEMENT_URL", "http://127.0.0.1:8161");
      environment.put("QQQ_ALL_RABBITMQ_USER", System.getenv("RABBITMQ_USER"));
      environment.put("QQQ_ALL_RABBITMQ_PASSWORD", System.getenv("RABBITMQ_PASSWORD"));
      environment.put("QQQ_ALL_RABBITMQ_MANAGEMENT_URL", "http://127.0.0.1:15672");
      QInstance instance = new FullProfileApplication(dataDirectory, FullProfileSettings.from(environment)).defineQInstance();
      EsbInstanceMetaData esb = EsbInstanceMetaData.of(instance);

      ArtemisJolokiaAdapter artemis = new ArtemisJolokiaAdapter(esb.getProvider("artemis"));
      String subscription = EsbBrokerNames.subscriptionQueue(EsbProviderType.ACTIVEMQ_ARTEMIS,
         "qqq.all.orderEvents", "syncOrder.qqq.all.orderEvents");
      assertTrue(artemis.getQueueInfo(subscription).isPresent());
      try
      {
         artemis.pauseQueue(subscription);
         assertTrue(artemis.getQueueInfo(subscription).orElseThrow().paused());
      }
      finally
      {
         artemis.resumeQueue(subscription);
      }
      assertFalse(artemis.getQueueInfo(subscription).orElseThrow().paused());

      RabbitManagementAdapter rabbit = new RabbitManagementAdapter(esb.getProvider("rabbitmq"));
      String queueName = "qqq.all.c6.managementProbe." + UUID.randomUUID();
      String queueUrl = "http://127.0.0.1:15672/api/queues/%2F/" + queueName;
      String credentials = environment.get("QQQ_ALL_RABBITMQ_USER") + ":" + environment.get("QQQ_ALL_RABBITMQ_PASSWORD");
      String authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
      try(HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build())
      {
         HttpRequest create = HttpRequest.newBuilder(URI.create(queueUrl))
            .header("Authorization", authorization).header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"durable\":true,\"auto_delete\":false,\"arguments\":{}}"))
            .build();
         assertEquals(201, client.send(create, HttpResponse.BodyHandlers.discarding()).statusCode());
         try
         {
            assertTrue(rabbit.getQueueInfo(queueName).isPresent());
            assertEquals(0L, rabbit.purgeQueue(queueName));
         }
         finally
         {
            HttpRequest delete = HttpRequest.newBuilder(URI.create(queueUrl))
               .header("Authorization", authorization).DELETE().build();
            assertEquals(204, client.send(delete, HttpResponse.BodyHandlers.discarding()).statusCode());
         }
      }
   }
}
