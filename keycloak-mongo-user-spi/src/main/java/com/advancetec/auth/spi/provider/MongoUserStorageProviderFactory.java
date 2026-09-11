package com.advancetec.auth.spi.provider;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

/**
 * Punto de entrada del SPI. Keycloak descubre esta clase por el archivo
 * src/main/resources/META-INF/services/org.keycloak.storage.UserStorageProviderFactory
 * y la muestra en Admin Console -> User Federation -> Add provider.
 */
public class MongoUserStorageProviderFactory implements UserStorageProviderFactory<MongoUserStorageProvider> {

    public static final String PROVIDER_ID = "advancetec-mongo-user-provider";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Federa usuarios desde la coleccion de MongoDB de Advancetec en vez de la base interna de Keycloak.";
    }

    @Override
    public MongoUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        return new MongoUserStorageProvider(session, model);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        // Estos campos aparecen en el formulario de configuracion del provider
        // dentro de la Admin Console, por si prefieres configurarlos ahi en vez
        // de por variable de entorno (ver MongoClientHolder).
        return List.of(
                new ProviderConfigProperty(
                        "mongoUri", "Mongo connection URI", "Ej. mongodb://mongo:27017",
                        ProviderConfigProperty.STRING_TYPE, "mongodb://localhost:27017"),
                new ProviderConfigProperty(
                        "mongoDatabase", "Mongo database", "Nombre de la base de datos",
                        ProviderConfigProperty.STRING_TYPE, "advancetec_auth"),
                new ProviderConfigProperty(
                        "mongoCollection", "Mongo collection", "Nombre de la coleccion de usuarios",
                        ProviderConfigProperty.STRING_TYPE, "users")
        );
    }
}
