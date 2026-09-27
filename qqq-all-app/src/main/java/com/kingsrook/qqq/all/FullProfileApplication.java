/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import com.kingsrook.qbits.userrolepermissions.UserRolePermissionsQBitConfig;
import com.kingsrook.qbits.userrolepermissions.UserRolePermissionsQBitProducer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.AuthScope;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.OAuth2AuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.messaging.email.EmailMessagingProviderMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.ProvidedOrSuppliedTableConfig;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.metadata.RedirectStateMetaDataProducer;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.metadata.UserSessionMetaDataProducer;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.model.UserSession;
import com.kingsrook.qqq.backend.module.filesystem.base.model.metadata.Cardinality;
import com.kingsrook.qqq.backend.module.filesystem.base.model.metadata.RecordFormat;
import com.kingsrook.qqq.backend.module.filesystem.s3.model.metadata.S3TableBackendDetails;
import com.kingsrook.qqq.backend.module.filesystem.sftp.model.metadata.SFTPBackendMetaData;
import com.kingsrook.qqq.backend.module.filesystem.sftp.model.metadata.SFTPTableBackendDetails;
import com.kingsrook.qqq.backend.module.mongodb.model.metadata.MongoDBBackendMetaData;
import com.kingsrook.qqq.backend.module.mongodb.model.metadata.MongoDBTableBackendDetails;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.backend.module.postgres.model.metadata.PostgreSQLBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSTableBackendDetails;
import com.kingsrook.qqq.esb.model.EsbDestinationType;
import com.kingsrook.qqq.esb.model.EsbInstanceMetaData;
import com.kingsrook.qqq.esb.model.EsbProcessEvent;
import com.kingsrook.qqq.esb.model.EsbProcessMetaData;
import com.kingsrook.qqq.esb.model.EsbProcessPublication;
import com.kingsrook.qqq.esb.model.EsbProviderType;
import com.kingsrook.qqq.esb.model.QEsbDestinationMetaData;
import com.kingsrook.qqq.esb.model.QEsbProviderMetaData;

/** Extends the core demo with external services selected by the full profile. */
final class FullProfileApplication extends AbstractQQQApplication
{
   private final Path                dataDirectory;
   private final FullProfileSettings settings;
   private final boolean             provisionSchema;

   FullProfileApplication(Path dataDirectory, FullProfileSettings settings)
   {
      this(dataDirectory, settings, false);
   }

   FullProfileApplication(Path dataDirectory, FullProfileSettings settings, boolean provisionSchema)
   {
      this.dataDirectory = dataDirectory;
      this.settings = settings;
      this.provisionSchema = provisionSchema;
   }

   @Override
   public QInstance defineQInstance() throws QException
   {
      QInstance instance = new QqqAllApplication(dataDirectory, settings.get("ARTEMIS_URL"), false, settings.port("PORT", "8080"), settings.get("BIND_HOST")).defineQInstance();
      addBackends(instance);
      addTables(instance);
      addAuthentication(instance);
      addEsb(instance);
      DemoQBits.addCore(instance, "postgres");
      DemoQBits.addFull(instance, settings);
      // OIDC and ESB sessions use string user IDs, unlike the qbit's default integer user ID.
      instance.getTable("processTrace").getField("userId").setType(QFieldType.STRING);
      DemoUsers.protectWebhookResources(instance);
      for(QTableMetaData table : instance.getTables().values())
      {
         if(!"postgres".equals(table.getBackendName()))
         {
            continue;
         }
         if(table.getBackendDetails() == null)
         {
            table.setBackendDetails(new RDBMSTableBackendDetails()
               .withTableName(QInstanceEnricher.inferBackendName(table.getName())));
         }
         QInstanceEnricher.setInferredFieldBackendNames(table);
      }

      EmailMessagingProviderMetaData mail = new EmailMessagingProviderMetaData()
         .withSmtpServer(settings.get("SMTP_HOST"))
         .withSmtpPort(settings.getOrDefault("SMTP_PORT", "1025"));
      mail.setName("mailpit");
      instance.addMessagingProvider(mail);
      if(provisionSchema)
      {
         try
         {
            DemoQBitData.ensurePostgresSchema(instance);
         }
         catch(SQLException e)
         {
            throw new QException("Could not provision full-profile qbit tables", e);
         }
      }
      return instance;
   }

