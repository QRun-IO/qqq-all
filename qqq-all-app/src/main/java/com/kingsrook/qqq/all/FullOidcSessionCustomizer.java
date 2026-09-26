/*
 * Copyright 2026 Kingsrook, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.kingsrook.qqq.all;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.kingsrook.qbits.userrolepermissions.model.Role;
import com.kingsrook.qbits.userrolepermissions.utils.PermissionManager;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;
import org.json.JSONArray;
import org.json.JSONObject;

/** Resolves Keycloak realm roles through qbit-user-role-permissions. */
public class FullOidcSessionCustomizer implements QAuthenticationModuleCustomizerInterface
{
   @Override
   public void customizeSession(QInstance instance, QSession session, Map<String, Object> context)
   {
      Set<String> roles = realmRoles(context.get("idToken") instanceof JSONObject idToken ? idToken : null);
      if(roles.isEmpty() && context.get("jwtPayloadJsonObject") instanceof JSONObject accessToken)
      {
         roles = realmRoles(accessToken);
      }
      try
      {
         session.setPermissions(permissionsForRoles(roles));
      }
      catch(QException e)
      {
         throw new IllegalStateException("Could not resolve OIDC role permissions", e);
      }
   }

   private static Set<String> realmRoles(JSONObject claims)
   {
      if(claims == null)
      {
         return Set.of();
      }
      JSONObject realmAccess = claims.optJSONObject("realm_access");
      JSONArray values = realmAccess == null ? null : realmAccess.optJSONArray("roles");
      if(values == null)
      {
         return Set.of();
      }
      Set<String> roles = new HashSet<>();
      for(Integer i = 0; i < values.length(); i++)
      {
         if(values.opt(i) instanceof String role && !role.isBlank())
         {
            roles.add(role);
         }
      }
      return roles;
   }

   protected Set<String> permissionsForRoles(Set<String> roles) throws QException
   {
      if(roles.isEmpty())
      {
         return Set.of();
      }
      Set<Integer> roleIds = new QueryAction().execute(new QueryInput(Role.TABLE_NAME)
            .withFilter(new QQueryFilter().withCriteria("name", QCriteriaOperator.IN, roles)))
         .getRecords().stream().map(record -> record.getValueInteger("id")).collect(Collectors.toSet());
      return PermissionManager.getInstance().getEffectivePermissionsForRoles(roleIds);
   }
}
