/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import com.kingsrook.qqq.middleware.javalin.QApplicationLauncher;
import com.kingsrook.qqq.middleware.javalin.QApplicationLauncherConfig;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

/** Owns the embedded broker and launcher as one closeable core profile. */
final class CoreRuntime implements AutoCloseable
{
   private final EmbeddedActiveMQ    broker;
   private final QApplicationLauncher launcher;
   private final Thread              shutdownHook;

   private CoreRuntime(EmbeddedActiveMQ broker, QApplicationLauncher launcher)
   {
      this.broker = broker;
      this.launcher = launcher;
      this.shutdownHook = new Thread(this::close, "qqq-all-core-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdownHook);
   }

   static CoreRuntime launch(Path dataDirectory, Integer port) throws Exception
   {
      return launch(dataDirectory, port, "127.0.0.1");
   }

   static CoreRuntime launch(Path dataDirectory, Integer port, String bindHost) throws Exception
   {
      CoreData.seed(dataDirectory);
      Integer brokerPort = findFreePort();
      Path brokerDirectory = dataDirectory.resolve("artemis").toAbsolutePath();
      String brokerUrl = "tcp://127.0.0.1:" + brokerPort;
      Configuration configuration = new ConfigurationImpl()
         .setPersistenceEnabled(false).setSecurityEnabled(false).setJMXManagementEnabled(false)
         .setBindingsDirectory(brokerDirectory.resolve("bindings").toString())
         .setJournalDirectory(brokerDirectory.resolve("journal").toString())
         .setPagingDirectory(brokerDirectory.resolve("paging").toString())
         .setLargeMessagesDirectory(brokerDirectory.resolve("large-messages").toString())
         .addAcceptorConfiguration("tcp", brokerUrl);
      EmbeddedActiveMQ broker = new EmbeddedActiveMQ().setConfiguration(configuration);
      broker.start();
      try
      {
         QApplicationLauncher launcher = QApplicationLauncher.run(
            new QqqAllApplication(dataDirectory, brokerUrl),
            new QApplicationLauncherConfig().withRegisterShutdownHook(false)
               .withServerCustomizer(server -> server.withPort(port)
                  .withJavalinConfigCustomizer(config -> config.jetty.host = bindHost)));
         return new CoreRuntime(broker, launcher);
      }
      catch(Exception | LinkageError e)
      {
         broker.stop();
         throw e;
      }
   }

   private static Integer findFreePort() throws IOException
   {
      try(ServerSocket socket = new ServerSocket(0))
      {
         return socket.getLocalPort();
      }
   }

   QApplicationLauncher getLauncher()
   {
      return launcher;
   }

   @Override
   public void close()
   {
      launcher.stop();
      try
      {
         broker.stop();
      }
      catch(Exception e)
      {
         throw new IllegalStateException("Could not stop embedded Artemis", e);
      }
      if(Thread.currentThread() != shutdownHook)
      {
         Runtime.getRuntime().removeShutdownHook(shutdownHook);
      }
   }
}
