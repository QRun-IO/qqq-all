/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsQBitProducer;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantQBitProducer;
import com.kingsrook.qbits.backups.BackupsQBitConfig;
import com.kingsrook.qbits.backups.BackupsQBitProducer;
import com.kingsrook.qbits.backups.storage.InMemoryBackupStorage;
import com.kingsrook.qbits.cdn.CdnQBitConfig;
import com.kingsrook.qbits.cdn.CdnQBitProducer;
import com.kingsrook.qbits.cluster.ClusterQBitConfig;
import com.kingsrook.qbits.cluster.ClusterQBitProducer;
import com.kingsrook.qbits.collaboration.CollaborationQBitConfig;
import com.kingsrook.qbits.collaboration.CollaborationQBitProducer;
import com.kingsrook.qbits.dataanalysis.DataAnalysisQBitConfig;
import com.kingsrook.qbits.dataanalysis.DataAnalysisQBitProducer;
import com.kingsrook.qbits.dbperformance.DbPerfQBitConfig;
import com.kingsrook.qbits.dbperformance.DbPerfQBitProducer;
import com.kingsrook.qbits.email.EmailQBitConfig;
import com.kingsrook.qbits.email.EmailQBitProducer;
import com.kingsrook.qbits.filegateway.FileGatewayQBitConfig;
import com.kingsrook.qbits.filegateway.FileGatewayQBitProducer;
import com.kingsrook.qbits.help.HelpQBitConfig;
import com.kingsrook.qbits.help.HelpQBitProducer;
import com.kingsrook.qbits.jobs.JobsQBitConfig;
import com.kingsrook.qbits.jobs.JobsQBitProducer;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitConfig;
import com.kingsrook.qbits.objectstorage.ObjectStorageQBitProducer;
import com.kingsrook.qbits.objectstorage.config.AddressingStyle;
import com.kingsrook.qbits.objectstorage.config.BucketRole;
import com.kingsrook.qbits.objectstorage.config.ProviderType;
import com.kingsrook.qbits.objectstorage.config.StorageBucketConfig;
import com.kingsrook.qbits.objectstorage.config.StorageConnectionConfig;
import com.kingsrook.qbits.qacapture.QaCaptureQBitConfig;
import com.kingsrook.qbits.qacapture.QaCaptureQBitProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitProducer;
import com.kingsrook.qbits.quicksearch.SearchableFieldConfig;
import com.kingsrook.qbits.redis.RedisConnectionConfig;
import com.kingsrook.qbits.redis.RedisQBitConfig;
import com.kingsrook.qbits.redis.RedisQBitProducer;
import com.kingsrook.qbits.releasenotes.ReleaseNotesQBitConfig;
import com.kingsrook.qbits.releasenotes.ReleaseNotesQBitProducer;
import com.kingsrook.qbits.releasenotes.sources.ComponentVersionsSource;
import com.kingsrook.qbits.secureddata.SecuredDataQBitConfig;
import com.kingsrook.qbits.secureddata.SecuredDataQBitProducer;
import com.kingsrook.qbits.sessionstore.QSessionStoreProviderType;
import com.kingsrook.qbits.sessionstore.QSessionStoreQBitConfig;
import com.kingsrook.qbits.sessionstore.QSessionStoreQBitProducer;
import com.kingsrook.qbits.slack.SlackQBitConfig;
import com.kingsrook.qbits.slack.SlackQBitProducer;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitConfig;
import com.kingsrook.qbits.sqlworkbench.SqlWorkbenchQBitProducer;
import com.kingsrook.qbits.wormaudit.WormAuditQBitConfig;
import com.kingsrook.qbits.wormaudit.WormAuditQBitProducer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.health.HealthMetaDataProvider;
import com.kingsrook.qqq.backend.core.jobs.DurableJobsMetaData;
import com.kingsrook.qqq.backend.core.jobs.DurableJobsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.audits.AuditsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.processes.implementations.columnstats.ColumnStatsStep;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel;
import com.kingsrook.qqq.backend.core.model.metadata.audits.QAuditRules;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.quartz.QuartzSchedulerMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppChildMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.querystats.QueryStatMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.savedbulkloadprofiles.SavedBulkLoadProfileMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.savedreports.SavedReportsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.savedviews.SavedViewsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.scripts.ScriptsMetaDataProvider;
import com.kingsrook.qqq.backend.core.actions.preferences.UserPreferencesKeyValueStoreMetaDataProvider;
import com.kingsrook.qqq.backend.core.processes.implementations.sharing.SharingMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordFeature;
import com.kingsrook.qqq.backend.module.filesystem.base.model.metadata.AbstractFilesystemTableBackendDetails;
import com.kingsrook.qqq.backend.module.filesystem.base.model.metadata.Cardinality;
import com.kingsrook.qqq.backend.module.filesystem.local.model.metadata.FilesystemBackendMetaData;
import com.kingsrook.qqq.backend.module.filesystem.local.model.metadata.FilesystemTableBackendDetails;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;


