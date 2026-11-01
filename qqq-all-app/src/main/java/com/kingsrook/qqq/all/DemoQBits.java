/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.List;
import com.kingsrook.qbits.customizabletableviews.CustomizableTableViewsQBitConfig;
import com.kingsrook.qbits.customizabletableviews.CustomizableTableViewsQBitProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchStartupMode;
import com.kingsrook.qbits.quicksearch.SearchableFieldConfig;
import com.kingsrook.qbits.sftpdataintegration.SFTPDataIntegrationQBitConfig;
import com.kingsrook.qbits.sftpdataintegration.SFTPDataIntegrationQBitProducer;
import com.kingsrook.qbits.geodata.GeoDataQBitConfig;
import com.kingsrook.qbits.geodata.GeoDataQBitProducer;
import com.kingsrook.qbits.standardprocesstrace.StandardProcessTraceQBitConfig;
import com.kingsrook.qbits.standardprocesstrace.StandardProcessTraceQBitProducer;
import com.kingsrook.qbits.standardprocesstrace.utils.StandardProcessTracer;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksQBitProducer;
import com.kingsrook.qbits.webhooks.model.WebhookEventCategory;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qbits.workflows.WorkflowsQBitConfig;
import com.kingsrook.qbits.workflows.WorkflowsQBitProducer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.ProvidedOrSuppliedTableConfig;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.TablesPossibleValueSourceMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.processes.QQQProcessesMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.tables.QQQTablesMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.savedbulkloadprofiles.SavedBulkLoadProfileMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.savedreports.SavedReportsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.scheduledjobs.ScheduledJobsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.common.TimeZonePossibleValueSourceMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.simple.SimpleSchedulerMetaData;
import com.kingsrook.qqq.backend.module.filesystem.base.model.metadata.Cardinality;
import com.kingsrook.qqq.backend.module.filesystem.sftp.model.metadata.SFTPTableBackendDetails;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;

/** Registers the locally usable qbits against the demo's relational backend. */
final class DemoQBits
{
   private DemoQBits()
   {
   }

