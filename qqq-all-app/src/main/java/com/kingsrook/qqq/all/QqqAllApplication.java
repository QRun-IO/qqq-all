/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerHelper;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.AuthScope;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.processes.tracing.ProcessTracerKeyRecordMessage;
import com.kingsrook.qqq.backend.module.filesystem.base.model.metadata.Cardinality;
import com.kingsrook.qqq.backend.module.filesystem.base.model.metadata.RecordFormat;
import com.kingsrook.qqq.backend.module.filesystem.local.model.metadata.FilesystemBackendMetaData;
import com.kingsrook.qqq.backend.module.filesystem.local.model.metadata.FilesystemTableBackendDetails;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import com.kingsrook.qqq.backend.module.sqlite.model.metadata.SQLiteBackendMetaData;
import com.kingsrook.qqq.esb.envelope.EsbEvent;
import com.kingsrook.qqq.esb.api.EsbJavalinMetaDataProducer;
import com.kingsrook.qqq.esb.metadata.EsbAppMetaDataProducer;
import com.kingsrook.qqq.esb.model.EsbDestinationType;
import com.kingsrook.qqq.esb.model.EsbInstanceMetaData;
import com.kingsrook.qqq.esb.model.EsbProcessMetaData;
import com.kingsrook.qqq.esb.model.EsbProviderType;
import com.kingsrook.qqq.esb.model.EsbTableEvent;
import com.kingsrook.qqq.esb.model.EsbTableMetaData;
import com.kingsrook.qqq.esb.model.EsbTablePublication;
import com.kingsrook.qqq.esb.model.EsbTrigger;
import com.kingsrook.qqq.esb.model.QEsbDestinationMetaData;
import com.kingsrook.qqq.esb.model.QEsbProviderMetaData;
import com.kingsrook.qqq.esb.runtime.EsbTriggerHandler;
import com.kingsrook.qqq.middleware.health.JavalinHealthRouteProvider;
import com.kingsrook.qqq.middleware.health.indicators.MemoryHealthIndicator;
import com.kingsrook.qqq.middleware.health.model.metadata.HealthCheckMetaData;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;

/** The self-contained core QQQ reference application. */
public final class QqqAllApplication extends AbstractQQQApplication
{
   private final Path   dataDirectory;
   private final String brokerUrl;
   private final boolean includeDemoQbits;

   public QqqAllApplication(Path dataDirectory, String brokerUrl)
   {
      this(dataDirectory, brokerUrl, true);
   }

   QqqAllApplication(Path dataDirectory, String brokerUrl, boolean includeDemoQbits)
   {
      this.dataDirectory = dataDirectory;
      this.brokerUrl = brokerUrl;
      this.includeDemoQbits = includeDemoQbits;
   }

   public static void main(String[] args) throws Exception
   {
      Path dataDirectory = Path.of(System.getenv().getOrDefault("QQQ_ALL_DATA_DIR", "./data"));
      Integer port = Integer.valueOf(System.getenv().getOrDefault("QQQ_ALL_PORT", "8080"));
      CoreRuntime.launch(dataDirectory, port);
   }

