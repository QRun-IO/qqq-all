/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.io.Serializable;
import java.util.Map;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.module.api.actions.BaseAPIActionUtil;

/** The local catalog supports ascending ID pages and optional exact ID selection. */
public class DemoCatalogApi extends BaseAPIActionUtil
{
   @Override
   public String buildUrlSuffixForSingleRecordGet(Serializable primaryKey) throws QException
   {
      try
      {
         return "/" + Integer.parseInt(String.valueOf(primaryKey));
      }
      catch(NumberFormatException e)
      {
         throw new QException("Invalid catalog ID", e);
      }
   }

   @Override
   protected Integer getApiStandardLimit()
   {
      return 2;
   }

   @Override
   protected String buildQueryStringForGet(QQueryFilter filter, Integer limit, Integer skip, Map<String, QFieldMetaData> fields) throws QException
   {
      String query = "?limit=" + (limit == null ? 2 : limit) + "&skip=" + (skip == null ? 0 : skip);
      if(filter != null)
      {
         if(!filter.getSubFilters().isEmpty() || filter.getBooleanOperator() != QQueryFilter.BooleanOperator.AND
            || filter.getCriteria().size() > 1 || filter.getOrderBys().stream().anyMatch(order -> !"id".equals(order.getFieldName()) || !order.getIsAscending()))
         {
            throw new QException("Demo catalog supports ascending ID order and one exact ID filter only");
         }
         if(!filter.getCriteria().isEmpty())
         {
            var criterion = filter.getCriteria().getFirst();
            if(!"id".equals(criterion.getFieldName()) || criterion.getOperator() != QCriteriaOperator.EQUALS || criterion.getValues().size() != 1)
            {
               throw new QException("Demo catalog supports one exact ID filter only");
            }
            try
            {
               query += "&id=" + Integer.parseInt(String.valueOf(criterion.getValues().getFirst()));
            }
            catch(NumberFormatException e)
            {
               throw new QException("Invalid catalog ID", e);
            }
         }
      }
      return query;
   }

   @Override
   public CountOutput doCount(QTableMetaData table, CountInput input) throws QException
   {
      QQueryFilter filter = input.getFilter() == null ? new QQueryFilter() : input.getFilter().clone();
      filter.setLimit(null);
      filter.setSkip(null);
      return new CountOutput().withCount(doQuery(table, new QueryInput(table.getName()).withFilter(filter)).getRecords().size());
   }
}
