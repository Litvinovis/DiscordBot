package services.sandbox.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import services.sandbox.model.TradeRecord;
import services.sandbox.model.TradeSide;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeRepositoryDbDownTest {

	private JdbcTemplate brokenJdbc() throws SQLException {
		DataSource ds = mock(DataSource.class);
		when(ds.getConnection()).thenThrow(new SQLException("Connection refused: DB is down"));
		return new JdbcTemplate(ds);
	}

	@Test
	void findAll_whenDbDown_returnsEmptyList() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		List<TradeRecord> result = repo.findAll();
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void findById_whenDbDown_returnsNull() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		assertNull(repo.findById("key1"));
	}

	@Test
	void findByUserId_whenDbDown_returnsEmptyList() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		List<TradeRecord> result = repo.findByUserId("user1");
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void save_whenDbDown_doesNotThrow() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		TradeRecord trade = new TradeRecord("id1", "user1", "SBER", TradeSide.BUY, 10, 100.0, 0.1, Instant.now());
		assertDoesNotThrow(() -> repo.save("id1", trade));
	}

	@Test
	void delete_whenDbDown_doesNotThrow() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		assertDoesNotThrow(() -> repo.delete("id1"));
	}
}
