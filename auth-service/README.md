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
├── mongo-init/
│   └── init-users.js       -> seed de arranque, corre solo con el volumen vacío
├── keycloak-import/
│   └── advancetec-realm.json  -> bootstrap del realm (roles, i18n, provider)
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

## Este SPI es de solo lectura

`MongoUserRepository` solo tiene métodos de lectura (`find*`, `search`,
`count`) — no hay ningún camino para escribir de vuelta a Mongo. Es
intencional: la colección la gestiona otro sistema, este SPI la federa
para login, no la administra.

Por eso `MongoUserAdapter` rechaza con `ReadOnlyException` cualquier
intento real de editar `username`, `email`, `firstName` o `lastName`
(por ejemplo desde "Personal info" en el Account Console) — antes esos
setters aceptaban el cambio en silencio, mutaban un objeto en memoria de
esa request nada más, y el dato se perdía sin ningún error visible al
siguiente login.

Dos detalles si tocás esto:
- El chequeo es **"solo lanza si el valor cambia"**, no incondicional.
  Keycloak reinvoca estos mismos setters con el valor ya vigente en
  flujos propios (el required action `VERIFY_PROFILE`, que se dispara en
  el primer login de cada usuario) — lanzar siempre ahí rompe el login
  con un 500. Ver `rejectIfChanged()` en `MongoUserAdapter`.
- `enabled` y `emailVerified` quedan afuera de esta regla a propósito:
  Keycloak los reescribe desde sus propios mecanismos internos (p. ej.
  `VERIFY_PROFILE` fuerza `emailVerified=false` en cada reconfirmación,
  tenga o no sentido de negocio bloquearlo), así que esos dos setters
  simplemente no hacen nada — el valor real siempre sigue viniendo de
  Mongo vía `isEnabled()` / `isEmailVerified()`.
- Por defecto Keycloak tampoco deja tocar el username desde el Account
  Console salvo que actives **Realm settings → General → Edit username**
  (`editUsernameAllowed`, `false` por default) — con eso apagado ni
  siquiera llega el intento a nuestro código.

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
Keycloak — en local ya vienen creados por `keycloak-import/advancetec-realm.json`
(ver **Levantar todo en local**); en otro ambiente se crean a mano en
Admin Console → Realm roles. Si un usuario trae un rol que no existe
ahí, se ignora en vez de romper el login.

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
Admin Console de Keycloak, que es para operadores técnicos) — los valores
de rol ya siguen la regla (inglés) para que el resto de la plataforma
escale sin retrabajo. Las pantallas de **login/account que sí ve el
usuario final** son las que trae Keycloak de fábrica (no las escribimos
nosotros) y ya soportan inglés/español porque `advancetec-realm.json`
activa Internationalization (`en`/`es`) al crear el realm — el selector
de idioma aparece solo, sin que este módulo tenga que hacer nada.
Cuando exista un frontend propio (fuera de este
módulo), esas pantallas custom deberán seguir la misma regla EN/ES con su
propio mecanismo de i18n.

## Build

Compilado y verificado con Maven + JDK 17+ (`mvn clean package`, build
`SUCCESS`).

```bash
mvn clean package
# genera target/advancetec-auth-service.jar con mongodb-driver y jbcrypt
# ya empaquetados adentro (via maven-shade-plugin)
```

## Levantar todo en local (para un dev nuevo)

Requisitos: Docker + Docker Compose, Maven, JDK 17+.

```bash
git clone <este repo>
cd auth-service

mvn clean package          # genera target/advancetec-auth-service.jar
docker compose up -d       # Mongo + Keycloak, con el realm y la data ya listos
```

Con eso alcanza — no hay pasos manuales en la Admin Console. En el
primer `up` (volumen vacío):

- **Mongo** corre `mongo-init/init-users.js` y crea la colección
  `advancetec_auth.users` con 2 usuarios de prueba:

  | username | password    | role       |
  |----------|-------------|------------|
  | `jdoe`   | `Test1234!` | `admin`    |
  | `asmith` | `Test1234!` | `operator` |

  (Ese hash bcrypt es solo para dev/test — no lo reutilices en un
  ambiente real.)

- **Keycloak** arranca con `--import-realm` (ver `command` en
  `docker-compose.yml`) y crea el realm `advancetec` a partir de
  `keycloak-import/advancetec-realm.json`: ya trae los Realm Roles
  `admin`/`operator`, Internationalization en `en`/`es`, y el User
  Federation provider (`advancetec-mongo-user-provider`) apuntando a
  Mongo — todo lo que antes había que clickear a mano en la Admin
  Console.

Probar que quedó andando: entrá a
`http://localhost:8081/realms/advancetec/account/` y logueate con
`jdoe` / `Test1234!`.

Admin Console (para inspeccionar/editar algo puntual):
`http://localhost:8081` → `admin` / `admin`.

**Importante:** `--import-realm` no pisa un realm que ya existe. Un
`docker compose restart` reusa el mismo container (y su estado) y no
reimporta nada; para volver a un estado 100% limpio (realm + Mongo
desde cero) hace falta `docker compose down -v` antes de `up` — eso
también borra el volumen de Mongo, así que se vuelve a correr el seed.

Si tu colección Mongo real ya tiene datos (no es un ambiente de cero),
no uses el seed: sacá el volumen `./mongo-init` del `docker-compose.yml`
o simplemente no toques el volumen de datos existente.

## Cómo agregar/editar el provider a mano

Si necesitás tocar algo que no está en `advancetec-realm.json` (otro
realm, otra config de conexión, etc.), se hace igual que cualquier User
Federation de Keycloak: Admin Console → tu realm → **User Federation →
Add provider → advancetec-mongo-user-provider**, completando
`mongoUri` / `mongoDatabase` / `mongoCollection` (si los dejás vacíos,
cae a las variables de entorno del `docker-compose.yml`, y si tampoco
existen, a los defaults hardcodeados de `MongoClientHolder`).

## Próximos pasos para escalar esto

- [x] Definir el realm `advancetec` (roles, i18n, provider) — automatizado
      en `keycloak-import/advancetec-realm.json`, se crea solo en local.
- [ ] Definir los **clients** de Keycloak que van a usar los
      microservicios/frontend reales (el `account-console` que usamos
      para probar es el que trae Keycloak por defecto, no uno nuestro).
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
