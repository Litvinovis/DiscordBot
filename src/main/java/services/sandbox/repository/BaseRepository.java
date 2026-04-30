package services.sandbox.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class BaseRepository {

	protected final Logger log = LoggerFactory.getLogger(getClass());
	protected final JdbcTemplate jdbc;

	protected BaseRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}
}
