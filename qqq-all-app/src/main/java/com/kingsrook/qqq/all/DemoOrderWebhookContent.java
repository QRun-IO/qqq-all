/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qbits.webhooks.actions.WebhookEventTypeCustomizerInterface;
import com.kingsrook.qbits.webhooks.model.WebhookEventContent;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;

/** Builds a self-contained demo payload without requiring a separate public API definition. */
public class DemoOrderWebhookContent implements WebhookEventTypeCustomizerInterface
{
   @Override
   public WebhookEventContent buildEventContent(QRecord record, String eventType, String apiName, String apiVersion)
   {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("eventType", eventType);
      body.put("orderNo", record.getValueString("orderNo"));
      body.put("orderId", record.getValueInteger("id"));
      return new WebhookEventContent().withPostBody(JsonUtils.toJson(body));
   }
}
