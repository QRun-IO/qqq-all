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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the shaded entry point and its HTTP API after packaging. */
class CoreJarIT
{
   @TempDir
   Path dataDirectory;

   @Test
   void packagedApplicationServesSeededData() throws Exception
   {
      Integer port;
      try(ServerSocket socket = new ServerSocket(0))
      {
         port = socket.getLocalPort();
      }

      Path jar = Path.of(System.getProperty("qqqAllJar"));
      Path log = dataDirectory.resolve("app.log");
      ProcessBuilder builder = new ProcessBuilder(
         Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString());
      builder.environment().put("QQQ_ALL_PORT", port.toString());
      builder.environment().put("QQQ_ALL_DATA_DIR", dataDirectory.resolve("data").toString());
      builder.redirectErrorStream(true).redirectOutput(log.toFile());
      Process process = builder.start();
      try
      {
         HttpClient client = HttpClient.newHttpClient();
         URI healthUri = URI.create("http://127.0.0.1:" + port + "/health");
         HttpResponse<String> health = awaitHealth(client, healthUri, process, log);
         assertEquals(200, health.statusCode());
         assertTrue(health.body().contains("\"UP\""));
         assertTrue(Files.readString(log).contains("{127.0.0.1:" + port + "}"),
            "Core profile must bind only to loopback");

         HttpRequest query = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/qqq/v1/table/customer/query"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
         HttpResponse<String> customers = client.send(query, HttpResponse.BodyHandlers.ofString());
         assertEquals(200, customers.statusCode(), customers.body());
         assertTrue(customers.body().contains("Ada Lovelace"));
      }
      finally
      {
         process.destroy();
         if(!process.waitFor(5, TimeUnit.SECONDS))
         {
            process.destroyForcibly();
            process.waitFor();
         }
      }
   }

   private static HttpResponse<String> awaitHealth(HttpClient client, URI uri, Process process, Path log) throws Exception
   {
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while(System.nanoTime() < deadline && process.isAlive())
      {
         try
         {
            return client.send(HttpRequest.newBuilder().uri(uri).build(), HttpResponse.BodyHandlers.ofString());
         }
         catch(java.net.ConnectException e)
         {
            Thread.sleep(100);
         }
      }
      throw new AssertionError("Packaged app did not become healthy. Output:\n" + Files.readString(log));
   }
}
