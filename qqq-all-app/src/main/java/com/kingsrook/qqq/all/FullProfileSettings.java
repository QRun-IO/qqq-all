/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.List;
import java.util.Map;

/** Validated environment settings for the external services in the full profile. */
final class FullProfileSettings
{
   private static final List<String> REQUIRED = List.of(
      "BIND_HOST",
      "POSTGRES_HOST", "POSTGRES_DATABASE", "POSTGRES_USER", "POSTGRES_PASSWORD",
      "MYSQL_HOST", "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD",
      "MONGO_HOST", "MONGO_DATABASE", "MONGO_USER", "MONGO_PASSWORD", "MONGO_REPLICA_SET",
      "S3_ENDPOINT", "S3_BUCKET", "S3_ACCESS_KEY", "S3_SECRET_KEY",
      "SFTP_HOST", "SFTP_USER", "SFTP_PASSWORD",
      "ARTEMIS_URL", "ARTEMIS_USER", "ARTEMIS_PASSWORD", "ARTEMIS_MANAGEMENT_URL",
      "RABBITMQ_URL", "RABBITMQ_USER", "RABBITMQ_PASSWORD", "RABBITMQ_MANAGEMENT_URL",
      "SMTP_HOST", "OIDC_BASE_URL", "OIDC_CLIENT_ID", "OIDC_CLIENT_SECRET",
      "OPENSEARCH_HOST");

   private final Map<String, String> values;

   private FullProfileSettings(Map<String, String> values)
   {
      this.values = Map.copyOf(values);
   }

   static FullProfileSettings from(Map<String, String> environment)
   {
      for(String name : REQUIRED)
      {
         if(environment.get("QQQ_ALL_" + name) == null || environment.get("QQQ_ALL_" + name).isBlank())
         {
            throw new IllegalArgumentException("Missing required environment variable QQQ_ALL_" + name);
         }
      }
      return new FullProfileSettings(environment);
   }

   String get(String name)
   {
      return values.get("QQQ_ALL_" + name);
   }

   String getOrDefault(String name, String defaultValue)
   {
      String value = get(name);
      return value == null || value.isBlank() ? defaultValue : value;
   }

   Integer port(String name, String defaultValue)
   {
      Integer port = Integer.valueOf(getOrDefault(name, defaultValue));
      if(port < 1 || port > 65535)
      {
         throw new IllegalArgumentException("Invalid port in QQQ_ALL_" + name);
      }
      return port;
   }
}
