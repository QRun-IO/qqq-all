/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.AssistantQBitProducer;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The enterprise assistant's provider rows follow the environment: Anthropic with a key, else Ollama, else none. */
class EnterpriseAssistantTest
{
   @TempDir Path dataDirectory;

   @AfterEach
   void clear()
   {
      QContext.clear();
      MemoryRecordStore.fullReset();
   }

   @Test
   void choiceFollowsKeyThenOllama()
   {
      assertEquals(EnterpriseAssistant.Choice.ANTHROPIC, EnterpriseAssistant.choose(env(Map.of("ANTHROPIC_API_KEY", "sk-test")), model -> true));
      assertEquals(EnterpriseAssistant.Choice.OLLAMA, EnterpriseAssistant.choose(env(Map.of("ANTHROPIC_API_KEY", " ")), model -> true));
      assertEquals(EnterpriseAssistant.Choice.NONE, EnterpriseAssistant.choose(env(Map.of()), model -> false));
   }

   @Test
   void ollamaModelAndUrlFromEnvironment()
   {
      List<String> asked = new java.util.ArrayList<>();
      EnterpriseAssistant.choose(env(Map.of("QQQ_ASSISTANT_OLLAMA_MODEL", "qwen3-coder:30b")), model -> asked.add(model));
      assertEquals(List.of("qwen3-coder:30b"), asked);
      assertEquals("llama3.3:70b-instruct-q4_K_M", EnterpriseAssistant.ollamaModel(env(Map.of())));
      assertEquals("http://127.0.0.1:11434/v1", EnterpriseAssistant.ollamaBaseUrl(env(Map.of())));

      AssistantQBitConfig config = EnterpriseAssistant.config("h2", "http://127.0.0.1:8078", env(Map.of("QQQ_ASSISTANT_OLLAMA_URL", "http://127.0.0.1:9999/v1")));
      assertTrue(config.getAllowedBaseUrls().contains("http://127.0.0.1:9999/v1"));
      assertTrue(config.getProviderTypes().containsKey("anthropic"));
      assertTrue(config.getProviderTypes().containsKey("openai-compatible"));
   }

   @Test
   void rowsNeverHoldAKey()
   {
      List<QRecord> rows = EnterpriseAssistant.rows(EnterpriseAssistant.Choice.ANTHROPIC, env(Map.of("ANTHROPIC_API_KEY", "sk-secret")));
      for(QRecord row : rows)
      {
         assertFalse(row.getValues().values().stream().anyMatch(v -> String.valueOf(v).contains("sk-secret")));
      }
      assertEquals("ANTHROPIC_API_KEY", rows.get(0).getValueString("apiKeyEnvName"));
      assertNull(rows.get(1).getValue("apiKeyEnvName"));
   }

   @Test
   void reconcileInsertsThenSwitchesTheDefault() throws Exception
   {
      QInstance instance = new QqqAllApplication(dataDirectory, "tcp://127.0.0.1:61616").defineQInstance();
      new AssistantQBitProducer().withQBitConfig(EnterpriseAssistant.config("transientMemory", "http://127.0.0.1:1", env(Map.of()))).produce(instance);
      QContext.init(instance, new QSystemUserSession());

      assertEquals(EnterpriseAssistant.Choice.OLLAMA, EnterpriseAssistant.reconcile(instance, env(Map.of()), model -> true));
      Map<String, QRecord> rows = providerRows();
      assertEquals(2, rows.size());
      assertTrue(rows.get(EnterpriseAssistant.OLLAMA_ROW).getValueBoolean("isDefault"));
      assertTrue(rows.get(EnterpriseAssistant.OLLAMA_ROW).getValueBoolean("enabled"));
      assertEquals("OLLAMA", rows.get(EnterpriseAssistant.OLLAMA_ROW).getValueString("kind"));
      assertEquals("llama3.3:70b-instruct-q4_K_M", rows.get(EnterpriseAssistant.OLLAMA_ROW).getValueString("model"));
      assertFalse(rows.get(EnterpriseAssistant.ANTHROPIC_ROW).getValueBoolean("enabled"));

      assertEquals(EnterpriseAssistant.Choice.ANTHROPIC, EnterpriseAssistant.reconcile(instance, env(Map.of("ANTHROPIC_API_KEY", "sk-test")), model -> true));
      rows = providerRows();
      assertEquals(2, rows.size());
      assertTrue(rows.get(EnterpriseAssistant.ANTHROPIC_ROW).getValueBoolean("isDefault"));
      assertTrue(rows.get(EnterpriseAssistant.ANTHROPIC_ROW).getValueBoolean("enabled"));
      assertFalse(rows.get(EnterpriseAssistant.OLLAMA_ROW).getValueBoolean("isDefault"));
      assertFalse(rows.get(EnterpriseAssistant.OLLAMA_ROW).getValueBoolean("enabled"));

      assertEquals(EnterpriseAssistant.Choice.NONE, EnterpriseAssistant.reconcile(instance, env(Map.of()), model -> false));
      assertTrue(providerRows().values().stream().noneMatch(row -> row.getValueBoolean("enabled")));
   }

   private static Map<String, QRecord> providerRows() throws Exception
   {
      return new QueryAction().execute(new QueryInput(AssistantProvider.TABLE_NAME)).getRecords().stream()
         .collect(Collectors.toMap(row -> row.getValueString("name"), row -> row));
   }

   private static Function<String, String> env(Map<String, String> values)
   {
      return new HashMap<>(values)::get;
   }
}
