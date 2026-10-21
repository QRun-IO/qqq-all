/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.modules.backend.QBackendModuleDispatcher;
import com.kingsrook.qqq.backend.module.filesystem.s3.S3BackendModule;

/** Uses the shared S3 actions with MinIO endpoint metadata. */
public final class MinioBackendModule extends S3BackendModule
{
   static
   {
      QBackendModuleDispatcher.registerBackendModule(new MinioBackendModule());
   }

   @Override
   public String getBackendType()
   {
      return "minio";
   }

   @Override
   public Class<? extends QBackendMetaData> getBackendMetaDataClass()
   {
      return MinioBackendMetaData.class;
   }
}
