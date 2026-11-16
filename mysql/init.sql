CREATE DATABASE IF NOT EXISTS book_service_db;
CREATE DATABASE IF NOT EXISTS member_service_db;
CREATE DATABASE IF NOT EXISTS loan_service_db;
CREATE DATABASE IF NOT EXISTS auth_service_db;

-- Privilegi necessari: CREATE/ALTER/DROP per Flyway (auth-service) e ddl-auto=update, DML per l'applicazione
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON book_service_db.* TO 'librarian'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON member_service_db.* TO 'librarian'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON loan_service_db.* TO 'librarian'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON auth_service_db.* TO 'librarian'@'%';
FLUSH PRIVILEGES;