/*******************************************************************************
 ** The "enterprise" profile (QQQ_ALL_PROFILE=enterprise): the core profile plus
 ** every qbit and core module an enterprise rollout runs, all on the local H2
 ** backend, for exercising the terminal clients against a large instance.
 **
 ** Each feature is named; QQQ_ALL_ENTERPRISE_EXCLUDE (comma separated names)
 ** leaves features out. Quick search needs QQQ_ALL_OPENSEARCH_HOST and redis
 ** needs QQQ_ALL_REDIS_HOST; without them those two are left out.
 *******************************************************************************/
final class EnterpriseFeatures
{
   static final String PROFILE = "enterprise";

   private static final String SAMPLE_PROVIDER = "com.kingsrook.sampleapp.metadata.SampleMetaDataProvider";

   private interface Feature
   {
      void add(QInstance instance) throws Exception;
   }



   private EnterpriseFeatures()
   {
   }



   /*******************************************************************************
    ** True when the process runs the enterprise profile.
    *******************************************************************************/
   static boolean isEnabled()
   {
      return PROFILE.equals(System.getenv("QQQ_ALL_PROFILE"));
   }



   /*******************************************************************************
    ** Names the operator excluded.
    *******************************************************************************/
   static Set<String> excluded()
   {
      String value = System.getenv().getOrDefault("QQQ_ALL_ENTERPRISE_EXCLUDE", "");
      return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
   }



   /*******************************************************************************
    ** Add every enterprise feature that is not excluded.
    *******************************************************************************/
   static void add(QInstance instance, String backendName, int port) throws QException
   {
      String schedulerName = "demoScheduler";
      String baseUrl = "http://127.0.0.1:" + port;

      //////////////////////////////////////////////////////////////////////////
      // qbits schedule with cron expressions, which the simple scheduler of  //
      // the core profile cannot run: swap it for an in-memory Quartz         //
      //////////////////////////////////////////////////////////////////////////
      Properties quartzProperties = new Properties();
      quartzProperties.put("org.quartz.scheduler.instanceName", "qqqAllEnterprise");
      quartzProperties.put("org.quartz.threadPool.threadCount", "4");
      quartzProperties.put("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
      instance.getSchedulers().put(schedulerName, new QuartzSchedulerMetaData().withProperties(quartzProperties).withName(schedulerName));
      MetaDataCustomizerInterface<QTableMetaData> tableCustomizer = (i, table) ->
      {
         if(table.getBackendName() == null || backendName.equals(table.getBackendName()))
         {
            table.setBackendName(backendName);
            table.setBackendDetails(new RDBMSTableBackendDetails()
               .withTableName(QInstanceEnricher.inferBackendName(table.getName())));
            QInstanceEnricher.setInferredFieldBackendNames(table);
         }
         return table;
      };
      Consumer<QTableMetaData> enricher = table ->
      {
         try
         {
            tableCustomizer.customizeMetaData(instance, table);
         }
         catch(QException e)
         {
            throw new IllegalStateException(e);
         }
      };

      Map<String, Feature> features = new LinkedHashMap<>();

      /////////////////////////
      // core qqq modules    //
      /////////////////////////
      features.put("audits", i ->
      {
         new AuditsMetaDataProvider().defineAll(i, backendName, enricher);
         for(String tableName : List.of("customer", "order", "address"))
         {
            i.getTable(tableName).setAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD));
         }
      });
      features.put("scripts", i ->
      {
         ////////////////////////////////////////////////////////////////////
         // the scripts provider defines its own "tables" possible values  //
         ////////////////////////////////////////////////////////////////////
         i.getPossibleValueSources().remove("tables");
         new ScriptsMetaDataProvider().defineAll(i, backendName, enricher);
      });
      features.put("savedViews", i -> new SavedViewsMetaDataProvider().defineAll(i, backendName, enricher));
      features.put("savedReports", i ->
      {
         new SavedReportsMetaDataProvider().defineAll(i, backendName, "files", enricher);
         //////////////////////////////////////////////////////////////////
         // rendered reports are files, one per record, under the files  //
         // backend (the provider leaves the storage details to the host) //
         //////////////////////////////////////////////////////////////////
         QTableMetaData reportStorage = i.getTable("reportStorage");
         if(reportStorage != null && reportStorage.getBackendDetails() == null)
         {
            reportStorage.setBackendDetails(new FilesystemTableBackendDetails().withBasePath("reportStorage").withCardinality(Cardinality.ONE).withFileNameFieldName("reference").withContentsFieldName("contents"));
         }
         ensureFilesystemDirectories(i);
      });
      features.put("bulkLoadProfiles", i -> new SavedBulkLoadProfileMetaDataProvider().defineAll(i, backendName, enricher));
      features.put("sharing", i -> new SharingMetaDataProvider().defineAll(i, null));
      features.put("queryStats", i -> new QueryStatMetaDataProvider().defineAll(i, backendName, enricher));
      features.put("userPreferences", i -> new UserPreferencesKeyValueStoreMetaDataProvider().defineAll(i, backendName, null));
      //////////////////////////////////////////////////////////////////////////
      // central health: its tables turn on the health UI API extension, so  //
      // /version lists it (QRun-IO/qqq-all#44). Enterprise only for now: in //
      // the core profile its backend checks share the cached sqlite         //
      // connection across the test suite's runtimes                         //
      //////////////////////////////////////////////////////////////////////////
      features.put("health", i -> new HealthMetaDataProvider().defineAll(i, backendName, enricher));
      features.put("durableJobs", i ->
      {
         new DurableJobsMetaDataProvider().defineAll(i, backendName, enricher);
         i.withSupplementalMetaData(new DurableJobsMetaData().withWorkerThreads(2));
      });

