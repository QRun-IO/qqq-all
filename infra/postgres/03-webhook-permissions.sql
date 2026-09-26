-- Add the webhook qbit's protected resources to the demo OIDC roles.
-- Runs after C6's 02-auth-and-roles.sql during PostgreSQL initialization.
INSERT INTO permission (name, object_type, object_label)
SELECT table_name || '.' || action, 'table', table_name
FROM (VALUES
   ('webhook'), ('webhookSubscription'), ('webhookEvent'),
   ('webhookEventContent'), ('webhookEventSendLog'),
   ('scheduledJob'), ('scheduledJobParameter')
) AS tables(table_name)
CROSS JOIN (VALUES ('read'), ('insert'), ('edit'), ('delete')) AS actions(action)
ON CONFLICT (name) DO NOTHING;

INSERT INTO permission (name, object_type, object_label)
SELECT process_name || '.hasAccess', 'process', process_name
FROM (VALUES
   ('SendWebhookEvent'), ('SendTestEventToWebhook'), ('ManageWebhookHealth')
) AS processes(process_name)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permission_int (role_id, permission_id)
SELECT role.id, permission.id
FROM role CROSS JOIN permission
WHERE (role.name = 'admin' OR (role.name = 'viewer' AND permission.name LIKE '%.read'))
   AND (permission.name LIKE 'webhook.%'
      OR permission.name LIKE 'webhookSubscription.%'
      OR permission.name LIKE 'webhookEvent.%'
      OR permission.name LIKE 'webhookEventContent.%'
      OR permission.name LIKE 'webhookEventSendLog.%'
      OR permission.name LIKE 'scheduledJob.%'
      OR permission.name LIKE 'scheduledJobParameter.%'
      OR permission.name IN ('SendWebhookEvent.hasAccess',
         'SendTestEventToWebhook.hasAccess', 'ManageWebhookHealth.hasAccess'))
ON CONFLICT (role_id, permission_id) DO NOTHING;
