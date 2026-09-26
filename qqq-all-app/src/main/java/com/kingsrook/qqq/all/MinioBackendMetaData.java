/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import com.kingsrook.qqq.backend.module.filesystem.s3.model.metadata.S3BackendMetaData;

/** S3 metadata with an explicit endpoint for MinIO. */
public final class MinioBackendMetaData extends S3BackendMetaData
{
   private final String endpoint;

   public MinioBackendMetaData(String endpoint)
   {
      this.endpoint = endpoint;
      setBackendType(MinioBackendModule.class);
   }

   public String getEndpoint()
   {
      return endpoint;
   }
}
