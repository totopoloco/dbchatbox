package at.mavila.dbchatbox.domain.support;

import com.github.f4b6a3.tsid.TsidFactory;

/**
 * Utility class for generating TSID (Time-Sorted Unique Identifier) values.
 *
 * <p>
 * Provides a thread-safe singleton {@link TsidFactory} instance for generating unique, time-sorted 64-bit identifiers
 * used as primary keys across all domain entities.
 * </p>
 *
 * @since 2026-04-09
 */
public final class TsidGenerator {

  private static final TsidFactory FACTORY = TsidFactory.newInstance256();

  private TsidGenerator() {
    // utility class
  }

  /**
   * Generates a new TSID value as a {@code Long}.
   *
   * @return a unique, time-sorted 64-bit identifier
   */
  public static long generate() {
    return FACTORY.create().toLong();
  }
}
