package com.advancetec.auth.spi.model;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.util.HashSet;
import java.util.Set;

/**
 * Traduce un MongoUserEntity (nuestro modelo) al UserModel que Keycloak
 * entiende internamente. Keycloak solo interactua con esta clase; nunca
 * toca MongoDB directamente.
 */
public class MongoUserAdapter extends AbstractUserAdapterFederatedStorage {

    private final MongoUserEntity entity;
    private final String keycloakId;

    public MongoUserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, MongoUserEntity entity) {
        super(session, realm, model);
        this.entity = entity;
        this.keycloakId = StorageId.keycloakId(model, entity.getId());
    }

    @Override
    public String getId() {
        return keycloakId;
    }

    @Override
    public String getUsername() {
        return entity.getUsername();
    }

    @Override
    public void setUsername(String username) {
        entity.setUsername(username);
    }

    @Override
    public String getEmail() {
        return entity.getEmail();
    }

    @Override
    public void setEmail(String email) {
        entity.setEmail(email);
    }

    @Override
    public String getFirstName() {
        return entity.getFirstName();
    }

    @Override
    public void setFirstName(String firstName) {
        entity.setFirstName(firstName);
    }

    @Override
    public String getLastName() {
        return entity.getLastName();
    }

    @Override
    public void setLastName(String lastName) {
        entity.setLastName(lastName);
    }

    @Override
    public boolean isEnabled() {
        return entity.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        entity.setEnabled(enabled);
    }

    @Override
    public boolean isEmailVerified() {
        return entity.isEmailVerified();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        entity.setEmailVerified(verified);
    }

    /**
     * Roles del usuario: se leen del registro Mongo (entity.getRoles(),
     * valores canonicos en ingles - ver Roles.java) y se resuelven contra
     * los Realm Roles ya creados en Keycloak, sumados a lo que ya traiga
     * el federated storage (super). Si un rol del documento no existe
     * todavia como Realm Role en Keycloak, se ignora en vez de romper el
     * login.
     */
    @Override
    protected Set<RoleModel> getRoleMappingsInternal() {
        Set<RoleModel> roles = new HashSet<>(super.getRoleMappingsInternal());
        for (String roleName : entity.getRoles()) {
            RoleModel role = realm.getRole(roleName);
            if (role != null) {
                roles.add(role);
            }
        }
        return roles;
    }

    /** Acceso directo al documento Mongo original, util dentro del provider. */
    public MongoUserEntity getEntity() {
        return entity;
    }
}
