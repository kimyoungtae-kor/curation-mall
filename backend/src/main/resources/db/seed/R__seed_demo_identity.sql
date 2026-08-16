INSERT INTO users (
    id, email, normalized_email, password_hash, name, phone, status
) VALUES
    (
        '11000000-0000-0000-0000-000000000001',
        'demo@example.com',
        'demo@example.com',
        '$2a$10$11TXKGTuxAgb./F5KvDgLeHxj2lK1nLD6Z/9A2l7wW2bv/IUtErlO',
        '김데모',
        '01012345678',
        'ACTIVE'
    ),
    (
        '11000000-0000-0000-0000-000000000002',
        'admin@example.com',
        'admin@example.com',
        '$2a$10$11TXKGTuxAgb./F5KvDgLeHxj2lK1nLD6Z/9A2l7wW2bv/IUtErlO',
        '관리자',
        '01000000000',
        'ACTIVE'
    )
ON CONFLICT (normalized_email) DO UPDATE SET
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    name = EXCLUDED.name,
    phone = EXCLUDED.phone,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
  FROM users u
  JOIN roles r ON r.code = 'CUSTOMER'
 WHERE u.normalized_email = 'demo@example.com'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
  FROM users u
  JOIN roles r ON r.code = 'ADMIN'
 WHERE u.normalized_email = 'admin@example.com'
ON CONFLICT DO NOTHING;
