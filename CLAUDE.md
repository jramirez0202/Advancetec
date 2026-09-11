# Advancetec — Convenciones de Arquitectura (Microservicios / Spring Boot / MongoDB)

Este documento define cómo se estructura **cada microservicio Spring Boot**
de Advancetec dentro de este monorepo. La idea es que cualquier servicio
nuevo (el que siga al de auth, y los que sigan después) se vea igual en su
esqueleto, aunque el dominio sea distinto.

Este `CLAUDE.md` vive en la raíz del repo `Advancetec` (que hoy es un
monorepo: cada microservicio es una carpeta, no un repo aparte) y Claude
Code lo lee automáticamente al empezar a trabajar en cualquier parte del
repo.

**Excepción explícita:** `keycloak-mongo-user-spi/` **no** sigue esta
estructura — es un plugin de Keycloak (User Storage SPI), sin capa HTTP,
sin Spring Boot y sin DTOs/controllers. Su propia organización y
convenciones están documentadas en `keycloak-mongo-user-spi/README.md`.
Este documento aplica a los microservicios de negocio (resource servers)
que se construyan de aquí en adelante.

## 0. Convenciones que ya están en producción (no discutir de nuevo)

Estas dos reglas ya se decidieron y están implementadas en
`keycloak-mongo-user-spi/` — aplican a **todo** microservicio nuevo, no
solo al de auth:

- **Roles**: los valores canónicos son `admin` y `operator` (en inglés).
  Se leen del registro Mongo del usuario y terminan en el claim
  `realm_access.roles` del JWT que emite Keycloak — ningún microservicio
  debe mantener su propia tabla de roles. Ver
  `keycloak-mongo-user-spi/src/main/java/com/advancetec/auth/spi/model/Roles.java`
  para la lista completa y cómo agregar uno nuevo.
- **Idioma (EN/ES)**: código, modelos, nombres de campo, valores/IDs
  internos (roles, estados, claims, query params de filtros) van siempre
  en **inglés**. Todo lo que ve un humano (labels de UI, mensajes de
  error/validación, opciones de un filtro) va en **inglés y español**,
  resuelto en la capa de presentación (resource bundles / i18n) de cada
  servicio — nunca mezclado con los identificadores internos. Ver el
  detalle en `keycloak-mongo-user-spi/README.md` → "Convención de idioma
  (EN/ES)".

## 1. Principio rector

**Una responsabilidad por clase, una clase por archivo, un propósito por
paquete.** Si una clase empieza a hacer dos cosas (ej. validar Y guardar, o
mapear Y exponer HTTP), se separa. No se combinan responsabilidades salvo que
sea estrictamente necesario (y en ese caso, se documenta el porqué en un
comentario en la clase).

## 2. Estructura de carpetas estándar por microservicio

```
<nombre-servicio>/
├── pom.xml
├── docker-compose.yml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/advancetec/<servicio>/
    │   │   ├── <Servicio>Application.java
    │   │   │
    │   │   ├── config/                <- Beans de configuración de Spring
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── MongoConfig.java
    │   │   │   └── OpenApiConfig.java
    │   │   │
    │   │   ├── controller/            <- SOLO capa HTTP. Recibe request,
    │   │   │   └── UserController.java   delega al service, devuelve response.
    │   │   │                             Nunca tiene lógica de negocio.
    │   │   │
    │   │   ├── service/
    │   │   │   ├── UserService.java   <- interfaz (el contrato)
    │   │   │   └── impl/
    │   │   │       └── UserServiceImpl.java  <- la lógica de negocio real
    │   │   │
    │   │   ├── repository/            <- SOLO acceso a datos (Spring Data
    │   │   │   └── UserRepository.java   MongoRepository). Sin lógica de negocio.
    │   │   │
    │   │   ├── model/                 <- Entidades @Document de Mongo.
    │   │   │   └── User.java             Representan lo que hay en la BD, nada más.
    │   │   │
    │   │   ├── dto/                   <- Lo que entra/sale por HTTP.
    │   │   │   ├── request/              NUNCA se expone `model/` directamente.
    │   │   │   │   └── CreateUserRequest.java
    │   │   │   └── response/
    │   │   │       └── UserResponse.java
    │   │   │
    │   │   ├── mapper/                <- Traduce entre model <-> dto.
    │   │   │   └── UserMapper.java       Responsabilidad única: mapear, nada más.
    │   │   │
    │   │   ├── exception/             <- Excepciones propias del dominio +
    │   │   │   ├── UserNotFoundException.java   manejador global.
    │   │   │   └── GlobalExceptionHandler.java
    │   │   │
    │   │   ├── security/              <- Filtros JWT, integración Keycloak,
    │   │   │   └── JwtAuthFilter.java     lo que valida el token entrante.
    │   │   │
    │   │   └── validation/            <- Validadores custom (@Constraint),
    │   │       └── UniqueEmailValidator.java  si los hay.
    │   │
    │   └── resources/
    │       ├── application.yml
    │       └── application-{profile}.yml
    │
    └── test/
        └── java/com/advancetec/<servicio>/
            ├── controller/            <- Espeja 1:1 la estructura de main/
            ├── service/
            └── repository/
```

