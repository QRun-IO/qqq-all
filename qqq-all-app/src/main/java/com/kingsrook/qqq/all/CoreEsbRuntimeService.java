/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import com.kingsrook.qqq.backend.core.instances.QRuntimeServiceInterface;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.esb.runtime.QEsbRuntime;

/** Launches the ESB subscriber through the common QQQ application launcher. */
public class CoreEsbRuntimeService implements QRuntimeServiceInterface
{
   @Override
   public String getName()
   {
      return "coreEsb";
   }

   @Override
   public void start(QInstance instance)
   {
      QEsbRuntime.getInstance().start(instance);
   }

   @Override
   public void stop()
   {
      QEsbRuntime.getInstance().stop();
   }
}
