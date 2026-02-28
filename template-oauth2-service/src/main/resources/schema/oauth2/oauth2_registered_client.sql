-- OAuth2 客户端表，与 Spring Authorization Server 官方 schema 一致，支持 IF NOT EXISTS 幂等执行
CREATE TABLE IF NOT EXISTS oauth2_registered_client
(
    id
    varchar
(
    100
) NOT NULL,
    client_id varchar
(
    100
) NOT NULL,
    client_id_issued_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret varchar
(
    200
) DEFAULT NULL,
    client_secret_expires_at timestamp DEFAULT NULL,
    client_name varchar
(
    200
) NOT NULL,
    client_authentication_methods varchar
(
    1000
) NOT NULL,
    authorization_grant_types varchar
(
    1000
) NOT NULL,
    redirect_uris varchar
(
    1000
) DEFAULT NULL,
    post_logout_redirect_uris varchar
(
    1000
) DEFAULT NULL,
    scopes varchar
(
    1000
) NOT NULL,
    client_settings varchar
(
    2000
) NOT NULL,
    token_settings varchar
(
    2000
) NOT NULL,
    PRIMARY KEY
(
    id
)
    );