      /////////////////////////
      // qbits               //
      /////////////////////////
      //////////////////////////////////////////////////////////////////////////
      // object storage on a local MinIO (enterprise-server.sh starts it):    //
      // collaboration attachments live in its private "collaboration"       //
      // bucket. Credentials are references to the server's env only.         //
      //////////////////////////////////////////////////////////////////////////
      String s3Endpoint = System.getenv("S3_ENDPOINT");
      if(s3Endpoint != null && !s3Endpoint.isBlank())
      {
         features.put("objectStorage", i -> new ObjectStorageQBitProducer().withQBitConfig(new ObjectStorageQBitConfig()
            .withBackendName(backendName).withSchedulerName(schedulerName).withAdminApp(true)
            .withConnection(new StorageConnectionConfig("local").withProviderType(ProviderType.MINIO)
               .withEndpoint("${env.S3_ENDPOINT}").withRegion("us-east-1").withAddressingStyle(AddressingStyle.PATH)
               .withAccessKeyRef("${env.S3_ACCESS_KEY}").withSecretKeyRef("${env.S3_SECRET_KEY}"))
            .withBucket(new StorageBucketConfig("collaboration").withConnection("local")
               .withBucketName("${env.S3_ATTACHMENTS_BUCKET}").withRole(BucketRole.PRIVATE).withIndexed(false)))
            .produce(i).addSelfToInstance(i));
      }

