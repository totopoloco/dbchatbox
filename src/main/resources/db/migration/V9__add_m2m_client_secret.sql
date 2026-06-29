-- V9: Add m2m_client_secret to tenant so the API-key path can obtain a
--     Keycloak service-account token without a user JWT.
ALTER TABLE tenant ADD COLUMN m2m_client_secret VARCHAR(200);

UPDATE tenant SET m2m_client_secret = 'ChangeMe-WAT-M2M' WHERE slug = 'wat-simmering';
UPDATE tenant SET m2m_client_secret = 'ChangeMe-URW-M2M' WHERE slug = 'union-rot-weiss';
UPDATE tenant SET m2m_client_secret = 'ChangeMe-ASV-M2M' WHERE slug = 'asv-pressbaum-badminton';
