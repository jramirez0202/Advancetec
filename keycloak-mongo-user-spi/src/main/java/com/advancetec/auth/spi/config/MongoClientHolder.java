package com.advancetec.auth.spi.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.keycloak.component.ComponentModel;

/**
 * Cliente de MongoDB compartido por todas las instancias del provider.
 * MongoClient ya maneja pooling internamente, por eso basta con un solo
 * cliente estatico para todo el proceso de Keycloak.
 *
 * La config se resuelve en este orden de prioridad:
 *   1. Campos del provider en la Admin Console (mongoUri, mongoDatabase,
 *      mongoCollection - ver MongoUserStorageProviderFactory#getConfigProperties)
 *   2. Variables de entorno:
 *        ADVANCETEC_MONGO_URI       -> ej. mongodb://mongo:27017
 *        ADVANCETEC_MONGO_DATABASE  -> ej. advancetec_auth
 *        ADVANCETEC_MONGO_COLLECTION-> ej. users
 *   3. Defaults hardcodeados (localhost:27017 / advancetec_auth / users)
 *
 * El MongoClient en si se crea una sola vez con la URI de la primera
 * llamada: en el caso normal (un solo provider de Mongo configurado por
 * Keycloak) esto es equivalente a leer la config siempre, pero si llegaras
 * a registrar mas de una instancia del provider con URIs distintas, todas
 * comparten el cliente creado por la primera.
 */
public final class MongoClientHolder {

    private static volatile MongoClient client;

    public static final String CONFIG_URI = "mongoUri";
    public static final String CONFIG_DATABASE = "mongoDatabase";
    public static final String CONFIG_COLLECTION = "mongoCollection";

    public static final String ENV_URI = "ADVANCETEC_MONGO_URI";
    public static final String ENV_DATABASE = "ADVANCETEC_MONGO_DATABASE";
    public static final String ENV_COLLECTION = "ADVANCETEC_MONGO_COLLECTION";

    private MongoClientHolder() {
    }

    public static MongoClient getClient(ComponentModel model) {
        if (client == null) {
            synchronized (MongoClientHolder.class) {
                if (client == null) {
                    String uri = resolve(model, CONFIG_URI, ENV_URI, "mongodb://localhost:27017");
                    client = MongoClients.create(uri);
                }
            }
        }
        return client;
    }

    public static MongoDatabase getDatabase(ComponentModel model) {
        String dbName = resolve(model, CONFIG_DATABASE, ENV_DATABASE, "advancetec_auth");
        return getClient(model).getDatabase(dbName);
    }

    public static String getCollectionName(ComponentModel model) {
        return resolve(model, CONFIG_COLLECTION, ENV_COLLECTION, "users");
    }

    private static String resolve(ComponentModel model, String configKey, String envKey, String defaultValue) {
        String envOrDefault = System.getenv().getOrDefault(envKey, defaultValue);
        if (model == null) {
            return envOrDefault;
        }
        return model.get(configKey, envOrDefault);
    }
}
