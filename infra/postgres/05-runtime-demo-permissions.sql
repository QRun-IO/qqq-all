-- Read-only API catalog, transient notes, and the fixed JavaScript calculation.
INSERT INTO permission (name, object_type, object_label)
VALUES
   ('apiCatalog.read', 'table', 'API Catalog'),
   ('demoNote.read', 'table', 'Demo Note (Transient)'),
   ('demoNote.insert', 'table', 'Demo Note (Transient)'),
   ('demoNote.edit', 'table', 'Demo Note (Transient)'),
   ('demoNote.delete', 'table', 'Demo Note (Transient)'),
   ('calculateOrderTotal.hasAccess', 'process', 'Calculate Order Total (JavaScript)')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permission_int (role_id, permission_id)
SELECT role.id, permission.id
FROM role CROSS JOIN permission
WHERE permission.name IN ('apiCatalog.read', 'demoNote.read', 'demoNote.insert',
   'demoNote.edit', 'demoNote.delete', 'calculateOrderTotal.hasAccess')
   AND (role.name = 'admin' OR (role.name = 'viewer' AND permission.name IN ('apiCatalog.read', 'demoNote.read')))
ON CONFLICT (role_id, permission_id) DO NOTHING;
