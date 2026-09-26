/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import io.javalin.config.JavalinConfig;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

/** Local receiver that makes successful order webhook delivery visible in the core demo. */
public class DemoOrderWebhookReceiver implements QJavalinRouteProviderInterface
{
   private static final List<String> RECEIPTS = new CopyOnWriteArrayList<>();

   static void clear()
   {
      RECEIPTS.clear();
   }

   @Override
   public void setQInstance(QInstance instance)
   {
   }

   @Override
   public void acceptJavalinConfig(JavalinConfig config)
   {
      config.routes.apiBuilder(() ->
      {
         post("/demo/order-webhook", context ->
         {
            String body = context.body();
            if(body.length() > 8192)
            {
               context.status(413);
               return;
            }
            RECEIPTS.add(body);
            if(RECEIPTS.size() > 20)
            {
               RECEIPTS.removeFirst();
            }
            context.status(204);
         });
         get("/demo/order-webhook-receipts", context -> context.json(List.copyOf(RECEIPTS)));
      });
   }
}