      features.put("jobs", i -> new JobsQBitProducer().withQBitConfig(new JobsQBitConfig()
         .withSchedulerName(schedulerName)).produce(i).addSelfToInstance(i));
      features.put("collaboration", i -> new CollaborationQBitProducer().withQBitConfig(new CollaborationQBitConfig()
         .withBackendName(backendName).withSchedulerName(schedulerName).withUiApi(true).withAdminApp(true)
         .withDefaultFeatures(RecordFeature.values())
         .withFrontendBaseUrl(baseUrl)).produce(i).addSelfToInstance(i));
      features.put("alerts", i -> new AlertsQBitProducer().withQBitConfig(new AlertsQBitConfig()
         .withBackendName(backendName).withSchedulerName(schedulerName)
         .withPublicBaseUrl(baseUrl)).produce(i).addSelfToInstance(i));
      features.put("dataAnalysis", i -> new DataAnalysisQBitProducer().withQBitConfig(new DataAnalysisQBitConfig()
         .withBackendName(backendName).withSchedulerName(schedulerName)).produce(i).addSelfToInstance(i));
      features.put("dbPerformance", i -> new DbPerfQBitProducer().withQBitConfig(new DbPerfQBitConfig()
         .withBackendName(backendName).withSchedulerName(schedulerName)).produce(i).addSelfToInstance(i));
      features.put("sqlWorkbench", i -> new SqlWorkbenchQBitProducer().withQBitConfig(new SqlWorkbenchQBitConfig()
         .withBackendName(backendName).withTableMetaDataCustomizer(tableCustomizer)
         .withAllowedBackendNames(List.of(backendName))).produce(i).addSelfToInstance(i));
      features.put("help", i -> new HelpQBitProducer().withQBitConfig(new HelpQBitConfig()
         .withBackendName(backendName).withTableMetaDataCustomizer(tableCustomizer)).produce(i).addSelfToInstance(i));
      features.put("releaseNotes", i -> new ReleaseNotesQBitProducer().withQBitConfig(new ReleaseNotesQBitConfig()
         .withBackendName(backendName).withApplicationName("qqqAll").withApplicationLabel("QQQ All")
         .withAppVersion("4.1.0").withAllowNonReleaseVersions(true)
         .withSource(new ComponentVersionsSource())).produce(i).addSelfToInstance(i));
      features.put("qaCapture", i -> new QaCaptureQBitProducer().withQBitConfig(new QaCaptureQBitConfig()
         .withBackendName(backendName).withEnvironment("local").withBackendVersion("4.1.0-SNAPSHOT"))
         .produce(i).addSelfToInstance(i));
      features.put("securedData", i -> new SecuredDataQBitProducer().withQBitConfig(new SecuredDataQBitConfig()
         .withBackendName(backendName).withSchedulerName(schedulerName)
         .withTableMetaDataCustomizer(tableCustomizer)).produce(i).addSelfToInstance(i));
      features.put("email", i -> new EmailQBitProducer().withQBitConfig(new EmailQBitConfig()
         .withBackendName(backendName).withSchedulerName(schedulerName).withPublicBaseUrl(baseUrl)
         .withTableMetaDataCustomizer(tableCustomizer)).produce(i).addSelfToInstance(i));
      features.put("slack", i -> new SlackQBitProducer().withQBitConfig(new SlackQBitConfig()
         .withBackendName(backendName).withSchedulerName(schedulerName).withPublicBaseUrl(baseUrl)
         .withTableMetaDataCustomizer(tableCustomizer)).produce(i).addSelfToInstance(i));
      features.put("cluster", i -> new ClusterQBitProducer().withQBitConfig(new ClusterQBitConfig()
         .withBackendName(backendName)).produce(i).addSelfToInstance(i));
      features.put("cdn", i -> new CdnQBitProducer().withQBitConfig(new CdnQBitConfig()
         .withBackendName(backendName).withSchedulerName(schedulerName)
         .withPurgeableTables(List.of("customer")).withTableMetaDataCustomizer(tableCustomizer))
         .produce(i).addSelfToInstance(i));
      features.put("backups", i -> new BackupsQBitProducer().withQBitConfig(new BackupsQBitConfig()
         .withBackendName(backendName).withSchedulerName(schedulerName)
         .withStorageResolver(name -> new InMemoryBackupStorage())).produce(i).addSelfToInstance(i));
      features.put("fileGateway", i -> new FileGatewayQBitProducer().withQBitConfig(new FileGatewayQBitConfig()
         .withBackendName(backendName).withSftpEnabled(false).withSchedulerName(schedulerName)
         .withTableMetaDataCustomizer(tableCustomizer)).produce(i).addSelfToInstance(i));
      features.put("sessionStore", i ->
      {
         new QSessionStoreQBitProducer().withConfig(new QSessionStoreQBitConfig()
         .withProviderType(QSessionStoreProviderType.TABLE_BASED).withBackendName(backendName)
         .withDefaultTtl(Duration.ofHours(8)).withEnableSlidingExpiration(true)).produce(i, "session-store");
         if(i.getProcess("cleanExpiredSessions") != null && i.getProcess("cleanExpiredSessions").getSchedule() != null)
         {
            i.getProcess("cleanExpiredSessions").getSchedule().setSchedulerName(schedulerName);
         }
      });
      features.put("wormAudit", i ->
      {
         KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
         KeyPair keyPair = generator.generateKeyPair();
         new WormAuditQBitProducer().withQBitConfig(new WormAuditQBitConfig()
            .withBackendName(backendName).withSchedulerName(schedulerName).withAppRole("app")
            .withSigningKey(() -> new WormAuditQBitConfig.SigningKey("local", keyPair.getPrivate()))
            .withTrustedPublicKeys(Map.of("local", keyPair.getPublic()))
            .withTableMetaDataCustomizer(tableCustomizer)).produce(i).addSelfToInstance(i);
      });
      features.put("assistant", i -> new AssistantQBitProducer().withQBitConfig(new AssistantQBitConfig()
         .withBackendName(backendName).withUiApiBaseUrl(baseUrl)
         .withTableMetaDataCustomizer(tableCustomizer)).produce(i).addSelfToInstance(i));

