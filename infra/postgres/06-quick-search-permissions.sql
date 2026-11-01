-- Quick Search 1.x protects its admin processes (quickSearchFullReindex and friends) and its
-- admin app with the quick_search_admin permission; the full-profile admin runs the reindex.
INSERT INTO permission (name, object_type, object_label)
VALUES
   ('quick_search_admin.hasAccess', 'process', 'Quick Search Admin')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permission_int (role_id, permission_id)
SELECT role.id, permission.id
FROM role CROSS JOIN permission
WHERE permission.name = 'quick_search_admin.hasAccess'
   AND role.name = 'admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;
