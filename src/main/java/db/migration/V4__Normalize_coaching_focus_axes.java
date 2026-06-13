package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V4__Normalize_coaching_focus_axes extends BaseJavaMigration {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();
		createFocusAxesTable(connection);
		copyExistingFocusAxes(connection);
		dropFocusAxesJsonColumn(connection);
	}

	private void createFocusAxesTable(Connection connection) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.execute("""
					create table coaching_focus_axes (
					  coaching_id bigint not null,
					  axis_order int not null,
					  axis varchar(30) not null,

					  constraint pk_coaching_focus_axes
					    primary key (coaching_id, axis_order),

					  constraint fk_coaching_focus_axes_coaching
					    foreign key (coaching_id) references coachings(id),

					  constraint uq_coaching_focus_axes_axis
					    unique (coaching_id, axis)
					)
					""");
		}
	}

	private void copyExistingFocusAxes(Connection connection) throws Exception {
		try (
				Statement select = connection.createStatement();
				ResultSet resultSet = select.executeQuery("select id, result_focus_axes from coachings where result_focus_axes is not null");
				PreparedStatement insert = connection.prepareStatement("""
						insert into coaching_focus_axes (coaching_id, axis_order, axis)
						values (?, ?, ?)
						""")
		) {
			while (resultSet.next()) {
				Long coachingId = resultSet.getLong("id");
				List<String> axes = readAxes(resultSet.getString("result_focus_axes"));
				for (int i = 0; i < axes.size(); i++) {
					insert.setLong(1, coachingId);
					insert.setInt(2, i);
					insert.setString(3, axes.get(i));
					insert.addBatch();
				}
			}
			insert.executeBatch();
		}
	}

	private List<String> readAxes(String axesJson) throws Exception {
		if (axesJson == null || axesJson.isBlank()) {
			return List.of();
		}
		List<String> axes = objectMapper.readValue(axesJson, new TypeReference<>() {
		});
		if (axes == null) {
			return List.of();
		}
		return axes;
	}

	private void dropFocusAxesJsonColumn(Connection connection) throws Exception {
		try (Statement statement = connection.createStatement()) {
			statement.execute("alter table coachings drop column result_focus_axes");
		}
	}
}