**Regla dura:** la estructura de `test/` espeja la de `main/`. Si existe
`service/impl/UserServiceImpl.java`, su test vive en
`test/.../service/impl/UserServiceImplTest.java`. Nada de una carpeta `tests/`
genérica con todo mezclado.

## 3. Flujo de una request (de afuera hacia adentro)

```
Controller  →  Service (interfaz)  →  ServiceImpl  →  Repository  →  Mongo
   ▲                                        │
   │                                        ▼
  DTO  ◀──────────────  Mapper  ◀──────  Model (entity)
```

- El **Controller** nunca toca `Repository` ni `Model` directamente.
- El **Service** nunca construye respuestas HTTP (`ResponseEntity`, códigos
  de estado, etc.) — eso es trabajo del Controller.
- El **Mapper** nunca llama a `Repository` ni contiene lógica de negocio,
  solo transforma objetos.

## 4. Patrones de diseño a seguir

| Patrón | Dónde se usa | Por qué |
|---|---|---|
| **Repository** | `repository/` (Spring Data `MongoRepository`) | Aislar el acceso a datos del resto de la app |
| **DTO** | `dto/request`, `dto/response` | Nunca exponer el modelo de persistencia por HTTP |
| **Mapper** | `mapper/` | Separar la traducción entity↔DTO de la lógica de negocio |
| **Factory** | Ej. `MongoUserStorageProviderFactory` del SPI de Keycloak | Cuando la construcción de un objeto depende de configuración externa |
| **Strategy** | Cuando haya más de una forma de autorizar/autenticar (ej. distintos tipos de credenciales) | Evita `if/else` gigantes en el service |
| **Builder** | Objetos con muchos campos opcionales (ej. respuestas complejas) | Legibilidad y evitar constructores telescópicos |
| **Dependency Injection por constructor** | Todos los `@Service`, `@RestController`, `@Component` | Nunca `@Autowired` en campos — siempre constructor (permite testear con mocks fácilmente) |
| **Global Exception Handler** | `exception/GlobalExceptionHandler.java` con `@ControllerAdvice` | Un solo lugar que traduce excepciones a respuestas HTTP consistentes |

## 5. Convenciones de nombres

- **Clases**: `PascalCase`, con sufijo que delata su rol:
  `UserController`, `UserService`, `UserServiceImpl`, `UserRepository`,
  `CreateUserRequest`, `UserResponse`, `UserMapper`, `UserNotFoundException`.
- **Paquetes**: minúsculas, sustantivo singular que describe la
  responsabilidad (`controller`, `service`, `repository`, `model`, `dto`,
  `mapper`, `exception`) — nunca plural (`controllers`) ni términos ad-hoc.
- **Variables/métodos**: `camelCase`, verbos claros (`findByUsername`, no
  `getU` o `process`).
- **Constantes**: `UPPER_SNAKE_CASE`.
- Un microservicio = un `groupId` común `com.advancetec`, `artifactId`
  específico (`advancetec-auth-service`, `advancetec-tracking-service`, etc.)
  para que todos los módulos se vean como parte de la misma familia.

## 6. Reglas específicas para Spring Boot + MongoDB

- Las entidades (`model/`) se anotan con `@Document(collection = "...")`,
  nunca se reusan directamente como request/response de la API.
- Los repositorios extienden `MongoRepository<Entity, String>` (el `String`
  es el `ObjectId` como hex) — sin queries manuales salvo que Spring Data
  no las pueda derivar del nombre del método; en ese caso, `@Query` dentro
  del propio repository, nunca en el service.
- Toda validación de entrada (`@NotBlank`, `@Email`, etc.) vive en los DTOs
  de `dto/request/`, no en el modelo ni en el service.
- Configuración de conexión a Mongo y de seguridad (JWT/Keycloak) vive en
  `config/`, nunca hardcodeada en clases de negocio.

## 7. Sobre "instalar un MCP para mantener el orden"

Un MCP (Model Context Protocol) sirve para que Claude Code se conecte a
herramientas externas (Jira, GitHub, bases de datos, etc.) — no es lo que
mantiene el orden de carpetas de un proyecto. Lo que realmente resuelve tu
necesidad es este archivo como **`CLAUDE.md`** en la raíz del repo:
Claude Code lo lee automáticamente al empezar a trabajar y lo trata como
reglas obligatorias del proyecto, así no "improvisa" estructura.

Si en cambio lo que buscas es automatizar la *verificación* de que el
código cumple esta estructura (por ejemplo, un check en CI que falle si
alguien mete lógica de negocio en un controller), eso es un tema aparte
(un linter/arquetipo, no un MCP) y lo podemos armar después.

## 8. Próximos pasos

- [x] Este archivo vive como `CLAUDE.md` en la raíz de `Advancetec`.
- [ ] Definir el `groupId`/`artifactId` oficial y el nombre corto de cada
      servicio previsto para el MVP (el `groupId` común ya es
      `com.advancetec`, ver `keycloak-mongo-user-spi/pom.xml`).
- [ ] Decidir si se arma un arquetipo Maven / template con este esqueleto
      ya creado, para no repetir la estructura a mano cada vez que se
      agregue un microservicio Spring Boot nuevo.
