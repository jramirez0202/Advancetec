# Advancetec — Keycloak Mongo User Storage SPI

Base del sistema de autenticación de Advancetec. Este módulo es un
**Keycloak User Storage SPI**: un plugin que se instala dentro de Keycloak
para que, en vez de guardar los usuarios en su base de datos interna,
los busque y valide contra una **colección de usuarios ya existente en
MongoDB**.

## Arquitectura

```
                 ┌─────────────────────────────┐
                 │           Keycloak           │
                 │                               │
Login ─────────▶ │  MongoUserStorageProvider     │ ───▶ MongoDB
                 │  (este SPI)                   │      colección "users"
                 │                               │
                 │  emite JWT firmado (RS256)    │
                 └───────────────┬───────────────┘
                                 │ Authorization: Bearer <JWT>
                                 ▼
                 ┌─────────────────────────────┐
                 │   Microservicios Java        │
                 │   (Resource Servers)         │
                 │   - todos los endpoints son  │
                 │     privados por defecto     │
                 └─────────────────────────────┘
```

Flujo:
1. El usuario hace login contra Keycloak.
2. Keycloak, en vez de mirar su propia base, delega en este SPI, que
   consulta Mongo (`MongoUserRepository`) y valida el password (bcrypt).
3. Si es válido, Keycloak emite un JWT (access token) firmado con su
   clave privada.
4. Los microservicios reciben ese JWT en el header `Authorization`, lo
   validan contra el JWKS de Keycloak (`/realms/{realm}/protocol/openid-connect/certs`)
   y solo entonces exponen sus métodos — **todo es privado por defecto**.

## Estructura del proyecto

```
keycloak-mongo-user-spi/
├── pom.xml
├── docker-compose.yml
└── src/main/
    ├── java/com/advancetec/auth/spi/
    │   ├── config/MongoClientHolder.java       -> conexión a Mongo
    │   ├── repository/MongoUserRepository.java -> queries a la colección "users"
    │   ├── model/MongoUserEntity.java          -> documento Mongo "crudo"
    │   ├── model/MongoUserAdapter.java         -> UserModel que ve Keycloak
    │   └── provider/
    │       ├── MongoUserStorageProvider.java        -> lookup + validación de password
    │       └── MongoUserStorageProviderFactory.java -> registro del SPI
    └── resources/META-INF/services/
        └── org.keycloak.storage.UserStorageProviderFactory
```

## Cómo se conecta a tu colección real

Todo el acceso a Mongo pasa por `MongoUserRepository`. Si tu colección
usa otros nombres de campo (por ejemplo `correo` en vez de `email`, o
guarda el hash con otra clave), el único lugar que hay que tocar es el
método `toEntity(Document doc)` de esa clase.

Campos que el repo espera hoy en cada documento:
- `username`, `email`, `firstName`, `lastName`
- `passwordHash` (bcrypt)
- `enabled`, `emailVerified` (booleanos, opcionales)
- `attributes` (subdocumento libre, opcional)

## Build

Compilado y verificado con Maven + JDK 17+ (`mvn clean package`, build
`SUCCESS`).

```bash
mvn clean package
# genera target/keycloak-mongo-user-spi.jar con mongodb-driver y jbcrypt
# ya empaquetados adentro (via maven-shade-plugin)
```

## Levantar todo en local

```bash
docker compose up -d
```

Esto levanta Mongo en `localhost:27017` y Keycloak en `localhost:8081`
(admin/admin), con el jar del SPI ya montado en `/opt/keycloak/providers/`.

## Activar el provider en Keycloak

1. Entra a la Admin Console (`http://localhost:8081`), crea o entra al
   realm `advancetec`.
2. **User Federation → Add provider → advancetec-mongo-user-provider**.
3. Completa los campos `mongoUri` / `mongoDatabase` / `mongoCollection`
   (o déjalos vacíos para usar las variables de entorno del
   `docker-compose.yml`, y si tampoco existen, los defaults hardcodeados
   de `MongoClientHolder`) y guarda.
4. En **Users** deberías ver ahora los usuarios que existen en la
   colección de Mongo.

## Próximos pasos para escalar esto

- [ ] Definir el realm `advancetec` y sus roles/clients en Keycloak.
- [ ] Mapear roles: ¿vienen del propio documento Mongo (`attributes.roles`)
      o se administran 100% dentro de Keycloak?
- [ ] Configurar el/los microservicios Java como Resource Server
      (Spring Security OAuth2 Resource Server, validación contra el JWKS).
- [ ] Decidir estrategia multi-tenant si aplica (realm por tenant vs.
      claim `tenant_id` en el JWT).
- [ ] Tests de integración con Testcontainers (Mongo + Keycloak) para
      no depender de que el ambiente de dev esté siempre levantado.
