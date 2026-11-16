-- Ruoli applicativi
INSERT INTO roles (name) VALUES
('ROLE_ADMIN'),
('ROLE_USER');

-- Utenti iniziali (password salvate come hash BCrypt, forza 10)
--   admin / Admin123!
--   user  / User123!
INSERT INTO users (username, password) VALUES
('admin', '$2a$10$Z0yxvC67Dtlm8V3peNqkXe0XMMT9eHTGkoSWAPtebD2.P62GeOUpe'),
('user',  '$2a$10$ZUDPdSiUEkyDihosbmZtk.Vx5PW/xxDPA8AsOta96DFKddrehsvWC');

-- Associazione utente -> ruolo
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'user' AND r.name = 'ROLE_USER';
