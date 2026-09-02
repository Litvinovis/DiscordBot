package services.sandbox.repository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import services.sandbox.model.TradeRecord;
import services.sandbox.model.TradeSide;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Недоступная БД должна быть видна вызывающему коду.
 * Раньше репозитории глушили исключение и возвращали пустоту: сделка «проходила»,
 * хотя ничего не записалось, а планировщик решал, что заявок нет.
 */
class TradeRepositoryDbDownTest {

	private JdbcTemplate brokenJdbc() throws SQLException {
		DataSource ds = mock(DataSource.class);
		when(ds.getConnection()).thenThrow(new SQLException("Connection refused: DB is down"));
		return new JdbcTemplate(ds);
	}

	@Test
	void findAll_whenDbDown_throws() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		assertThrows(DataAccessException.class, repo::findAll,
				"Пустой список вместо ошибки заставлял планировщик считать, что заявок нет");
	}

	@Test
	void findById_whenDbDown_throws() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		assertThrows(DataAccessException.class, () -> repo.findById("key1"),
				"null вместо ошибки читался как «пользователь не зарегистрирован»");
	}

	@Test
	void findByUserId_whenDbDown_throws() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		assertThrows(DataAccessException.class, () -> repo.findByUserId("user1"));
	}

	@Test
	void save_whenDbDown_throws() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		TradeRecord trade = new TradeRecord("id1", "user1", "SBER", TradeSide.BUY, 10, BigDecimal.valueOf(300.0), BigDecimal.ONE, Instant.now());
		assertThrows(DataAccessException.class, () -> repo.save("id1", trade),
				"Молчаливый провал записи расходился с ответом бота «Куплено»");
	}

	@Test
	void delete_whenDbDown_throws() throws SQLException {
		TradeRepository repo = new TradeRepository(brokenJdbc());
		assertThrows(DataAccessException.class, () -> repo.delete("id1"));
	}
}
