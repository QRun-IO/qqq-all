/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kingsrook.qqq.backend.core.actions.interfaces.CountInterface;
import com.kingsrook.qqq.backend.core.actions.interfaces.DeleteInterface;
import com.kingsrook.qqq.backend.core.actions.interfaces.InsertInterface;
import com.kingsrook.qqq.backend.core.actions.interfaces.QStorageInterface;
import com.kingsrook.qqq.backend.core.actions.interfaces.QueryInterface;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.modules.backend.QBackendModuleDispatcher;
import com.kingsrook.qqq.backend.module.filesystem.base.actions.AbstractBaseFilesystemAction;
import com.kingsrook.qqq.backend.module.filesystem.s3.S3BackendModule;
import com.kingsrook.qqq.backend.module.filesystem.s3.actions.AbstractS3Action;
import com.kingsrook.qqq.backend.module.filesystem.s3.actions.S3CountAction;
import com.kingsrook.qqq.backend.module.filesystem.s3.actions.S3DeleteAction;
import com.kingsrook.qqq.backend.module.filesystem.s3.actions.S3InsertAction;
import com.kingsrook.qqq.backend.module.filesystem.s3.actions.S3QueryAction;
import com.kingsrook.qqq.backend.module.filesystem.s3.actions.S3StorageAction;

/** Routes every S3 operation to the configured MinIO endpoint. */
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

   private static AmazonS3 client(QBackendMetaData metadata)
   {
      MinioBackendMetaData minio = (MinioBackendMetaData) metadata;
      return AmazonS3ClientBuilder.standard()
         .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(minio.getEndpoint(), minio.getRegion()))
         .withPathStyleAccessEnabled(true)
         .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(minio.getAccessKey(), minio.getSecretKey())))
         .build();
   }

   @Override
   public AbstractBaseFilesystemAction<S3ObjectSummary> getActionBase()
   {
      return new AbstractS3Action()
      {
         @Override
         protected AmazonS3 buildAmazonS3ClientFromBackendMetaData(QBackendMetaData metadata)
         {
            return client(metadata);
         }
      };
   }

   @Override
   public QueryInterface getQueryInterface()
   {
      return new S3QueryAction()
      {
         @Override
         protected AmazonS3 buildAmazonS3ClientFromBackendMetaData(QBackendMetaData metadata)
         {
            return client(metadata);
         }
      };
   }

   @Override
   public InsertInterface getInsertInterface()
   {
      return new S3InsertAction()
      {
         @Override
         protected AmazonS3 buildAmazonS3ClientFromBackendMetaData(QBackendMetaData metadata)
         {
            return client(metadata);
         }
      };
   }

   @Override
   public CountInterface getCountInterface()
   {
      return new S3CountAction()
      {
         @Override
         protected AmazonS3 buildAmazonS3ClientFromBackendMetaData(QBackendMetaData metadata)
         {
            return client(metadata);
         }
      };
   }

   @Override
   public DeleteInterface getDeleteInterface()
   {
      return new S3DeleteAction()
      {
         @Override
         protected AmazonS3 buildAmazonS3ClientFromBackendMetaData(QBackendMetaData metadata)
         {
            return client(metadata);
         }
      };
   }

   @Override
   public QStorageInterface getStorageInterface()
   {
      return new S3StorageAction()
      {
         @Override
         protected AmazonS3 buildAmazonS3ClientFromBackendMetaData(QBackendMetaData metadata)
         {
            return client(metadata);
         }
      };
   }
}
