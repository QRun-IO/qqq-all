/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import javax.script.ScriptEngineManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The reference application must ship its advertised backend and language. */
class RuntimeAssemblyTest
{
   @Test
   void includesApiBackendAndJavaScriptEngine()
   {
      assertDoesNotThrow(() -> Class.forName("com.kingsrook.qqq.backend.module.api.APIBackendModule"));
      assertDoesNotThrow(() -> Class.forName("com.kingsrook.qqq.languages.javascript.QJavaScriptExecutor"));
      assertNotNull(new ScriptEngineManager().getEngineByName("JavaScript"));
   }
}
