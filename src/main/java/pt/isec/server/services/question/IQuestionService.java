package pt.isec.server.services.question;

import pt.isec.common.dto.question.*;
import pt.isec.common.model.question.Question;

import java.util.List;

/**
 * Interface pública do serviço de gestão de Perguntas.
 */
public interface IQuestionService {
    CreateQuestionResponseDTO createQuestion(CreateQuestionDTO dto) throws Exception;
    List<Question> listQuestions(ListQuestionsDTO dto) throws Exception;
    Question joinQuestion(JoinQuestionDTO dto) throws Exception;
    boolean editQuestion(EditQuestionDTO dto) throws Exception;
    boolean deleteQuestion(DeleteQuestionDTO dto) throws Exception;
}
