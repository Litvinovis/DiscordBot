package services.sandbox.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SandboxMigrationService.CacheMigrationResult}.
 *
 * The result holder is a public inner class used by the migration summary
 * map; its {@code toString()} format is logged and potentially parsed by
 * monitoring tooling.
 */
class CacheMigrationResultTest {

	@Test
	void constructor_storesValues() {
		SandboxMigrationService.CacheMigrationResult result =
				new SandboxMigrationService.CacheMigrationResult(5, 2);
		assertEquals(5, result.migrated());
		assertEquals(2, result.removed());
	}

	@Test
	void constructor_zeroValues() {
		SandboxMigrationService.CacheMigrationResult result =
				new SandboxMigrationService.CacheMigrationResult(0, 0);
		assertEquals(0, result.migrated());
		assertEquals(0, result.removed());
	}

	@Test
	void toString_containsMigratedCount() {
		SandboxMigrationService.CacheMigrationResult result =
				new SandboxMigrationService.CacheMigrationResult(3, 1);
		String s = result.toString();
		assertTrue(s.contains("3"), "toString() must include migrated count");
	}

	@Test
	void toString_containsRemovedCount() {
		SandboxMigrationService.CacheMigrationResult result =
				new SandboxMigrationService.CacheMigrationResult(3, 7);
		String s = result.toString();
		assertTrue(s.contains("7"), "toString() must include removed count");
	}

	@Test
	void toString_containsKeywords() {
		SandboxMigrationService.CacheMigrationResult result =
				new SandboxMigrationService.CacheMigrationResult(1, 0);
		String s = result.toString();
		assertTrue(s.contains("migrated"), "toString() must contain 'migrated' label");
		assertTrue(s.contains("removed"),  "toString() must contain 'removed' label");
	}

	@Test
	void allMigratedAndRemoved_reflectedInSummaryTotals() {
		// Simulate what runMigrations() does: sum migrated and removed across caches
		SandboxMigrationService.CacheMigrationResult r1 =
				new SandboxMigrationService.CacheMigrationResult(10, 0);
		SandboxMigrationService.CacheMigrationResult r2 =
				new SandboxMigrationService.CacheMigrationResult(0, 3);
		SandboxMigrationService.CacheMigrationResult r3 =
				new SandboxMigrationService.CacheMigrationResult(2, 1);

		int totalMigrated = r1.migrated() + r2.migrated() + r3.migrated();
		int totalRemoved  = r1.removed()  + r2.removed()  + r3.removed();

		assertEquals(12, totalMigrated);
		assertEquals(4,  totalRemoved);
	}
}