      if(isSampleAppOnClasspath())
      {
         features.put("sampleApp", EnterpriseFeatures::mergeSampleApp);
      }

      String opensearchHost = System.getenv("QQQ_ALL_OPENSEARCH_HOST");
      if(opensearchHost != null && !opensearchHost.isBlank())
      {
         features.put("quickSearch", i ->
         {
            QuickSearchQBitConfig searchConfig = new QuickSearchQBitConfig()
               .withBackendName(backendName)
               .withOpensearchHost(opensearchHost)
               .withOpensearchPort(Integer.valueOf(System.getenv().getOrDefault("QQQ_ALL_OPENSEARCH_PORT", "9200")))
               .withOpensearchIndexName(System.getenv().getOrDefault("QQQ_ALL_OPENSEARCH_INDEX", "qqq-all-enterprise"))
               .withUseSsl(false)
               .withAllowPlaintextCredentials(true)
               .withSchedulerName(schedulerName)
               .withTableMetaDataCustomizer(tableCustomizer)
               .withSearchableTable("customer", List.of(new SearchableFieldConfig("name"), new SearchableFieldConfig("email")))
               .withSearchableTable("order", List.of(new SearchableFieldConfig("orderNo"), new SearchableFieldConfig("status")));
            new QuickSearchQBitProducer().withConfig(searchConfig).produce(i).addSelfToInstance(i);
         });
      }

      String redisHost = System.getenv("QQQ_ALL_REDIS_HOST");
      if(redisHost != null && !redisHost.isBlank())
      {
         features.put("redis", i -> new RedisQBitProducer().withQBitConfig(new RedisQBitConfig()
            .withConnection("realtime", new RedisConnectionConfig().withHost(redisHost)
               .withPort(Integer.valueOf(System.getenv().getOrDefault("QQQ_ALL_REDIS_PORT", "6379")))
               .withTls(false))
            .withRealtime("realtime", "local", "qqq-all")).produce(i).addSelfToInstance(i));
      }

      Set<String> excluded = excluded();
      for(Map.Entry<String, Feature> entry : features.entrySet())
      {
         if(excluded.contains(entry.getKey()))
         {
            System.out.println("[enterprise] skipped " + entry.getKey());
            continue;
         }
         try
         {
            entry.getValue().add(instance);
            System.out.println("[enterprise] added " + entry.getKey());
         }
         catch(Exception e)
         {
            if("true".equals(System.getenv("QQQ_ALL_ENTERPRISE_LENIENT")))
            {
               System.out.println("[enterprise] FAILED " + entry.getKey() + ": " + e);
               Arrays.stream(e.getStackTrace()).limit(6).forEach(frame -> System.out.println("[enterprise]    at " + frame));
               continue;
            }
            throw new QException("Enterprise feature " + entry.getKey() + " failed: " + e.getMessage(), e);
         }
      }

