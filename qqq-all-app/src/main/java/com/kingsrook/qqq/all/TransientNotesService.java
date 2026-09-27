/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QRuntimeServiceInterface;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;

/** Seed and clear only the application's transient memory table. */
public class TransientNotesService implements QRuntimeServiceInterface
{
   private static final QLogger LOG = QLogger.getLogger(TransientNotesService.class);
   private QInstance instance;

   @Override
   public String getName()
   {
      return "transientDemoNotes";
   }

   @Override
   public void start(QInstance instance) throws QException
   {
      this.instance = instance;
      var previous = QContext.capture();
      try
      {
         QContext.init(instance, new QSystemUserSession());
         clearNotes();
         new InsertAction().execute(new InsertInput("demoNote").withRecords(List.of(
            new QRecord().withValue("id", 1).withValue("text", "Transient demo note"))));
      }
      catch(QException | RuntimeException failure)
      {
         try
         {
            clearNotes();
         }
         catch(Exception cleanup)
         {
            failure.addSuppressed(cleanup);
         }
         throw failure;
      }
      finally
      {
         QContext.init(previous);
      }
   }

   @Override
   public void stop()
   {
      var previous = QContext.capture();
      try
      {
         QContext.init(instance, new QSystemUserSession());
         clearNotes();
      }
      catch(Exception failure)
      {
         LOG.warn("Could not clear transient demo notes", failure);
      }
      finally
      {
         QContext.init(previous);
      }
   }

   private void clearNotes() throws QException
   {
      new DeleteAction().execute(new DeleteInput("demoNote").withQueryFilter(new QQueryFilter()));
   }
}
