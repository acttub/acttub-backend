package db.migration;

import java.sql.Connection;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V7__Update_coaching_input_contract extends BaseJavaMigration {

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		try (Statement statement = connection.createStatement()) {
			statement.execute("alter table coachings add column genre text");
			statement.execute("alter table coachings add column custom_genre text");
			statement.execute("alter table coachings add column situation text");
			statement.execute("alter table coachings add column character_setting text");
			statement.execute("alter table coachings add column subtext text");
			dropPerformanceIntentNotNull(connection, statement);
		}
	}

	private void dropPerformanceIntentNotNull(Connection connection, Statement statement) throws Exception {
		statement.execute("alter table coachings alter column performance_intent drop not null");
	}
}
