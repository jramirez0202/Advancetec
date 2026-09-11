package com.advancetec.auth.spi.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Representa un documento de la coleccion "users" en MongoDB.
 * Este es el modelo "crudo" que viene de la base de datos, antes de
 * adaptarlo al UserModel que espera Keycloak (ver MongoUserAdapter).
 *
 * Ajusta los nombres de campo aqui si tu coleccion real usa otros nombres
 * (ej. "correo" en vez de "email").
 */
public class MongoUserEntity {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String passwordHash; // hash bcrypt, NUNCA texto plano
    private boolean enabled = true;
    private boolean emailVerified = false;

    // Atributos libres para lo que no mapea 1:1 a un campo estandar de Keycloak
    // (ej. tenantId, rut, telefono, etc.)
    private Map<String, String> attributes = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }
}
