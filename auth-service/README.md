# Advancetec — Auth Service

Base del sistema de autenticación de Advancetec (módulo `auth-service`,
`artifactId` Maven `advancetec-auth-service`). Hoy este módulo es un
**Keycloak User Storage SPI**: un plugin que se instala dentro de Keycloak
para que, en vez de guardar los usuarios en su base de datos interna,
los busque y valide contra una **colección de usuarios ya existente en
MongoDB**. El nombre del módulo es deliberadamente genérico (por dominio,
no por tecnología) para que si el mecanismo interno cambia el nombre
siga siendo correcto.

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
auth-service/
├── pom.xml
├── docker-compose.yml
└── src/main/
    ├── java/com/advancetec/auth/spi/
    │   ├── config/MongoClientHolder.java       -> conexión a Mongo
    │   ├── repository/MongoUserRepository.java -> queries a la colección "users"
    │   ├── model/MongoUserEntity.java          -> documento Mongo "crudo"
    │   ├── model/MongoUserAdapter.java         -> UserModel que ve Keycloak
    │   ├── model/Roles.java                    -> IDs canónicos de rol (admin/operator)
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
- `roles` (array de strings, opcional — ver sección **Roles** abajo). También
  acepta un campo singular `role` si tu colección solo guarda uno por usuario.
- `attributes` (subdocumento libre, opcional)

## Roles

Los roles se leen del propio registro Mongo (campo `roles`, o `role` si es
uno solo) y Keycloak los expone en el JWT (`realm_access.roles`) para que
los microservicios autoricen con eso — no hay una tabla de roles aparte que
mantener sincronizada.

Roles soportados hoy (valores canónicos, ver `Roles.java`):
- `admin`
- `operator`

Si tu colección ya guarda los roles en español (`administrador`,
`operador`), `Roles.normalize()` los traduce automáticamente al valor
canónico en inglés — no hace falta migrar los datos. Para que el mapeo
tenga efecto, esos mismos roles deben existir como **Realm Roles** en
Keycloak (Admin Console → Realm roles → crea `admin` y `operator`); si un
usuario trae un rol que no existe ahí, se ignora en vez de romper el login.

Agregar un rol nuevo: añade la constante en `Roles.java` (y su alias en
español si aplica) y crea el Realm Role correspondiente en Keycloak.

## Convención de idioma (EN/ES)

Esta convención aplica a este módulo y a todo lo que se construya después
sobre esta base (microservicios, frontend):

- **Código, modelos, nombres de campo, valores/IDs internos (roles,
  estados, claims del JWT), parámetros de filtros/query**: siempre en
  inglés (ej. `role=admin`, no `rol=administrador`). Esto es lo que viaja
  entre sistemas y lo que persiste en la base de datos.
- **Todo lo que ve un humano** (labels de UI, mensajes de error/validación,
  texto de opciones en un filtro, notificaciones): en **inglés y español**.
  Esa traducción vive en la capa de presentación de cada
  servicio/frontend (resource bundles / i18n), nunca mezclada con los
  identificadores internos.

Este SPI no tiene UI propia (solo el formulario de configuración en la
Admin Console de Keycloak, que es para operadores técnicos), así que hoy
no requiere bundles de traducción — pero los valores de rol ya siguen la
regla (inglés) para que el resto de la plataforma escale sin retrabajo.

## Build

Compilado y verificado con Maven + JDK 17+ (`mvn clean package`, build
`SUCCESS`).

```bash
mvn clean package
# genera target/advancetec-auth-service.jar con mongodb-driver y jbcrypt
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
2. **Realm roles → Create role**: crea `admin` y `operator` (ver sección
   **Roles** arriba) — deben existir antes de que el login mapee roles.
3. **User Federation → Add provider → advancetec-mongo-user-provider**.
4. Completa los campos `mongoUri` / `mongoDatabase` / `mongoCollection`
   (o déjalos vacíos para usar las variables de entorno del
   `docker-compose.yml`, y si tampoco existen, los defaults hardcodeados
   de `MongoClientHolder`) y guarda.
5. En **Users** deberías ver ahora los usuarios que existen en la
   colección de Mongo, con sus roles ya mapeados.

## Próximos pasos para escalar esto

- [ ] Definir el realm `advancetec` y sus clients en Keycloak.
- [x] Mapear roles: vienen del documento Mongo (`roles` / `role`), se
      normalizan a inglés y se resuelven contra Realm Roles de Keycloak
      (ver sección **Roles**). Hoy: `admin`, `operator`.
- [ ] Configurar el/los microservicios Java como Resource Server
      (Spring Security OAuth2 Resource Server, validación contra el JWKS).
- [ ] Decidir estrategia multi-tenant si aplica (realm por tenant vs.
      claim `tenant_id` en el JWT).
- [ ] Tests de integración con Testcontainers (Mongo + Keycloak) para
      no depender de que el ambiente de dev esté siempre levantado.
- [ ] Auditoría de login/roles: hoy el rol queda disponible en el JWT
      (`realm_access.roles`) porque se lee del registro Mongo en cada
      login, pero no se escribe ningún log/evento aparte. Si además
      necesitas un registro de auditoría explícito (quién entró, con qué
      rol, cuándo), falta decidir dónde vive (¿colección Mongo aparte?
      ¿Event Listener SPI de Keycloak?) — no implementado todavía.