      enableColumnStatistics(instance);
      auditDataTables(instance);
      addNavigationApps(instance);
   }



   /*******************************************************************************
    ** QQQ turns QUERY_STATS (column statistics: counts, distinct values, min/max
    ** and averages per field) off on every backend by default. Turn it on for the
    ** backends whose module can aggregate, RDBMS (H2, SQLite, the sample's
    ** in-memory H2) and the memory backend, so their tables offer statistics as
    ** the web UI and the terminal clients expect; filesystem and API backends
    ** cannot aggregate and stay without it. Tables of those backends that list
    ** QUERY_STATS as disabled keep it off. The columnStats process that serves
    ** the statistics is added when the instance lacks it.
    *******************************************************************************/
   static void enableColumnStatistics(QInstance instance)
   {
      ////////////////////////////////////////////////////////////////////
      // the UI API serves column statistics through this process       //
      ////////////////////////////////////////////////////////////////////
      if(instance.getProcess(ColumnStatsStep.getProcessMetaData().getName()) == null)
      {
         instance.addProcess(ColumnStatsStep.getProcessMetaData());
      }
      for(QBackendMetaData backend : instance.getBackends().values())
      {
         if(backend instanceof RDBMSBackendMetaData || new MemoryBackendModule().getBackendType().equals(backend.getBackendType()))
         {
            backend.withCapability(Capability.QUERY_STATS);
         }
      }
   }



   /*******************************************************************************
    ** Field-level audits on the demo and sample data tables (customer, order and
    ** address get them with the audits feature), when audits are installed.
    *******************************************************************************/
   static void auditDataTables(QInstance instance)
   {
      if(instance.getTable("audit") == null)
      {
         return;
      }
      for(String tableName : List.of("customer", "order", "orderLine", "address", "person", "pet", "petNote", "carrier"))
      {
         QTableMetaData table = instance.getTable(tableName);
         if(table != null && table.getAuditRules() == null)
         {
            table.setAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD));
         }
      }
   }



   /*******************************************************************************
    ** The core profile has no apps and several qbits only add tables. Put the
    ** demo tables in a "QQQ All" app and every visible table that no app reaches
    ** into an "Enterprise Admin" app, one section per table-name prefix, so the
    ** clients' app tree reaches everything.
    *******************************************************************************/
   private static void addNavigationApps(QInstance instance)
   {
      List<String> demoTables = List.of("customer", "order", "orderLine", "product", "address", "apiCatalog", "demoNote",
         "shipping_country", "shipping_stateProvince", "shipping_city");
      List<QAppChildMetaData> demoChildren = new ArrayList<>();
      demoTables.stream().map(instance::getTable).filter(Objects::nonNull).forEach(demoChildren::add);
      List.of("syncOrder", "calculateOrderTotal").stream().map(instance::getProcess).filter(Objects::nonNull).forEach(demoChildren::add);
      instance.addApp(new QAppMetaData().withName("qqqAll").withLabel("QQQ All").withIcon(new QIcon("storefront"))
         .withSectionOfChildren(new QAppSection().withName("demoData").withLabel("Demo Data").withIcon(new QIcon("table_chart")), demoChildren));

      Set<String> reached = new HashSet<>();
      for(QAppMetaData app : instance.getApps().values())
      {
         if(app.getChildren() != null)
         {
            app.getChildren().stream().filter(c -> c instanceof QTableMetaData).forEach(c -> reached.add(c.getName()));
         }
         if(app.getSections() != null)
         {
            app.getSections().stream().filter(section -> section.getTables() != null).forEach(section -> reached.addAll(section.getTables()));
         }
      }

      Map<String, List<QAppChildMetaData>> byPrefix = new TreeMap<>();
      for(QTableMetaData table : instance.getTables().values())
      {
         if(reached.contains(table.getName()) || table.getIsHidden())
         {
            continue;
         }
         String backendName = QInstanceEnricher.inferBackendName(table.getName());
         String prefix = backendName.contains("_") ? backendName.substring(0, backendName.indexOf('_')) : backendName;
         byPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(table);
      }
      QAppMetaData admin = new QAppMetaData().withName("enterpriseAdmin").withLabel("Enterprise Admin").withIcon(new QIcon("admin_panel_settings"));
      byPrefix.forEach((prefix, tables) -> admin.withSectionOfChildren(new QAppSection().withName("admin_" + prefix)
         .withLabel(QInstanceEnricher.nameToLabel(prefix)).withIcon(new QIcon("folder")), tables));
      instance.addApp(admin);
   }



   /*******************************************************************************
    ** Filesystem tables (rendered report storage) answer 500 when their directory
    ** does not exist yet: create each one under the files backend.
    *******************************************************************************/
   private static void ensureFilesystemDirectories(QInstance instance) throws Exception
   {
      if(!(instance.getBackend("files") instanceof FilesystemBackendMetaData files))
      {
         return;
      }
      for(QTableMetaData table : instance.getTables().values())
      {
         if("files".equals(table.getBackendName()) && table.getBackendDetails() instanceof AbstractFilesystemTableBackendDetails details && details.getBasePath() != null)
         {
            Files.createDirectories(Path.of(files.getBasePath(), details.getBasePath()));
         }
      }
   }



   /*******************************************************************************
    ** True when the qqq sample project's classes are on the classpath (the start
    ** script adds qqq-sample-project/target/classes).
    *******************************************************************************/
   private static boolean isSampleAppOnClasspath()
   {
      try
      {
         Class.forName(SAMPLE_PROVIDER);
         return true;
      }
      catch(ClassNotFoundException e)
      {
         return false;
      }
   }



   /*******************************************************************************
    ** Merge the standard qqq sample app (person, pet, carrier, city files, the
    ** field and adornment labs, its processes, widgets and apps) into this
    ** instance. Its ESB wiring is dropped (this instance has its own broker) and
    ** names this instance already defines win. Reflection keeps the sample
    ** project out of the build; it is a classpath add-on.
    *******************************************************************************/
   private static void mergeSampleApp(QInstance instance) throws Exception
   {
      Class<?> provider = Class.forName(SAMPLE_PROVIDER);
      QInstance sample = (QInstance) provider.getMethod("defineInstance").invoke(null);
      provider.getMethod("primeTestDatabase", String.class).invoke(null, "prime-test-database.sql");

      //////////////////////////////////////////////////////////////////////////
      // the sample's sales module defines its own "customer"; this instance  //
      // already has one, so the sales module stays out                       //
      //////////////////////////////////////////////////////////////////////////
      Set<String> skip = Set.of("sales", "customer", "sku", "tag", "sales_order", "sales_order_line", "sales_order_tag",
         "salesOrderJoinLine", "salesOrderJoinTag", "salesOrderLines", "salesOrderTags");
      sample.getTables().keySet().removeAll(skip);
      sample.getPossibleValueSources().keySet().removeAll(skip);
      sample.getJoins().keySet().removeAll(skip);
      sample.getWidgets().keySet().removeAll(skip);
      sample.getApps().keySet().removeAll(skip);
      sample.getApps().values().forEach(app ->
      {
         if(app.getChildren() != null)
         {
            app.getChildren().removeIf(child -> skip.contains(child.getName()));
         }
         if(app.getSections() != null)
         {
            app.getSections().forEach(section ->
            {
               if(section.getTables() != null)
               {
                  section.getTables().removeIf(skip::contains);
               }
            });
         }
      });

      sample.getBackends().forEach((name, value) -> instance.getBackends().putIfAbsent(name, value));
      sample.getPossibleValueSources().forEach((name, value) -> instance.getPossibleValueSources().putIfAbsent(name, value));
      sample.getSecurityKeyTypes().forEach((name, value) -> instance.getSecurityKeyTypes().putIfAbsent(name, value));
      sample.getTables().forEach((name, table) ->
      {
         if(table.getSupplementalMetaData() != null)
         {
            table.getSupplementalMetaData().values().removeIf(EnterpriseFeatures::isEsb);
         }
         instance.getTables().putIfAbsent(name, table);
      });
      sample.getProcesses().forEach((name, process) ->
      {
         if(process.getSupplementalMetaData() != null)
         {
            process.getSupplementalMetaData().values().removeIf(EnterpriseFeatures::isEsb);
         }
         instance.getProcesses().putIfAbsent(name, process);
      });
      sample.getJoins().forEach((name, value) -> instance.getJoins().putIfAbsent(name, value));
      sample.getReports().forEach((name, value) -> instance.getReports().putIfAbsent(name, value));
      sample.getWidgets().forEach((name, value) -> instance.getWidgets().putIfAbsent(name, value));
      sample.getApps().forEach((name, value) -> instance.getApps().putIfAbsent(name, value));
   }



   /*******************************************************************************
    ** ESB supplemental meta data of the sample points at its own destinations.
    *******************************************************************************/
   private static boolean isEsb(Object supplemental)
   {
      return supplemental != null && supplemental.getClass().getName().startsWith("com.kingsrook.qqq.esb.");
   }
}
