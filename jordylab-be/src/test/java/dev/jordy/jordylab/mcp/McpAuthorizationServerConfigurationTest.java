package dev.jordy.jordylab.mcp;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The authorization server's JDBC services live in the {@code mcp} schema, while every other query expects the default
 * one. This proves the wrapper borrows a connection with the connector schema and restores the previous one on release.
 */
class McpAuthorizationServerConfigurationTest {

    @Test
    void setsTheConnectorSchemaOnBorrowAndRestoresItOnRelease() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getSchema()).thenReturn("public");

        McpAuthorizationServerConfiguration.SchemaDataSource schemaDataSource =
                new McpAuthorizationServerConfiguration.SchemaDataSource(dataSource, "mcp");
        Connection borrowed = schemaDataSource.getConnection();
        verify(connection).setSchema("mcp");

        borrowed.close();
        verify(connection).setSchema("public");
        verify(connection).close();
    }
}
