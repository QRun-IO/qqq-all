/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.OAuth2AuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.messaging.email.EmailMessagingProviderMetaData;
import com.kingsrook.qqq.backend.module.mongodb.model.metadata.MongoDBBackendMetaData;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.esb.model.EsbInstanceMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Full profile wiring tests that require no external services. */
class FullProfileTest
{
   @TempDir
   Path dataDirectory;

   @Test
   void rejectsMissingFullProfileCredentialsBeforeStartup()
   {
      assertThrows(IllegalArgumentException.class, () -> FullProfileSettings.from(Map.of("QQQ_ALL_PROFILE", "full")));
   }

   @Test
   void wiresEveryFullBackendBrokerMailAndOidcFromEnvironment() throws Exception
   {
      FullProfileSettings settings = FullProfileSettings.from(environment());
      QInstance instance = new FullProfileApplication(dataDirectory, settings).defineQInstance();
      new QInstanceValidator().validate(instance);

      RDBMSBackendMetaData postgres = (RDBMSBackendMetaData) instance.getBackend("postgres");
      assertEquals("postgres.example.test", postgres.getHostName());
      assertEquals("jdbc:postgresql://postgres.example.test:5432/qqq", ConnectionManager.getJdbcUrl(postgres));
      assertEquals("org.postgresql.Driver", ConnectionManager.getJdbcDriverClassName(postgres));
      assertEquals("\"", postgres.getActionStrategy().getIdentifierQuoteString());
      assertEquals("DEFAULT VALUES", postgres.getActionStrategy().getInsertDefaultValuesClause());
      assertEquals("mysql.example.test", ((RDBMSBackendMetaData) instance.getBackend("mysql")).getHostName());
      assertEquals("replicaSet=rs0", ((MongoDBBackendMetaData) instance.getBackend("mongo")).getUrlSuffix());
      assertEquals("postgres", instance.getTable("warehouseCustomer").getBackendName());
      assertEquals("mysql", instance.getTable("supplierOrder").getBackendName());
      assertEquals("mongo", instance.getTable("shipment").getBackendName());
      assertEquals("http://minio.example.test:9000",
         ((MinioBackendMetaData) instance.getBackend("s3")).getEndpoint());
      assertEquals("s3", instance.getTable("document").getBackendName());
      assertEquals("sftp", instance.getTable("externalImportFile").getBackendName());

      EsbInstanceMetaData esb = EsbInstanceMetaData.of(instance);
      assertEquals("tcp://artemis.example.test:61616", esb.getProvider("artemis").getUrl());
      assertEquals("fixture", esb.getProvider("artemis").getUsername());
      assertEquals("fixture", esb.getProvider("artemis").getPassword());
      assertEquals("http://artemis.example.test:8161", esb.getProvider("artemis").getManagementUrl());
      assertEquals("fixture", esb.getProvider("artemis").getManagementUsername());
      assertEquals("fixture", esb.getProvider("artemis").getManagementPassword());
      assertEquals("amqp://rabbit.example.test:5672/%2F", esb.getProvider("rabbitmq").getUrl());
      assertEquals("http://rabbit.example.test:15672", esb.getProvider("rabbitmq").getManagementUrl());
      assertEquals("fixture", esb.getProvider("rabbitmq").getManagementUsername());
      assertEquals("fixture", esb.getProvider("rabbitmq").getManagementPassword());
      assertEquals("artemis", esb.getDestination("orderEvents").getProviderName());
      assertEquals("rabbitmq", esb.getDestination("orderSyncEvents").getProviderName());

      OAuth2AuthenticationMetaData auth = (OAuth2AuthenticationMetaData) instance.getAuthentication();
      assertEquals(QAuthenticationType.OAUTH2, auth.getType());
      assertEquals("https://keycloak.example.test/realms/demo", auth.getBaseUrl());
      assertNotNull(instance.getTable(auth.getUserSessionTableName()));
      assertNotNull(instance.getTable(auth.getRedirectStateTableName()));
      assertEquals("mailpit.example.test", ((EmailMessagingProviderMetaData) instance.getMessagingProvider("mailpit")).getSmtpServer());
      assertTrue(instance.getQBits().values().stream().anyMatch(qbit -> "user-role-permissions".equals(qbit.getArtifactId())));
   }

   static Map<String, String> environment()
   {
      Map<String, String> values = new HashMap<>();
      values.put("QQQ_ALL_BIND_HOST", "127.0.0.1");
      values.put("QQQ_ALL_POSTGRES_HOST", "postgres.example.test");
      values.put("QQQ_ALL_POSTGRES_DATABASE", "qqq");
      values.put("QQQ_ALL_POSTGRES_USER", "fixture");
      values.put("QQQ_ALL_POSTGRES_PASSWORD", "fixture");
      values.put("QQQ_ALL_MYSQL_HOST", "mysql.example.test");
      values.put("QQQ_ALL_MYSQL_DATABASE", "qqq");
      values.put("QQQ_ALL_MYSQL_USER", "fixture");
      values.put("QQQ_ALL_MYSQL_PASSWORD", "fixture");
      values.put("QQQ_ALL_MONGO_HOST", "mongo.example.test");
      values.put("QQQ_ALL_MONGO_DATABASE", "qqq");
      values.put("QQQ_ALL_MONGO_USER", "fixture");
      values.put("QQQ_ALL_MONGO_PASSWORD", "fixture");
      values.put("QQQ_ALL_MONGO_REPLICA_SET", "rs0");
      values.put("QQQ_ALL_S3_BUCKET", "qqq-demo");
      values.put("QQQ_ALL_S3_ENDPOINT", "http://minio.example.test:9000");
      values.put("QQQ_ALL_S3_ACCESS_KEY", "fixture");
      values.put("QQQ_ALL_S3_SECRET_KEY", "fixture");
      values.put("QQQ_ALL_SFTP_HOST", "sftp.example.test");
      values.put("QQQ_ALL_SFTP_USER", "fixture");
      values.put("QQQ_ALL_SFTP_PASSWORD", "fixture");
      values.put("QQQ_ALL_ARTEMIS_URL", "tcp://artemis.example.test:61616");
      values.put("QQQ_ALL_ARTEMIS_USER", "fixture");
      values.put("QQQ_ALL_ARTEMIS_PASSWORD", "fixture");
      values.put("QQQ_ALL_ARTEMIS_MANAGEMENT_URL", "http://artemis.example.test:8161");
      values.put("QQQ_ALL_RABBITMQ_URL", "amqp://rabbit.example.test:5672/%2F");
      values.put("QQQ_ALL_RABBITMQ_MANAGEMENT_URL", "http://rabbit.example.test:15672");
      values.put("QQQ_ALL_RABBITMQ_USER", "fixture");
      values.put("QQQ_ALL_RABBITMQ_PASSWORD", "fixture");
      values.put("QQQ_ALL_SMTP_HOST", "mailpit.example.test");
      values.put("QQQ_ALL_OIDC_BASE_URL", "https://keycloak.example.test/realms/demo");
      values.put("QQQ_ALL_OIDC_CLIENT_ID", "qqq-all");
      values.put("QQQ_ALL_OIDC_CLIENT_SECRET", "fixture");
      values.put("QQQ_ALL_OPENSEARCH_HOST", "127.0.0.1");
      values.put("QQQ_ALL_OPENSEARCH_PORT", "1");
      return values;
   }
}
