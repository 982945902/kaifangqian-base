UPDATE sys_app_info
SET app_address = REPLACE(app_address, 'http://localhost', 'http://localhost:8080')
WHERE app_address LIKE 'http://localhost%';
