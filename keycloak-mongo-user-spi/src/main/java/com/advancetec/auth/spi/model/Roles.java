package com.advancetec.auth.spi.model;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Identificadores canonicos de rol usados en todo el sistema Advancetec.
 *
 * Convencion de la plataforma: el CODIGO (valores de rol, nombres de campo,
 * claims del JWT, query params de filtros, etc.) siempre va en ingles. La
 * traduccion ES/EN de lo que ve un humano (labels, mensajes, opciones de
 * filtro en una UI) se resuelve en la capa de presentacion de cada
 * microservicio/frontend, nunca en estos identificadores.
 *
 * Estos mismos valores deben existir como Realm Roles en Keycloak
 * (Admin Console -> Realm roles) para que MongoUserAdapter pueda
 * resolverlos y que terminen en el claim "realm_access.roles" del JWT.
 */
public final class Roles {

    public static final String ADMIN = "admin";
    public static final String OPERATOR = "operator";

    public static final Set<String> KNOWN_ROLES = Set.of(ADMIN, OPERATOR);

    // Si la coleccion real todavia guarda los roles en espanol
    // ("administrador", "operador"), se normalizan aqui al valor canonico
    // en ingles para que el resto del sistema (JWT, resource servers) solo
    // vea "admin" / "operator". Ajusta este mapa si tu coleccion usa otras
    // variantes.
    private static final Map<String, String> ALIASES = Map.of(
            "administrador", ADMIN,
            "operador", OPERATOR
    );

    private Roles() {
    }

    /** Normaliza un valor de rol crudo (tal como viene de Mongo) a su forma canonica en ingles. */
    public static String normalize(String rawRole) {
        if (rawRole == null) {
            return null;
        }
        String lower = rawRole.trim().toLowerCase(Locale.ROOT);
        if (KNOWN_ROLES.contains(lower)) {
            return lower;
        }
        return ALIASES.getOrDefault(lower, lower);
    }
}
