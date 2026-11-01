/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import com.kingsrook.qbits.assistant.AssistantQBitConfig;
import com.kingsrook.qbits.assistant.model.AssistantProvider;
import com.kingsrook.qbits.assistant.provider.anthropic.AnthropicModelProvider;
import com.kingsrook.qbits.assistant.provider.anthropic.AnthropicProviderType;
import com.kingsrook.qbits.assistant.provider.openai.OpenAiCompatibleProviderType;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;


/*******************************************************************************
 ** The assistant on the enterprise test server: two provider rows, kept in
 ** step with the environment at every start.
 **
 ** - "Anthropic (Claude)": type anthropic, key variable ANTHROPIC_API_KEY
 **   (only the NAME is stored; enterprise-server.sh passes the value from its
 **   private 0600 secrets file). Enabled when the variable is set.
 ** - "Local Ollama": type openai-compatible, kind OLLAMA, base URL
 **   http://127.0.0.1:11434/v1, model QQQ_ASSISTANT_OLLAMA_MODEL (default
 **   llama3.3:70b-instruct-q4_K_M), no key. Enabled when Ollama answers and
 **   lists that model.
 **
 ** The default is Anthropic when its key is set, else Ollama when reachable;
 ** with neither, both rows are disabled and GET /assistant/config answers
 ** NOT_CONFIGURED with a message that says what to configure.
 *******************************************************************************/
final class EnterpriseAssistant
{
   private static final QLogger LOG = QLogger.getLogger(EnterpriseAssistant.class);

   static final String ANTHROPIC_ROW     = "Anthropic (Claude)";
   static final String OLLAMA_ROW        = "Local Ollama";
   static final String ANTHROPIC_KEY_ENV = "ANTHROPIC_API_KEY";
   static final String OLLAMA_MODEL_ENV  = "QQQ_ASSISTANT_OLLAMA_MODEL";
   static final String OLLAMA_URL_ENV    = "QQQ_ASSISTANT_OLLAMA_URL";
   static final String OLLAMA_BASE_URL   = "http://127.0.0.1:11434/v1";
   static final String OLLAMA_MODEL      = "llama3.3:70b-instruct-q4_K_M";

   /////////////////////////////////////////////////////////////////////////////
   // a local 70B model reads the tool catalog slowly: give a turn and one    //
   // model call room (the qbit defaults are 300 s and 120 s)                 //
   /////////////////////////////////////////////////////////////////////////////
   static final Integer MAX_TURN_SECONDS        = 900;
   static final Integer OLLAMA_REQUEST_SECONDS  = 600;



   /*******************************************************************************
    ** Which provider answers, from the environment at startup.
    *******************************************************************************/
   enum Choice
   {
      ANTHROPIC,
      OLLAMA,
      NONE
   }



   private EnterpriseAssistant()
   {
   }



   /*******************************************************************************
    ** The QBit config: both provider types, the Ollama URL allowed as a base
    ** URL override, and the code-registered Anthropic fallback.
    *******************************************************************************/
   static AssistantQBitConfig config(String backendName, String uiApiBaseUrl, Function<String, String> env)
   {
      return new AssistantQBitConfig()
         .withBackendName(backendName).withUiApiBaseUrl(uiApiBaseUrl)
         .withProviderType(new AnthropicProviderType())
         .withProviderType(new OpenAiCompatibleProviderType())
         .withAllowedBaseUrls(Set.of(ollamaBaseUrl(env)))
         .withMaxTurnSeconds(MAX_TURN_SECONDS);
   }



   /*******************************************************************************
    ** Anthropic when its key is set, else Ollama when reachable, else none.
    *******************************************************************************/
   static Choice choose(Function<String, String> env, Predicate<String> ollamaHasModel)
   {
      String key = env.apply(ANTHROPIC_KEY_ENV);
      if(key != null && !key.isBlank())
      {
         return Choice.ANTHROPIC;
      }
      return ollamaHasModel.test(ollamaModel(env)) ? Choice.OLLAMA : Choice.NONE;
   }



   /*******************************************************************************
    ** The Ollama model (QQQ_ASSISTANT_OLLAMA_MODEL, default llama3.3 70B).
    *******************************************************************************/
   static String ollamaModel(Function<String, String> env)
   {
      String model = env.apply(OLLAMA_MODEL_ENV);
      return model == null || model.isBlank() ? OLLAMA_MODEL : model.trim();
   }



