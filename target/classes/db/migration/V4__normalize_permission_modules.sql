-- Normalize permission module names to be uppercase and consistent

-- Update old V1 permissions to use uppercase module names
UPDATE permissions SET module = 'USERS' WHERE module = 'user';
UPDATE permissions SET module = 'ORDERS' WHERE module = 'order';
UPDATE permissions SET module = 'PAYMENTS' WHERE module = 'payment';
UPDATE permissions SET module = 'INVENTORY' WHERE module = 'inventory';
UPDATE permissions SET module = 'CREDIT' WHERE module = 'credit';
UPDATE permissions SET module = 'MERCHANTS' WHERE module = 'merchant';
UPDATE permissions SET module = 'REPORTS' WHERE module = 'report';
UPDATE permissions SET module = 'ADMIN' WHERE module = 'settings';
