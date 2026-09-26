/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import com.kingsrook.qqq.backend.module.rdbms.strategy.BaseRDBMSActionStrategy;

/** PostgreSQL syntax for the shared QQQ RDBMS actions. */
public class PostgresActionStrategy extends BaseRDBMSActionStrategy
{
   @Override
   public String getIdentifierQuoteString()
   {
      return "\"";
   }

   @Override
   public String getInsertDefaultValuesClause()
   {
      return "DEFAULT VALUES";
   }

   @Override
   protected int bindParamObject(PreparedStatement statement, int index, Object value) throws SQLException
   {
      if(value == null)
      {
         statement.setNull(index, Types.NULL);
         return 1;
      }
      return super.bindParamObject(statement, index, value);
   }
}
