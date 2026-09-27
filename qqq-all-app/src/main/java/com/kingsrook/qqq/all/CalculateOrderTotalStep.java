/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.actions.scripts.ExecuteCodeAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.scripts.ExecuteCodeInput;
import com.kingsrook.qqq.backend.core.model.actions.scripts.ExecuteCodeOutput;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeType;

/** Execute a fixed, side-effect-free JavaScript calculation through QQQ. */
public class CalculateOrderTotalStep implements BackendStep
{
   @Override
   public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
   {
      Integer quantity = input.getValueInteger("quantity");
      Integer unitPriceCents = input.getValueInteger("unitPriceCents");
      if(quantity == null || quantity < 1 || quantity > 1000
         || unitPriceCents == null || unitPriceCents < 0 || unitPriceCents > 1_000_000)
      {
         throw new QException("Supply quantity 1..1000 and unitPriceCents 0..1000000");
      }
      ExecuteCodeOutput result = new ExecuteCodeOutput();
      new ExecuteCodeAction().run(new ExecuteCodeInput()
         .withCodeReference(new QCodeReference().withCodeType(QCodeType.JAVA_SCRIPT)
            .withInlineCode("return quantity * unitPriceCents;"))
         .withInput(Map.of("quantity", quantity, "unitPriceCents", unitPriceCents)), result);
      output.addValue("totalCents", ((Number) result.getOutput()).intValue());
   }
}
