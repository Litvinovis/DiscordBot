package services.sandbox.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

public abstract class BaseRepository {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final DataSource dataSource;

    protected BaseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