   static void addCore(QInstance instance, String backendName) throws QException
   {
      new QQQTablesMetaDataProvider().defineAll(instance, backendName, backendName, null);
      new QQQProcessesMetaDataProvider().defineAll(instance, backendName, backendName, null);
      instance.addScheduler(new SimpleSchedulerMetaData().withName("demoScheduler"));
      new ScheduledJobsMetaDataProvider().defineAll(instance, backendName, null);
      instance.addPossibleValueSource(new TimeZonePossibleValueSourceMetaDataProvider().produce());
      instance.addPossibleValueSource(TablesPossibleValueSourceMetaDataProvider.defineTablesPossibleValueSource(instance));
      instance.addPossibleValueSource(new QPossibleValueSource().withName("apiName")
         .withType(QPossibleValueSourceType.ENUM).withIdType(QFieldType.STRING)
         .withEnumValues(List.of(new QPossibleValue<>("none", "No API in this demo"))));
      instance.addPossibleValueSource(new QPossibleValueSource().withName("apiVersion")
         .withType(QPossibleValueSourceType.ENUM).withIdType(QFieldType.STRING)
         .withEnumValues(List.of(new QPossibleValue<>("none", "No API version"))));
      if(instance.getPossibleValueSource("role") == null)
      {
         instance.addPossibleValueSource(new QPossibleValueSource().withName("role")
            .withType(QPossibleValueSourceType.ENUM).withIdType(QFieldType.INTEGER)
            .withEnumValues(List.of(new QPossibleValue<>(0, "No role"))));
      }

      MetaDataCustomizerInterface<QTableMetaData> tableCustomizer =
         (i, table) ->
         {
            table.setBackendName(backendName);
            table.setBackendDetails(new RDBMSTableBackendDetails()
               .withTableName(QInstanceEnricher.inferBackendName(table.getName())));
            QInstanceEnricher.setInferredFieldBackendNames(table);
            return table;
         };

      new CustomizableTableViewsQBitProducer()
         .withQBitConfig(new CustomizableTableViewsQBitConfig()
            .withTableMetaDataCustomizer(tableCustomizer))
         .produce(instance).addSelfToInstance(instance);
      CustomizableTableViewsQBitProducer.activateTableMetaDataPersonalizerInterface(instance);

      StandardProcessTraceQBitConfig traceConfig = new StandardProcessTraceQBitConfig()
         .withTableMetaDataCustomizer(tableCustomizer);
      StandardProcessTracer.setStandardProcessTraceQBitConfig(traceConfig);
      new StandardProcessTraceQBitProducer()
         .withStandardProcessTraceQBitConfig(traceConfig)
         .produce(instance).addSelfToInstance(instance);
      instance.getProcess("syncOrder").setProcessTracerCodeReference(new QCodeReference(StandardProcessTracer.class));

      new WebhooksQBitProducer()
         .withQBitConfig(new WebhooksQBitConfig().withDefaultBackendNameForTables(backendName)
            .withSchedulerName("demoScheduler")
            .withTableMetaDataCustomizer(tableCustomizer))
         .produce(instance).addSelfToInstance(instance);
      WebhooksRegistry.ofOrWithNew(instance).registerWebhookEventType(new WebhookEventType()
         .withName("orderStored").withLabel("Order Stored")
         .withCategory(WebhookEventCategory.STORE).withTableName("order")
         .withCustomizer(new QCodeReference(DemoOrderWebhookContent.class)));

      moveTestEventFormAfterPreview(instance);

      new WorkflowsQBitProducer()
         .withQBitConfig(new WorkflowsQBitConfig().withTableMetaDataCustomizer(tableCustomizer))
         .produce(instance).addSelfToInstance(instance);

      new GeoDataQBitProducer()
         .withConfig(new GeoDataQBitConfig().withBackendName(backendName).withTableNamePrefix("shipping"))
         .produce(instance, "shipping-geo");
      instance.addTable(new QTableMetaData().withName("shipping_country").withLabel("Country")
         .withBackendName(backendName).withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("alpha2Code", QFieldType.STRING).withBackendName("alpha2_code")));
      instance.addTable(new QTableMetaData().withName("shipping_stateProvince").withLabel("State Province")
         .withBackendName(backendName).withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("countryId", QFieldType.INTEGER).withBackendName("country_id")
            .withPossibleValueSourceName("shipping_country")));
      instance.addTable(new QTableMetaData().withName("shipping_city").withLabel("City")
         .withBackendName(backendName).withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("stateProvinceId", QFieldType.INTEGER).withBackendName("state_province_id")
            .withPossibleValueSourceName("shipping_stateProvince"))
         .withField(new QFieldMetaData("latitude", QFieldType.DECIMAL))
         .withField(new QFieldMetaData("longitude", QFieldType.DECIMAL)));
      instance.addTable(new QTableMetaData().withName("address").withLabel("Address")
         .withBackendName(backendName).withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("customerId", QFieldType.INTEGER).withBackendName("customer_id"))
         .withField(new QFieldMetaData("line1", QFieldType.STRING))
         .withField(new QFieldMetaData("cityId", QFieldType.INTEGER).withBackendName("city_id")
            .withPossibleValueSourceName("shipping_city")));
   }

   static void addFull(QInstance instance, FullProfileSettings settings) throws QException
   {
      QuickSearchQBitConfig searchConfig = new QuickSearchQBitConfig()
         .withBackendName("postgres")
         .withOpensearchHost(settings.get("OPENSEARCH_HOST"))
         .withOpensearchPort(settings.port("OPENSEARCH_PORT", "9200"))
         .withOpensearchIndexName(settings.getOrDefault("OPENSEARCH_INDEX", "qqq-all-customers"))
         .withOpensearchUsername(settings.get("OPENSEARCH_USER"))
         .withOpensearchPassword(settings.get("OPENSEARCH_PASSWORD"))
         .withUseSsl(Boolean.parseBoolean(settings.getOrDefault("OPENSEARCH_SSL", "false")))
         // quick search 1.x connects at startup unless degraded; 0.2.x did not (the enterprise profile bumped it)
         .withStartupMode(QuickSearchStartupMode.DEGRADED)
         .withSearchableTable("customer", List.of(new SearchableFieldConfig("name"),
            new SearchableFieldConfig("email")));
      ////////////////////////////////////////////////////////////////////
      // quick search 1.x registers its own QBitMetaData when produced   //
      ////////////////////////////////////////////////////////////////////
      new QuickSearchQBitProducer().withConfig(searchConfig).produce(instance);

      new SavedBulkLoadProfileMetaDataProvider().defineAll(instance, "postgres", null);
      new SavedReportsMetaDataProvider().defineAll(instance, "postgres", "sftp", null);
      ///////////////////////////////////////////////////////////////////////////
      // rendered reports are files, one per record, on the SFTP server: core   //
      // now refuses a filesystem table without backend details (qqq#1364)      //
      ///////////////////////////////////////////////////////////////////////////
      instance.getTable("reportStorage").setBackendDetails(new SFTPTableBackendDetails().withBasePath("reportStorage")
         .withCardinality(Cardinality.ONE).withFileNameFieldName("reference").withContentsFieldName("contents"));
      new SFTPDataIntegrationQBitProducer().withSftpDataIntegrationQBitConfig(new SFTPDataIntegrationQBitConfig()
         .withSourceFileTableConfig(ProvidedOrSuppliedTableConfig.provideTableUsingBackendNamed("sftp"))
         .withDestinationFileTableConfig(ProvidedOrSuppliedTableConfig.provideTableUsingBackendNamed("sftp"))
         .withStagingFileTableConfig(ProvidedOrSuppliedTableConfig.provideTableUsingBackendNamed("files"))
         .withSchedulerName("demoScheduler")
         .withTableMetaDataCustomizer((i, table) ->
         {
            if(table.getBackendName() == null)
            {
               table.setBackendName("postgres");
               table.setBackendDetails(new RDBMSTableBackendDetails()
                  .withTableName(QInstanceEnricher.inferBackendName(table.getName())));
               QInstanceEnricher.setInferredFieldBackendNames(table);
            }
            return table;
         })).produce(instance, "demo-import");
   }


   /**
    * qbit-webhooks 0.x puts SendTestEventToWebhook's input form before the
    * preview step, so the records chosen at init are lost and the run fails
    * with "No records were selected" (QRun-IO/qqq#1361; fixed on qbit-webhooks
    * fix/test-event-records). Until a release carries it, move the form after
    * the first backend step here.
    */
   static void moveTestEventFormAfterPreview(QInstance instance)
   {
      QProcessMetaData process = instance.getProcess("SendTestEventToWebhook");
      if(process == null || process.getStepList() == null || process.getStepList().size() < 2)
      {
         return;
      }
      List<QStepMetaData> steps = process.getStepList();
      if(steps.get(0) instanceof QFrontendStepMetaData form && "input".equals(form.getName()) && steps.get(1) instanceof QBackendStepMetaData)
      {
         steps.remove(0);
         steps.add(1, form);
      }
   }
}
