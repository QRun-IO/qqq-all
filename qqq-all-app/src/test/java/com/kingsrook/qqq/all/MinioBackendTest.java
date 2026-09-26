/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import com.kingsrook.qqq.backend.core.modules.backend.QBackendModuleDispatcher;
import com.kingsrook.qqq.backend.module.filesystem.s3.actions.AbstractS3Action;
import com.kingsrook.qqq.backend.module.filesystem.s3.utils.S3Utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioBackendTest
{
   @Test
   void usesConfiguredEndpointForAllFilesystemActions() throws Exception
   {
      MinioBackendMetaData backend = new MinioBackendMetaData("http://minio.example.test:9000");
      backend.setName("s3");
      backend.setBucketName("qqq-demo");
      backend.setAccessKey("fixture");
      backend.setSecretKey("fixture");
      backend.setRegion("us-east-1");
      MinioBackendModule module = assertInstanceOf(MinioBackendModule.class,
         new QBackendModuleDispatcher().getQBackendModule(backend));
      assertUrl((AbstractS3Action) module.getActionBase(), backend);
      assertUrl((AbstractS3Action) module.getQueryInterface(), backend);
      assertUrl((AbstractS3Action) module.getInsertInterface(), backend);
      assertUrl((AbstractS3Action) module.getCountInterface(), backend);
      assertUrl((AbstractS3Action) module.getDeleteInterface(), backend);
      assertUrl((AbstractS3Action) module.getStorageInterface(), backend);
   }

   private static void assertUrl(AbstractS3Action action, MinioBackendMetaData backend) throws Exception
   {
      action.preAction(backend);
      S3Utils utils = utils(action);
      assertEquals("http://minio.example.test:9000/qqq-demo/example.json",
         utils.getAmazonS3().getUrl("qqq-demo", "example.json").toString());
   }

   @Test
   void queryAndWriteUseLocalS3Endpoint() throws Exception
   {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> written = new AtomicReference<>();
      server.createContext("/", exchange ->
      {
         byte[] response;
         if("PUT".equals(exchange.getRequestMethod()))
         {
            written.set(exchange.getRequestURI().getPath() + ":" +
               new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("ETag", "\"fixture-etag\"");
            response = new byte[0];
         }
         else
         {
            String xml = "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
               + "<Name>qqq-demo</Name><IsTruncated>false</IsTruncated>"
               + "<Contents><Key>documents/example.json</Key><Size>2</Size>"
               + "<LastModified>2026-01-01T00:00:00.000Z</LastModified></Contents></ListBucketResult>";
            response = xml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
         }
         exchange.sendResponseHeaders(200, response.length);
         exchange.getResponseBody().write(response);
         exchange.close();
      });
      server.start();
      try
      {
         MinioBackendMetaData backend = new MinioBackendMetaData("http://127.0.0.1:" + server.getAddress().getPort());
         backend.setName("s3");
         backend.setBucketName("qqq-demo");
         backend.setAccessKey("fixture");
         backend.setSecretKey("fixture");
         backend.setRegion("us-east-1");
         MinioBackendModule module = new MinioBackendModule();

         AbstractS3Action query = (AbstractS3Action) module.getQueryInterface();
         query.preAction(backend);
         assertEquals("documents/example.json", utils(query).getAmazonS3()
            .listObjectsV2("qqq-demo").getObjectSummaries().getFirst().getKey());

         AbstractS3Action insert = (AbstractS3Action) module.getInsertInterface();
         insert.preAction(backend);
         utils(insert).writeFile("qqq-demo", "documents/new.json", "{}".getBytes(StandardCharsets.UTF_8), "application/json");
         assertTrue(written.get().startsWith("/qqq-demo/documents/new.json:"));
         assertTrue(written.get().contains("\r\n{}\r\n"));
      }
      finally
      {
         server.stop(0);
      }
   }

   private static S3Utils utils(AbstractS3Action action) throws Exception
   {
      var accessor = AbstractS3Action.class.getDeclaredMethod("getS3Utils");
      accessor.setAccessible(true);
      return (S3Utils) accessor.invoke(action);
   }
}
