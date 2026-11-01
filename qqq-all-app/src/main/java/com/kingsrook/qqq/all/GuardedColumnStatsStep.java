/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.io.Serializable;
import java.util.Collection;
import com.kingsrook.qqq.backend.core.actions.permissions.fieldaccess.FieldAccessHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.fieldaccess.FieldAccessTier;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.columnstats.ColumnStatsStep;

/**
 * Column statistics only for fields the caller may read in full.
 *
 * Statistics expose values (min, max, the most common values), so a field
 * whose access tier for the caller is REVEALABLE, MASKED or HIDDEN is refused
 * with a permission error before anything is computed. The result records are
 * then tagged with the analysed table, so the UI API applies that table's
 * field-access policy to them instead of withholding table-less records.
 * The process shape belongs in qqq core (QRun-IO/qqq#1367); this is the host's
 * safe check until then.
 */
public class GuardedColumnStatsStep extends ColumnStatsStep
{
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      String tableName = input.getValueString("tableName");
      String fieldName = input.getValueString("fieldName");
      QTableMetaData table = tableName == null ? null : QContext.getQInstance().getTable(tableName);
      assertFullAccess(table, fieldName);

      super.run(input, output);

      if(table != null)
      {
         for(Serializable value : output.getValues().values())
         {
            tag(table.getName(), value);
         }
      }
   }

   /**
    * Refuse statistics for a field the caller cannot read in full; a joined
    * field ("otherTable.field") is checked on its own table.
    */
   static void assertFullAccess(QTableMetaData table, String fieldName) throws QPermissionDeniedException
   {
      if(table == null || fieldName == null)
      {
         return;
      }
      QTableMetaData fieldTable = table;
      String name = fieldName;
      int dot = fieldName.indexOf('.');
      if(dot > 0 && QContext.getQInstance().getTable(fieldName.substring(0, dot)) != null)
      {
         fieldTable = QContext.getQInstance().getTable(fieldName.substring(0, dot));
         name = fieldName.substring(dot + 1);
      }
      FieldAccessTier tier = FieldAccessHelper.getTier(fieldTable, name);
      if(tier != FieldAccessTier.FULL)
      {
         String label = fieldTable.getField(name) == null ? name : fieldTable.getField(name).getLabel();
         throw new QPermissionDeniedException("Column statistics are not available for " + label
            + ": you do not have full access to this field.");
      }
   }

   private static void tag(String tableName, Serializable value)
   {
      if(value instanceof QRecord record && record.getTableName() == null)
      {
         record.setTableName(tableName);
      }
      else if(value instanceof Collection<?> collection)
      {
         for(Object element : collection)
         {
            if(element instanceof QRecord record && record.getTableName() == null)
            {
               record.setTableName(tableName);
            }
         }
      }
   }
}
