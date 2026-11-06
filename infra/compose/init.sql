\getenv postgres_user POSTGRES_USER

SELECT format('CREATE DATABASE identity OWNER %I', :'postgres_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'identity')
\gexec

SELECT format('CREATE DATABASE ecommerce OWNER %I', :'postgres_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'ecommerce')
\gexec

SELECT format('CREATE DATABASE media OWNER %I', :'postgres_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'media')
\gexec