   /*******************************************************************************
    ** The Ollama base URL (QQQ_ASSISTANT_OLLAMA_URL, default 127.0.0.1:11434/v1).
    *******************************************************************************/
   static String ollamaBaseUrl(Function<String, String> env)
   {
      String url = env.apply(OLLAMA_URL_ENV);
      return url == null || url.isBlank() ? OLLAMA_BASE_URL : url.trim();
   }



   /*******************************************************************************
    ** The two provider rows for a choice (id left out).
    *******************************************************************************/
   static List<QRecord> rows(Choice choice, Function<String, String> env)
   {
      QRecord anthropic = new QRecord()
         .withValue("name", ANTHROPIC_ROW)
         .withValue("providerType", AnthropicModelProvider.NAME)
         .withValue("apiKeyEnvName", ANTHROPIC_KEY_ENV)
         .withValue("enabled", choice == Choice.ANTHROPIC)
         .withValue("isDefault", choice == Choice.ANTHROPIC);
      QRecord ollama = new QRecord()
         .withValue("name", OLLAMA_ROW)
         .withValue("providerType", new OpenAiCompatibleProviderType().getType())
         .withValue("kind", "OLLAMA")
         .withValue("location", "LOCAL")
         .withValue("baseUrl", ollamaBaseUrl(env))
         .withValue("model", ollamaModel(env))
         .withValue("requestTimeoutSeconds", OLLAMA_REQUEST_SECONDS)
         .withValue("enabled", choice == Choice.OLLAMA)
         .withValue("isDefault", choice == Choice.OLLAMA);
      return List.of(anthropic, ollama);
   }



   /*******************************************************************************
    ** Insert or update the two rows (matched by name) for this start. Runs
    ** with the caller's QContext (the system session at startup).
    *******************************************************************************/
   static Choice reconcile(QInstance instance, Function<String, String> env, Predicate<String> ollamaHasModel) throws QException
   {
      if(instance.getTable(AssistantProvider.TABLE_NAME) == null)
      {
         return Choice.NONE;
      }
      Choice choice = choose(env, ollamaHasModel);
      List<QRecord> wanted = rows(choice, env);

      QueryInput queryInput = new QueryInput(AssistantProvider.TABLE_NAME);
      queryInput.setFilter(new QQueryFilter(new QFilterCriteria("name", QCriteriaOperator.IN, List.of(ANTHROPIC_ROW, OLLAMA_ROW))));
      Map<Serializable, QRecord> existing = new java.util.HashMap<>();
      for(QRecord record : new QueryAction().execute(queryInput).getRecords())
      {
         existing.putIfAbsent(record.getValueString("name"), record);
      }

      //////////////////////////////////////////////////////////////////////////
      // the non-default row first, so marking the default one last leaves it //
      // as the only default (the qbit clears other defaults on save)         //
      //////////////////////////////////////////////////////////////////////////
      List<QRecord> ordered = wanted.stream().sorted((a, b) -> Boolean.compare(Boolean.TRUE.equals(a.getValueBoolean("isDefault")), Boolean.TRUE.equals(b.getValueBoolean("isDefault")))).toList();
      for(QRecord row : ordered)
      {
         QRecord current = existing.get(row.getValueString("name"));
         if(current == null)
         {
            new InsertAction().execute(new InsertInput(AssistantProvider.TABLE_NAME).withRecord(row));
         }
         else
         {
            row.setValue("id", current.getValue("id"));
            new UpdateAction().execute(new UpdateInput(AssistantProvider.TABLE_NAME).withRecord(row));
         }
      }
      return choice;
   }



   /*******************************************************************************
    ** Reconcile from the process environment; a failure is logged and leaves
    ** the server running (the assistant then reports NOT_CONFIGURED).
    *******************************************************************************/
   static void reconcileAtStartup(QInstance instance)
   {
      try
      {
         Choice choice = reconcile(instance, System::getenv, model -> ollamaHasModel(ollamaBaseUrl(System::getenv), model));
         LOG.info("Enterprise assistant model provider: " + choice);
      }
      catch(QException | RuntimeException e)
      {
         LOG.warn("Could not set up the enterprise assistant provider rows", e);
      }
   }



   /*******************************************************************************
    ** True when Ollama answers GET {base}/models within 3 s and lists the model.
    *******************************************************************************/
   static boolean ollamaHasModel(String baseUrl, String model)
   {
      try
      {
         HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
         HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/models")).timeout(Duration.ofSeconds(3)).GET().build();
         HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
         return response.statusCode() == 200 && response.body().contains("\"" + model + "\"");
      }
      catch(Exception e)
      {
         if(e instanceof InterruptedException)
         {
            Thread.currentThread().interrupt();
         }
         return false;
      }
   }
}
