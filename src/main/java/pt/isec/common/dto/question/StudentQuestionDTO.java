package pt.isec.common.dto.question;

import pt.isec.common.model.question.Option;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Student-safe question projection. It deliberately excludes the correct option,
 * teacher identity and access code from the TCP payload.
 */
public record StudentQuestionDTO(
        Integer id,
        String statement,
        List<Option> options,
        LocalDateTime startAt,
        LocalDateTime endAt
) implements Serializable {
    public StudentQuestionDTO {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
