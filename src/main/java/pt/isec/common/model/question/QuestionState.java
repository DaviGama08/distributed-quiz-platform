package pt.isec.common.model.question;

import java.time.LocalDateTime;

/**
 * Estado de uma questão baseado no período de disponibilidade
 * - FUTURE: ainda não começou (now < startAt)
 * - ACTIVE: em curso (startAt <= now <= endAt)
 * - EXPIRED: já terminou (now > endAt)
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

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Calcula o estado baseado nas datas
     */
    public static QuestionState fromDates(LocalDateTime startAt, LocalDateTime endAt) {
        return fromDates(startAt, endAt, LocalDateTime.now());
    }

    /**
     * Calcula o estado baseado nas datas e momento específico
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
     * Verifica se pode ser respondida
     */
    public boolean canBeAnswered() {
        return this == ACTIVE;
    }

    /**
     * Verifica se resultados podem ser visualizados
     */
    public boolean canViewResults() {
        return this == EXPIRED;
    }

    /**
     * Verifica se pode ser editada/eliminada
     */
    public boolean canBeModified() {
        return this == FUTURE || this == ACTIVE;
    }
}

