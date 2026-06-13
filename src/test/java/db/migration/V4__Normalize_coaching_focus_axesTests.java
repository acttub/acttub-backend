package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class V4__Normalize_coaching_focus_axesTests {

	@Test
	void readAxesNormalizesDirtyJsonValues() throws Exception {
		V4__Normalize_coaching_focus_axes migration = new V4__Normalize_coaching_focus_axes();

		assertThat(migration.readAxes("[\" emotion \", null, \"speech\", \"emotion\", \"\", \"  \"]"))
				.containsExactly("emotion", "speech");
	}
}
