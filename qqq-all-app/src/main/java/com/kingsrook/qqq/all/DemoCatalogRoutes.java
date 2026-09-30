/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import io.javalin.config.JavalinConfig;
import static io.javalin.apibuilder.ApiBuilder.get;

/** Synthetic public supplier data, owned by the same server as the reference app. */
public class DemoCatalogRoutes implements QJavalinRouteProviderInterface
{
   private static final List<Map<String, Object>> CATALOG = List.of(
      Map.of("id", 101, "name", "Starter Kit", "unitPriceCents", 2500),
      Map.of("id", 102, "name", "Refill Pack", "unitPriceCents", 1250),
      Map.of("id", 103, "name", "Shipping Box", "unitPriceCents", 300));

   @Override
   public void setQInstance(QInstance instance)
   {
   }

   @Override
   public void acceptJavalinConfig(JavalinConfig config)
   {
      config.routes.apiBuilder(() ->
      {
         get("/demo/catalog/{id}", context ->
         {
            try
            {
               int id = Integer.parseInt(context.pathParam("id"));
               var row = CATALOG.stream().filter(item -> Integer.valueOf(id).equals(item.get("id"))).findFirst();
               if(row.isPresent())
               {
                  context.json(row.get());
               }
               else
               {
                  context.status(404);
               }
            }
            catch(NumberFormatException e)
            {
               context.status(400);
            }
         });
         get("/demo/catalog", context ->
         {
            try
            {
               int skip = Integer.parseInt(context.queryParamAsClass("skip", String.class).getOrDefault("0"));
               int limit = Integer.parseInt(context.queryParamAsClass("limit", String.class).getOrDefault("2"));
               Integer id = context.queryParam("id") == null ? null : Integer.valueOf(context.queryParam("id"));
               if(skip < 0 || limit < 1 || limit > 1000)
               {
                  context.status(400);
                  return;
               }
               context.json(CATALOG.stream().filter(row -> id == null || id.equals(row.get("id")))
                  .skip(skip).limit(limit).toList());
            }
            catch(NumberFormatException e)
            {
               context.status(400);
            }
         });
      });
   }
}
