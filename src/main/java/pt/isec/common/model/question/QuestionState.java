package pt.isec.common.model.question;

import java.time.LocalDateTime;

/**
 * Question state based on availability period:
 * <ul>
 *     <li>FUTURE: not started yet (now &lt; startAt)</li>
 *     <li>ACTIVE: in progress (startAt &lt;= now &lt;= endAt)</li>
 *     <li>EXPIRED: already finished (now &gt; endAt)</li>
 * </ul>
 */
public enum QuestionState {
    FUTURE("Futura", "Ainda não começou"),
    ACTIVE("Ativa", "Em curso"),
    EXPIRED("Expirada", "Já terminou");

    private final String displayName;
    private final String description;

    QuestionState(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Returns a human-friendly display name (in Portuguese).
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns a short description (in Portuguese).
     *
     * @return description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Computes the question state based on start and end dates using current time.
     *
     * @param startAt start date/time
     * @param endAt   end date/time
     * @return corresponding {@link QuestionState}
     */
    public static QuestionState fromDates(LocalDateTime startAt, LocalDateTime endAt) {
        return fromDates(startAt, endAt, LocalDateTime.now());
    }

    /**
     * Computes the question state based on start/end dates and a specific timestamp.
     *
     * @param startAt start date/time
     * @param endAt   end date/time
     * @param now     reference date/time
     * @return corresponding {@link QuestionState}
     */
    public static QuestionState fromDates(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        if (now.isBefore(startAt)) {
            return FUTURE;
        } else if (now.isAfter(endAt)) {
            return EXPIRED;
        } else {
            return ACTIVE;
        }
    }

    /**
     * Checks whether the question can be answered in this state.
     *
     * @return {@code true} if answer is allowed
     */
    public boolean canBeAnswered() {
        return this == ACTIVE;
    }

    /**
     * Checks whether results can be viewed in this state.
     *
     * @return {@code true} if results are available
     */
    public boolean canViewResults() {
        return this == EXPIRED;
    }

    /**
     * Checks whether the question can be modified (edited/deleted) in this state.
     *
     * @return {@code true} if modifications are allowed
     */
    public boolean canBeModified() {
        return this == FUTURE || this == ACTIVE;
    }
}
