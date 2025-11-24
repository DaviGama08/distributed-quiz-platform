// java
package pt.isec.common.dto.question;

import pt.isec.common.model.question.Option;
import pt.isec.common.model.question.OptionLetter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public record UpdateQuestionDTO (Integer questionId,
                                String statement,
                                Integer teacherId,
                                List<Option> options,
                                OptionLetter correctOption,
                                LocalDateTime startAt,
                                LocalDateTime endAt) implements Serializable {}
