-- Seed default supplier categories
INSERT INTO supplier_categories (name, description)
VALUES
    ('General Merchandise',    'Suppliers of general goods and everyday products'),
    ('Food & Beverages',       'Suppliers of food products, drinks, and consumables'),
    ('Electronics',            'Suppliers of electronic devices and accessories'),
    ('Pharmaceuticals',        'Suppliers of medicines, health products, and medical supplies'),
    ('Stationery & Office',    'Suppliers of office supplies, stationery, and equipment'),
    ('Clothing & Apparel',     'Suppliers of garments, footwear, and fashion accessories'),
    ('Building & Hardware',    'Suppliers of construction materials and hardware'),
    ('Agriculture',            'Suppliers of farm inputs, produce, and agri-products'),
    ('Cleaning & Hygiene',     'Suppliers of cleaning products and hygiene essentials'),
    ('Logistics & Transport',  'Service providers for freight and delivery')
ON CONFLICT (name) DO NOTHING;
