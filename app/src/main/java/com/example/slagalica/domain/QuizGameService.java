package com.example.slagalica.domain;

import com.example.slagalica.data.QuizRepository;
import com.example.slagalica.model.QuizQuestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizGameService {

    public static final int QUESTION_COUNT = 5;
    public static final int QUESTION_TIME_MILLIS = 5000;
    public static final int CORRECT_POINTS = 10;
    public static final int WRONG_POINTS = -5;

    public interface QuestionsCallback {
        void onLoaded(List<QuizQuestion> questions);
    }

    public static class AnswerAttempt {
        private final int selectedAnswerIndex;
        private final long answeredAtMillis;

        public AnswerAttempt(int selectedAnswerIndex, long answeredAtMillis) {
            this.selectedAnswerIndex = selectedAnswerIndex;
            this.answeredAtMillis = answeredAtMillis;
        }

        public int getSelectedAnswerIndex() {
            return selectedAnswerIndex;
        }

        public long getAnsweredAtMillis() {
            return answeredAtMillis;
        }
    }

    public static class QuestionScore {
        private final int player1Delta;
        private final int player2Delta;
        private final boolean player1Correct;
        private final boolean player2Correct;
        private final int fastestCorrectPlayer;

        public QuestionScore(int player1Delta, int player2Delta, boolean player1Correct, boolean player2Correct, int fastestCorrectPlayer) {
            this.player1Delta = player1Delta;
            this.player2Delta = player2Delta;
            this.player1Correct = player1Correct;
            this.player2Correct = player2Correct;
            this.fastestCorrectPlayer = fastestCorrectPlayer;
        }

        public int getPlayer1Delta() {
            return player1Delta;
        }

        public int getPlayer2Delta() {
            return player2Delta;
        }

        public boolean isPlayer1Correct() {
            return player1Correct;
        }

        public boolean isPlayer2Correct() {
            return player2Correct;
        }

        public int getFastestCorrectPlayer() {
            return fastestCorrectPlayer;
        }
    }

    private final QuizRepository repository;

    public QuizGameService(QuizRepository repository) {
        this.repository = repository;
    }

    public void getRoundQuestions(QuestionsCallback callback) {
        repository.getQuestions(questions -> {
            List<QuizQuestion> roundQuestions = new ArrayList<>(questions);
            Collections.shuffle(roundQuestions);
            if (roundQuestions.size() > QUESTION_COUNT) {
                roundQuestions = new ArrayList<>(roundQuestions.subList(0, QUESTION_COUNT));
            }
            callback.onLoaded(roundQuestions);
        });
    }

    public QuestionScore scoreQuestion(QuizQuestion question, AnswerAttempt player1Attempt, AnswerAttempt player2Attempt) {
        boolean player1Answered = player1Attempt != null;
        boolean player2Answered = player2Attempt != null;
        boolean player1Correct = player1Answered && isCorrect(question, player1Attempt.getSelectedAnswerIndex());
        boolean player2Correct = player2Answered && isCorrect(question, player2Attempt.getSelectedAnswerIndex());

        int player1Delta = player1Answered && !player1Correct ? WRONG_POINTS : 0;
        int player2Delta = player2Answered && !player2Correct ? WRONG_POINTS : 0;
        int fastestCorrectPlayer = 0;

        if (player1Correct && player2Correct) {
            if (player1Attempt.getAnsweredAtMillis() <= player2Attempt.getAnsweredAtMillis()) {
                player1Delta += CORRECT_POINTS;
                fastestCorrectPlayer = 1;
            } else {
                player2Delta += CORRECT_POINTS;
                fastestCorrectPlayer = 2;
            }
        } else if (player1Correct) {
            player1Delta += CORRECT_POINTS;
            fastestCorrectPlayer = 1;
        } else if (player2Correct) {
            player2Delta += CORRECT_POINTS;
            fastestCorrectPlayer = 2;
        }

        return new QuestionScore(player1Delta, player2Delta, player1Correct, player2Correct, fastestCorrectPlayer);
    }

    private boolean isCorrect(QuizQuestion question, int answerIndex) {
        return question.getCorrectAnswerIndex() == answerIndex;
    }
}
