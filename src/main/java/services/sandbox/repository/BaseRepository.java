package services.sandbox.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

public abstract class BaseRepository {

	protected final Logger log = LoggerFactory.getLogger(getClass());
	protected final JdbcTemplate jdbc;

	protected BaseRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * NULL в денежной колонке читается как ноль.
	 * getDouble() раньше возвращал 0.0 сам; getBigDecimal() отдаёт null,
	 * и без этой обёртки старая запись с пустым полем роняла бы расчёты.
	 */
	protected static BigDecimal nz(BigDecimal value) {
		return value != null ? value : BigDecimal.ZERO;
	}
}
