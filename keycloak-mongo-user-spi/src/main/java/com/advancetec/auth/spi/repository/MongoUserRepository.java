package com.advancetec.auth.spi.repository;

import com.advancetec.auth.spi.config.MongoClientHolder;
import com.advancetec.auth.spi.model.MongoUserEntity;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.keycloak.component.ComponentModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unico punto de contacto con la coleccion "users" de MongoDB.
 * El resto del SPI (provider, adapter) nunca habla con Mongo directamente,
 * siempre pasa por aqui. Esto es lo que vas a tocar si la coleccion real
 * usa otros nombres de campo.
 */
public class MongoUserRepository {

    private final ComponentModel model;

    public MongoUserRepository(ComponentModel model) {
        this.model = model;
    }

    private MongoCollection<Document> collection() {
        return MongoClientHolder.getDatabase(model).getCollection(MongoClientHolder.getCollectionName(model));
    }

    public MongoUserEntity findByUsername(String username) {
        Document doc = collection().find(Filters.eq("username", username)).first();
        return toEntity(doc);
    }

    public MongoUserEntity findByEmail(String email) {
        Document doc = collection().find(Filters.eq("email", email)).first();
        return toEntity(doc);
    }

    public MongoUserEntity findById(String id) {
        Document doc = collection().find(Filters.eq("_id", new ObjectId(id))).first();
        return toEntity(doc);
    }

    public List<MongoUserEntity> search(String searchTerm, int firstResult, int maxResults) {
        List<MongoUserEntity> results = new ArrayList<>();
        Document filter = (searchTerm == null || searchTerm.isBlank())
                ? new Document()
                : new Document("$or", List.of(
                        new Document("username", java.util.regex.Pattern.compile(searchTerm, java.util.regex.Pattern.CASE_INSENSITIVE)),
                        new Document("email", java.util.regex.Pattern.compile(searchTerm, java.util.regex.Pattern.CASE_INSENSITIVE))
                ));

        for (Document doc : collection().find(filter).skip(Math.max(firstResult, 0)).limit(Math.max(maxResults, 0))) {
            results.add(toEntity(doc));
        }
        return results;
    }

    public long count() {
        return collection().countDocuments();
    }

    private MongoUserEntity toEntity(Document doc) {
        if (doc == null) {
            return null;
        }
        MongoUserEntity entity = new MongoUserEntity();
        entity.setId(doc.getObjectId("_id").toHexString());
        entity.setUsername(doc.getString("username"));
        entity.setEmail(doc.getString("email"));
        entity.setFirstName(doc.getString("firstName"));
        entity.setLastName(doc.getString("lastName"));
        entity.setPasswordHash(doc.getString("passwordHash"));
        entity.setEnabled(doc.getBoolean("enabled", true));
        entity.setEmailVerified(doc.getBoolean("emailVerified", false));

        Map<String, String> attrs = new HashMap<>();
        Document rawAttrs = doc.get("attributes", Document.class);
        if (rawAttrs != null) {
            for (String key : rawAttrs.keySet()) {
                Object value = rawAttrs.get(key);
                if (value != null) {
                    attrs.put(key, value.toString());
                }
            }
        }
        entity.setAttributes(attrs);

        return entity;
    }
}
