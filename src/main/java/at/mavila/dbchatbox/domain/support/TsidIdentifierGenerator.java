package at.mavila.dbchatbox.domain.support;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

import java.util.EnumSet;

/**
 * Hibernate identifier generator that produces TSID (Time-Sorted Unique Identifier) values.
 *
 * <p>
 * Implements {@link BeforeExecutionGenerator} (Hibernate 7 API) to generate a unique {@code Long} identifier before
 * entity insertion. Delegates to {@link TsidGenerator} for the actual TSID creation.
 * </p>
 *
 * @since 2026-04-09
 */
public class TsidIdentifierGenerator implements BeforeExecutionGenerator {

  /**
   * Generates a TSID value for the entity's primary key.
   *
   * @param session
   *                       the Hibernate session
   * @param owner
   *                       the entity instance
   * @param currentValue
   *                       the current value of the identifier (typically null)
   * @param eventType
   *                       the event type triggering generation
   * @return a unique TSID as {@code Long}
   */
  @Override
  public Object generate(final SharedSessionContractImplementor session, final Object owner, final Object currentValue,
      final EventType eventType) {
    return TsidGenerator.generate();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public EnumSet<EventType> getEventTypes() {
    return EnumSet.of(EventType.INSERT);
  }
}
