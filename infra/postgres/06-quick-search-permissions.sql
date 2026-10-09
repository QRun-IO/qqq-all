/* Give the full-profile demo admin the quick-search admin app, its processes, and
   its operational tables. qbit-quick-search 1.0 protects the app and processes
   with quickSearchAdmin.hasAccess and the tables with read/write permissions.
   The viewer role keeps searching customers through customer.read. */
INSERT INTO permission (name, object_type, object_label)
VALUES
   ('quickSearchAdmin.hasAccess', 'app', 'Quick Search Admin'),
   ('quickSearchIndex.read', 'table', 'quickSearchIndex'),
   ('quickSearchIndex.write', 'table', 'quickSearchIndex'),
   ('quickSearchIndexRun.read', 'table', 'quickSearchIndexRun'),
   ('quickSearchIndexRun.write', 'table', 'quickSearchIndexRun'),
   ('quickSearchFailedEvent.read', 'table', 'quickSearchFailedEvent'),
   ('quickSearchFailedEvent.write', 'table', 'quickSearchFailedEvent')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permission_int (role_id, permission_id)
SELECT role.id, permission.id
FROM role CROSS JOIN permission
WHERE role.name = 'admin'
   AND permission.name IN ('quickSearchAdmin.hasAccess',
      'quickSearchIndex.read', 'quickSearchIndex.write',
      'quickSearchIndexRun.read', 'quickSearchIndexRun.write',
      'quickSearchFailedEvent.read', 'quickSearchFailedEvent.write')
ON CONFLICT (role_id, permission_id) DO NOTHING;
