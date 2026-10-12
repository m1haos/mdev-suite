package com.mdevstudio.core.storage;

import java.sql.Connection;
import java.sql.SQLException;

/** A piece of database work run on the database thread with the shared connection. */
@FunctionalInterface
public interface SqlWork<T> {

    T run(Connection connection) throws SQLException;
}
