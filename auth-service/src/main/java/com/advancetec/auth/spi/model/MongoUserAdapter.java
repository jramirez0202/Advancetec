package com.advancetec.auth.spi.model;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.storage.ReadOnlyException;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Traduce un MongoUserEntity (nuestro modelo) al UserModel que Keycloak
 * entiende internamente. Keycloak solo interactua con esta clase; nunca
 * toca MongoDB directamente.
 *
 * Este adapter es de SOLO LECTURA: MongoUserRepository no tiene ningun
 * metodo de escritura (por diseno, la coleccion Mongo la gestiona otro
 * sistema). Por eso los setters de los campos que vienen del documento
 * lanzan ReadOnlyException cuando el valor nuevo difiere del actual -
 * sin esto, Keycloak dejaba "guardar" cambios en el Account Console que
 * en realidad solo mutaban un objeto en memoria de esa request y se
 * perdian al siguiente login, sin ningun error visible.
 *
 * IMPORTANTE: el chequeo es "solo lanza si el valor cambia", no
 * incondicional. Keycloak reinvoca estos mismos setters con el valor YA
 * VIGENTE en flujos propios como el required action VERIFY_PROFILE (se
 * dispara en el primer login de cada usuario federado) - lanzar siempre
 * rompe el login con un 500 apenas ese required action reenvia
 * email/nombre/apellido sin cambiarlos. Solo un cambio real debe fallar.
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
        rejectIfChanged("username", entity.getUsername(), username);
    }

    @Override
    public String getEmail() {
        return entity.getEmail();
    }

    @Override
    public void setEmail(String email) {
        rejectIfChanged("email", entity.getEmail(), email);
    }

    @Override
    public String getFirstName() {
        return entity.getFirstName();
    }

    @Override
    public void setFirstName(String firstName) {
        rejectIfChanged("firstName", entity.getFirstName(), firstName);
    }

    @Override
    public String getLastName() {
        return entity.getLastName();
    }

    @Override
    public void setLastName(String lastName) {
        rejectIfChanged("lastName", entity.getLastName(), lastName);
    }

    @Override
    public boolean isEnabled() {
        return entity.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        // A diferencia de username/email/nombre, esto no es un campo que el
        // usuario edite desde un formulario: Keycloak lo toca desde sus
        // propios mecanismos internos (ver setEmailVerified mas abajo). No
        // tiene sentido de negocio bloquearlo con ReadOnlyException; se
        // ignora en silencio y el valor real sigue viniendo de Mongo
        // (isEnabled()) en cada login.
    }

    @Override
    public boolean isEmailVerified() {
        return entity.isEmailVerified();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        // Keycloak reinvoca esto (con "false") como parte interno del
        // required action VERIFY_PROFILE cada vez que se reconfirma el
        // perfil, independientemente de si el usuario cambio algo. No es
        // un intento de edicion real, asi que NO debe lanzar
        // ReadOnlyException (romperia el login) - se ignora en silencio,
        // igual que setEnabled, y el valor real sigue viniendo de Mongo.
    }

    /**
     * Deja pasar sin hacer nada cuando Keycloak reescribe el valor que ya
     * tenia (ver nota de la clase sobre VERIFY_PROFILE); lanza
     * ReadOnlyException solo ante un cambio real, que es lo que
     * corresponde a un intento genuino de editar un campo gestionado en
     * Mongo.
     */
    private void rejectIfChanged(String field, Object currentValue, Object newValue) {
        if (!Objects.equals(currentValue, newValue)) {
            throw new ReadOnlyException(field + " is managed in MongoDB, not editable through Keycloak");
        }
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