   private void addBackends(QInstance instance)
   {
      instance.addBackend(new PostgreSQLBackendMetaData().withName("postgres")
         .withHostName(settings.get("POSTGRES_HOST")).withPort(settings.port("POSTGRES_PORT", "5432"))
         .withDatabaseName(settings.get("POSTGRES_DATABASE"))
         .withUsername(settings.get("POSTGRES_USER")).withPassword(settings.get("POSTGRES_PASSWORD")));
      instance.addBackend(new RDBMSBackendMetaData().withName("mysql").withVendor("mysql")
         .withHostName(settings.get("MYSQL_HOST")).withPort(settings.port("MYSQL_PORT", "3306"))
         .withDatabaseName(settings.get("MYSQL_DATABASE"))
         .withUsername(settings.get("MYSQL_USER")).withPassword(settings.get("MYSQL_PASSWORD")));
      instance.addBackend(new MongoDBBackendMetaData().withName("mongo")
         .withHost(settings.get("MONGO_HOST")).withPort(settings.port("MONGO_PORT", "27017"))
         .withDatabaseName(settings.get("MONGO_DATABASE"))
         .withUsername(settings.get("MONGO_USER")).withPassword(settings.get("MONGO_PASSWORD"))
         .withAuthSourceDatabase(settings.getOrDefault("MONGO_AUTH_DATABASE", "admin"))
         .withUrlSuffix("replicaSet=" + settings.get("MONGO_REPLICA_SET"))
         .withTransactionsSupported(true));
      instance.addBackend(new MinioBackendMetaData(settings.get("S3_ENDPOINT")).withName("s3")
         .withBucketName(settings.get("S3_BUCKET"))
         .withAccessKey(settings.get("S3_ACCESS_KEY")).withSecretKey(settings.get("S3_SECRET_KEY"))
         .withRegion(settings.getOrDefault("S3_REGION", "us-east-1")));
      instance.addBackend(new SFTPBackendMetaData().withName("sftp")
         .withHostName(settings.get("SFTP_HOST")).withPort(settings.port("SFTP_PORT", "22"))
         .withUsername(settings.get("SFTP_USER")).withPassword(settings.get("SFTP_PASSWORD"))
         .withBasePath(settings.getOrDefault("SFTP_BASE_PATH", "/upload")));
   }

