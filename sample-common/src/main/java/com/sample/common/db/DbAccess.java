package com.sample.common.db;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.ResultSet;

abstract public class DbAccess<T extends HasId> {
    private static Log LOGGER = LogFactory.getLog(DbAccess.class);

    private DataSource dataSource;

    abstract public T newInstance();

    public void setDataSource(DataSource source) {
        dataSource = source;
    }

    abstract public PreparedStatement statement4Insert(Connection connection);

    abstract public void setInsertData(PreparedStatement stmt, T instance, int parentId);

    public void insert(T instance, int parentId) throws SQLException {

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement stmt = statement4Insert(connection);
        ) {
            if (stmt == null) {
                throw new SQLException("Invalid SQL statement");
            }
            connection.setAutoCommit(false);
            setInsertData(stmt, instance, parentId);

            try {
                stmt.executeUpdate();
                ResultSet resultSet = stmt.getGeneratedKeys();
                resultSet.next();
                instance.setId(resultSet.getInt(1));
                resultSet.close();
                insertChildren(connection, instance);
                connection.commit();
            } catch (SQLException e0) {
                LOGGER.error("Failed to execute SQL insert", e0);
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                    LOGGER.error("Failed to rollback SQL insert", e1);
                }
                throw e0;
            }
        }
    }

    public void insert2(T instance, int parentId) throws SQLException {

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement stmt = statement4Insert(connection);
        ) {
            Savepoint savepoint = null;
            try {
                stmt.executeUpdate();
                ResultSet resultSet = stmt.getGeneratedKeys();
                resultSet.next();
                instance.setId(resultSet.getInt(1));
                resultSet.close();
                savepoint = connection.setSavepoint();
                insertChildren(connection, instance);
                connection.commit();
            } catch (SQLException e0) {
                try{
                    if(savepoint == null){
                        connection.rollback();
                        LOGGER.error("Failed to execute SQL insert", e0);
                    }else{
                        connection.rollback(savepoint);
                        LOGGER.error("Failed to execute SQL insert children", e0);
                    }
                }catch (SQLException e1) {
                    LOGGER.error("Failed to execute rollback SQL insert", e1);
                }
                throw e0;
            }
        }
    }

    public void insertChildren(Connection connection, T instance) throws SQLException {
    }


}
