package com.mdevstudio.core.storage;

/** The few statements that differ between SQLite and MySQL/MariaDB. */
public enum Dialect {

    SQLITE("ON CONFLICT(%s) DO UPDATE SET ", "excluded.%s"),
    // VALUES() is deprecated in MySQL 8.0.20 but still works there, and MariaDB has no alias syntax.
    MYSQL("ON DUPLICATE KEY UPDATE ", "VALUES(%s)");

    private final String conflictClause;
    private final String incomingValue;

    Dialect(String conflictClause, String incomingValue) {
        this.conflictClause = conflictClause;
        this.incomingValue = incomingValue;
    }

    /**
     * The tail of an insert that updates the row on a key conflict, e.g.
     * {@code upsert("uuid", "name = " + incoming("name"))}.
     */
    public String upsert(String keyColumns, String assignments) {
        return (this == SQLITE ? conflictClause.formatted(keyColumns) : conflictClause) + assignments;
    }

    /** How the value being inserted is referenced inside {@link #upsert}. */
    public String incoming(String column) {
        return incomingValue.formatted(column);
    }
}
