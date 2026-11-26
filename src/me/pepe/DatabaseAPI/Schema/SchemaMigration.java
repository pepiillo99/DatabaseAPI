package me.pepe.DatabaseAPI.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SchemaMigration {
    private final String id;
    private final String description;
    private final List<SchemaChange> changes;
    public SchemaMigration(String id, String description, SchemaChange... changes) {
        if (id == null || !id.matches("[A-Za-z0-9_.-]{1,100}") || description == null || description.trim().isEmpty() || changes == null) {
            throw new IllegalArgumentException("Identificador y descripción de migración obligatorios.");
        }
        this.id = id;
        this.description = description;
        this.changes = Collections.unmodifiableList(new ArrayList<SchemaChange>(Arrays.asList(changes)));
    }
    public String getId() {
        return id;
    }
    public String getDescription() {
        return description;
    }
    public List<SchemaChange> getChanges() {
        return changes;
    }
}
