package com.cotani.storage.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cotani.storage.executor.QueryExecutor;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class SelectQueryTest {

    private static EntityMapper<String> stringMapper() {
        return row -> row.getString("value");
    }

    @Test
    void limitRejectsNonPositiveValue() {
        SelectQuery query = new SelectQuery("test", mock(QueryExecutor.class));

        assertThrows(IllegalArgumentException.class, () -> query.limit(-1));
        assertThrows(IllegalArgumentException.class, () -> query.limit(0));
    }

    @Test
    void positiveLimitAppendsLimitClause() {
        QueryExecutor executor = mock(QueryExecutor.class);
        SelectQuery query = new SelectQuery("test", executor).limit(5);

        query.one(stringMapper());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(executor).queryOne(captor.capture(), any(SqlConsumer.class), any(EntityMapper.class));
        assertTrue(captor.getValue().endsWith(" LIMIT 5"));
    }

    @Test
    void explicitColumnsAreSelected() {
        QueryExecutor executor = mock(QueryExecutor.class);
        SelectQuery query = new SelectQuery("test", executor).columns("id", "name");

        query.one(stringMapper());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(executor).queryOne(captor.capture(), any(SqlConsumer.class), any(EntityMapper.class));
        assertEquals("SELECT id, name FROM test", captor.getValue());
    }

    @Test
    void whereConditionGeneratesParameterizedClause() throws SQLException {
        QueryExecutor executor = mock(QueryExecutor.class);
        ParameterBinder binder = mock(ParameterBinder.class);
        SelectQuery query = new SelectQuery("test", executor).where("status", "active");

        query.one(stringMapper());

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(executor).queryOne(sqlCaptor.capture(), any(SqlConsumer.class), any(EntityMapper.class));
        assertEquals("SELECT * FROM test WHERE status = ?", sqlCaptor.getValue());

        var binderCaptor = ArgumentCaptor.forClass(SqlConsumer.class);
        verify(executor).queryOne(anyString(), binderCaptor.capture(), any(EntityMapper.class));
        binderCaptor.getValue().accept(binder);
        verify(binder).set("active");
    }

    @Test
    void orderByDescAppendsOrderClause() {
        QueryExecutor executor = mock(QueryExecutor.class);
        SelectQuery query = new SelectQuery("test", executor).orderByDesc("created_at");

        query.one(stringMapper());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(executor).queryOne(captor.capture(), any(SqlConsumer.class), any(EntityMapper.class));
        assertEquals("SELECT * FROM test ORDER BY created_at DESC", captor.getValue());
    }

    @Test
    void multipleConditionsAreJoinedWithAnd() {
        QueryExecutor executor = mock(QueryExecutor.class);
        SelectQuery query = new SelectQuery("test", executor).where("a", 1).where("b", 2);

        query.one(stringMapper());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(executor).queryOne(captor.capture(), any(SqlConsumer.class), any(EntityMapper.class));
        assertEquals("SELECT * FROM test WHERE a = ? AND b = ?", captor.getValue());
    }

    @Test
    void sqlIsCachedUntilBuilderIsMutated() {
        QueryExecutor executor = mock(QueryExecutor.class);
        SelectQuery query = new SelectQuery("test", executor);

        query.one(stringMapper());
        query.one(stringMapper());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(executor, times(2)).queryOne(captor.capture(), any(SqlConsumer.class), any(EntityMapper.class));
        List<String> calls = captor.getAllValues();
        assertEquals("SELECT * FROM test", calls.get(0));
        assertEquals("SELECT * FROM test", calls.get(1));

        query.where("x", 1).one(stringMapper());

        verify(executor, times(3)).queryOne(anyString(), any(SqlConsumer.class), any(EntityMapper.class));
    }
}
