package com.advancetec.auth.spi.provider;

import com.advancetec.auth.spi.model.MongoUserAdapter;
import com.advancetec.auth.spi.model.MongoUserEntity;
import com.advancetec.auth.spi.repository.MongoUserRepository;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.stream.Stream;

/**
 * Provider que Keycloak invoca cada vez que necesita buscar un usuario o
 * validar su password. Toda la logica de acceso a datos vive en
 * MongoUserRepository; esta clase solo hace de puente hacia la API de Keycloak.
 *
 * NOTA: las firmas exactas de UserQueryProvider pueden variar levemente
 * entre versiones de Keycloak (algunas usan Stream, otras List). Ajusta
 * segun el jar de keycloak-server-spi que estes usando (ver <keycloak.version>
 * en el pom.xml).
 */
public class MongoUserStorageProvider implements
        UserStorageProvider,
        UserLookupProvider,
        UserQueryProvider,
        CredentialInputValidator {

    private final KeycloakSession session;
    private final ComponentModel model;
    private final MongoUserRepository repository;

    public MongoUserStorageProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
        this.repository = new MongoUserRepository(model);
    }

    // ---- UserLookupProvider ----

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);
        MongoUserEntity entity = repository.findById(externalId);
        return toAdapter(realm, entity);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        MongoUserEntity entity = repository.findByUsername(username);
        return toAdapter(realm, entity);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        MongoUserEntity entity = repository.findByEmail(email);
        return toAdapter(realm, entity);
    }

    // ---- UserQueryProvider (listado / busqueda, ej. en la consola de admin) ----

    @Override
    public int getUsersCount(RealmModel realm) {
        return (int) repository.count();
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, String search, Integer firstResult, Integer maxResults) {
        int first = firstResult == null ? 0 : firstResult;
        int max = maxResults == null ? 50 : maxResults;
        List<MongoUserEntity> found = repository.search(search, first, max);
        return found.stream().map(entity -> toAdapter(realm, entity));
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, java.util.Map<String, String> params, Integer firstResult, Integer maxResults) {
        String search = params.getOrDefault("username", params.get("email"));
        return searchForUserStream(realm, search, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group, Integer firstResult, Integer maxResults) {
        // Los grupos en este SPI se resuelven vía Keycloak (federated storage),
        // no viven en la coleccion de Mongo. Se deja vacio a proposito.
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        return Stream.empty();
    }

    // ---- CredentialInputValidator (validacion de password) ----

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType()) || !(input instanceof org.keycloak.models.UserCredentialModel)) {
            return false;
        }
        String rawPassword = input.getChallengeResponse();
        MongoUserEntity entity = repository.findByUsername(user.getUsername());
        if (entity == null || entity.getPasswordHash() == null) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, entity.getPasswordHash());
    }

    @Override
    public void close() {
        // El MongoClient es compartido (ver MongoClientHolder) y no se cierra aqui.
    }

    private UserModel toAdapter(RealmModel realm, MongoUserEntity entity) {
        if (entity == null) {
            return null;
        }
        return new MongoUserAdapter(session, realm, model, entity);
    }
}