   @Override
   public QInstance defineQInstance() throws QException
   {
      QInstance instance = new QInstance().withFailOnMetaDataProducerError(true);
      instance.registerAuthenticationProvider(AuthScope.instanceDefault(), new QAuthenticationMetaData()
         .withName("mock").withType(QAuthenticationType.MOCK)
         .withCustomizer(new QCodeReference(DemoUsers.class)));

      instance.addBackend(new RDBMSBackendMetaData()
         .withName("h2").withVendor("h2").withHostName("file")
         .withDatabaseName(CoreData.h2DatabaseName(dataDirectory)).withUsername("sa"));
      instance.addBackend(new SQLiteBackendMetaData().withName("sqlite")
         .withPath(CoreData.sqlitePath(dataDirectory).toAbsolutePath().toString()));
      instance.addBackend(new FilesystemBackendMetaData().withName("files")
         .withBasePath(dataDirectory.resolve("files").toAbsolutePath().toString()));

      instance.addTable(new QTableMetaData().withName("customer").withLabel("Customer")
         .withBackendName("h2").withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("email", QFieldType.STRING)));
      instance.addTable(new QTableMetaData().withName("order").withLabel("Order")
         .withBackendName("h2").withBackendDetails(new RDBMSTableBackendDetails().withTableName("orders"))
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("orderNo", QFieldType.STRING).withBackendName("order_no"))
         .withField(new QFieldMetaData("customerId", QFieldType.INTEGER).withBackendName("customer_id"))
         .withField(new QFieldMetaData("status", QFieldType.STRING))
         .withSupplementalMetaData(new EsbTableMetaData().withPublication(new EsbTablePublication()
            .withDestinationName("orderEvents").withEvents(List.of(EsbTableEvent.INSERT, EsbTableEvent.UPDATE)))));
      instance.addTable(new QTableMetaData().withName("orderLine").withLabel("Order Line")
         .withBackendName("sqlite").withBackendDetails(new RDBMSTableBackendDetails().withTableName("order_line"))
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("orderId", QFieldType.INTEGER).withBackendName("order_id"))
         .withField(new QFieldMetaData("productId", QFieldType.INTEGER).withBackendName("product_id"))
         .withField(new QFieldMetaData("quantity", QFieldType.INTEGER)));
      instance.addTable(new QTableMetaData().withName("product").withLabel("Product")
         .withBackendName("files").withPrimaryKeyField("id")
         .withBackendDetails(new FilesystemTableBackendDetails()
            .withBasePath("products").withRecordFormat(RecordFormat.JSON)
            .withCardinality(Cardinality.MANY).withGlob("*.json"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("price", QFieldType.DECIMAL)));

      instance.addProcess(new QProcessMetaData().withName("syncOrder").withLabel("Sync Order")
         .withTableName("order")
         .withStep(new QBackendStepMetaData().withName("sync")
            .withCode(new QCodeReference(SyncOrderStep.class)))
         .withSupplementalMetaData(new EsbProcessMetaData()
            .withTrigger(new EsbTrigger().withDestinationName("orderEvents"))));

      EsbInstanceMetaData.of(instance).withInstanceName("qqqAll")
         .withProvider(new QEsbProviderMetaData().withName("artemis")
            .withType(EsbProviderType.ACTIVEMQ_ARTEMIS).withUrl(brokerUrl))
         .withDestination(new QEsbDestinationMetaData().withName("orderEvents")
            .withType(EsbDestinationType.TOPIC).withProviderName("artemis")
            .withDestinationName("qqq.all.orderEvents"));
      instance.withRuntimeService(new QCodeReference(CoreEsbRuntimeService.class));
      MetaDataProducerHelper.processAllMetaDataProducersInPackage(instance, EsbAppMetaDataProducer.class.getPackageName());
      new EsbJavalinMetaDataProducer().produce(instance);

      instance.withSupplementalMetaData(new HealthCheckMetaData().withEnabled(true)
         .withEndpointPath("/health").withIndicators(List.of(new MemoryHealthIndicator().withThreshold(99))));
      QJavalinMetaData.ofOrWithNew(instance)
         .withAdditionalRouteProviderReference(new QCodeReference(JavalinHealthRouteProvider.class));
      if(includeDemoQbits)
      {
         DemoQBits.addCore(instance, "h2");
         DemoUsers.protectDemoTables(instance);
         QJavalinMetaData.ofOrWithNew(instance)
            .withAdditionalRouteProviderReference(new QCodeReference(DemoOrderWebhookReceiver.class));
      }
      return instance;
   }

   /** A visible effect for the demo and for the ESB integration test. */
   public static final class SyncOrderStep implements BackendStep
   {
      private static final AtomicInteger RUN_COUNT = new AtomicInteger();

      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         if(input.getValue(EsbTriggerHandler.VALUE_ESB_MESSAGES) instanceof List<?> messages)
         {
            for(Object message : messages)
            {
               if(message instanceof EsbEvent event
                  && event.getSource().endsWith("/table/order") && event.getSubject() != null)
               {
                  Integer orderId = Integer.valueOf(event.getSubject());
                  input.getProcessTracer().ifPresent(tracer -> tracer.handleMessage(input,
                     new ProcessTracerKeyRecordMessage("order", orderId)));
               }
            }
         }
         RUN_COUNT.incrementAndGet();
      }

      public static Integer getRunCount()
      {
         return RUN_COUNT.get();
      }

      public static void reset()
      {
         RUN_COUNT.set(0);
      }
   }
}
