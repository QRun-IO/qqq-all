/* Give the full-profile demo admin service-level ESB visibility and controls.
   The viewer role keeps table READ without broker-wide message access. */
INSERT INTO permission (name, object_type, object_label)
VALUES
   ('esbView.hasAccess', 'app', 'ESB'),
   ('esbOperate.hasAccess', 'process', 'ESB operations'),
   ('esbDelete.hasAccess', 'process', 'ESB message deletion')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permission_int (role_id, permission_id)
SELECT role.id, permission.id
FROM role CROSS JOIN permission
WHERE role.name = 'admin'
   AND permission.name IN ('esbView.hasAccess', 'esbOperate.hasAccess', 'esbDelete.hasAccess')
ON CONFLICT (role_id, permission_id) DO NOTHING;