   private void addTables(QInstance instance)
   {
      instance.addTable(new QTableMetaData().withName("warehouseCustomer").withLabel("Warehouse Customer")
         .withBackendName("postgres").withPrimaryKeyField("id")
         .withBackendDetails(new RDBMSTableBackendDetails().withTableName("warehouse_customer"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING)));
      instance.addTable(new QTableMetaData().withName("supplierOrder").withLabel("Supplier Order")
         .withBackendName("mysql").withPrimaryKeyField("id")
         .withBackendDetails(new RDBMSTableBackendDetails().withTableName("supplier_order"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("reference", QFieldType.STRING)));
      instance.addTable(new QTableMetaData().withName("shipment").withLabel("Shipment")
         .withBackendName("mongo").withPrimaryKeyField("id")
         .withBackendDetails(new MongoDBTableBackendDetails().withTableName("shipment"))
         .withField(new QFieldMetaData("id", QFieldType.STRING).withBackendName("_id"))
         .withField(new QFieldMetaData("tracking", QFieldType.STRING)));
      instance.addTable(new QTableMetaData().withName("document").withLabel("Document")
         .withBackendName("s3").withPrimaryKeyField("id")
         .withBackendDetails(new S3TableBackendDetails().withBasePath("documents")
            .withRecordFormat(RecordFormat.JSON).withCardinality(Cardinality.MANY).withGlob("*.json"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING)));
      instance.addTable(new QTableMetaData().withName("externalImportFile").withLabel("External Import File")
         .withBackendName("sftp").withPrimaryKeyField("id")
         .withBackendDetails(new SFTPTableBackendDetails().withBasePath("imports")
            .withRecordFormat(RecordFormat.JSON).withCardinality(Cardinality.MANY).withGlob("*.json"))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER))
         .withField(new QFieldMetaData("name", QFieldType.STRING)));
   }

   private void addAuthentication(QInstance instance) throws QException
   {
      instance.addTable(new UserSessionMetaDataProducer("postgres").produce(instance));
      instance.addTable(new RedirectStateMetaDataProducer("postgres").produce(instance));

      UserRolePermissionsQBitConfig qbitConfig = new UserRolePermissionsQBitConfig()
         .withUserTableConfig(ProvidedOrSuppliedTableConfig.provideTableUsingBackendNamed("postgres"))
         .withTableMetaDataCustomizer((i, table) ->
         {
            table.setBackendName("postgres");
            table.setBackendDetails(new RDBMSTableBackendDetails()
               .withTableName(QInstanceEnricher.inferBackendName(table.getName())));
            QInstanceEnricher.setInferredFieldBackendNames(table);
            return table;
         });
      new UserRolePermissionsQBitProducer().withUserRolePermissionsQBitConfig(qbitConfig).produce(instance);

      instance.registerAuthenticationProvider(AuthScope.instanceDefault(), new OAuth2AuthenticationMetaData()
         .withBaseUrl(settings.get("OIDC_BASE_URL"))
         .withExternalBaseUrl(settings.getOrDefault("OIDC_EXTERNAL_BASE_URL", settings.get("OIDC_BASE_URL")))
         .withClientId(settings.get("OIDC_CLIENT_ID"))
         .withClientSecret(settings.get("OIDC_CLIENT_SECRET"))
         .withScopes(settings.getOrDefault("OIDC_SCOPES", "openid email profile"))
         .withUserSessionTableName(UserSession.TABLE_NAME)
         .withRedirectStateTableName(RedirectStateMetaDataProducer.TABLE_NAME)
         .withCustomizer(new QCodeReference(FullOidcSessionCustomizer.class))
         .withName("keycloak"));
   }

   private void addEsb(QInstance instance)
   {
      EsbInstanceMetaData.of(instance)
         .getProvider("artemis")
         .withUsername(settings.get("ARTEMIS_USER"))
         .withPassword(settings.get("ARTEMIS_PASSWORD"))
         .withManagementUrl(settings.get("ARTEMIS_MANAGEMENT_URL"))
         .withManagementUsername(settings.get("ARTEMIS_USER"))
         .withManagementPassword(settings.get("ARTEMIS_PASSWORD"));
      EsbInstanceMetaData.of(instance)
         .withProvider(new QEsbProviderMetaData().withName("rabbitmq")
            .withType(EsbProviderType.RABBITMQ).withUrl(settings.get("RABBITMQ_URL"))
            .withUsername(settings.get("RABBITMQ_USER")).withPassword(settings.get("RABBITMQ_PASSWORD"))
            .withManagementUrl(settings.get("RABBITMQ_MANAGEMENT_URL"))
            .withManagementUsername(settings.get("RABBITMQ_USER"))
            .withManagementPassword(settings.get("RABBITMQ_PASSWORD")))
         .withDestination(new QEsbDestinationMetaData().withName("orderSyncEvents")
            .withType(EsbDestinationType.QUEUE).withProviderName("rabbitmq")
            .withDestinationName("qqq.all.orderSyncEvents"));
      EsbProcessMetaData.ofOrWithNew(instance.getProcess("syncOrder"))
         .withPublication(new EsbProcessPublication().withDestinationName("orderSyncEvents")
            .withEvents(List.of(EsbProcessEvent.COMPLETED)));
   }
}
