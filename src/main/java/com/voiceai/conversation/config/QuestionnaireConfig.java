package com.voiceai.conversation.config;

import com.voiceai.conversation.model.Question;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration for questionnaire questions.
 * Questions are defined here for easy modification without code changes.
 */
@Configuration
public class QuestionnaireConfig {

    @Bean
    public List<Question> questionnaireQuestions() {
        return Arrays.asList(
                new Question(
                        1,
                        "Over the past week, how confident have you felt in making healthy food choices?",
                        Arrays.asList(
                                "very confident",
                                "somewhat confident",
                                "neutral",
                                "not very confident",
                                "not confident at all"
                        )
                ),
                new Question(
                        2,
                        "In the past week, how many days did you engage in at least 20 minutes of moderate activity, such as walking?",
                        Arrays.asList("0", "1-3", "4-5", "6-7")
                ),
                new Question(
                        3,
                        "Over the past week, how often have you felt physically well and energized?",
                        Arrays.asList(
                                "always",
                                "most of the time",
                                "sometimes",
                                "rarely",
                                "never"
                        )
                ),
                new Question(
                        4,
                        "In the past week, have you taken all of your prescribed medication as directed?",
                        Arrays.asList(
                                "yes",
                                "no",
                                "sometimes",
                                "i dont take medication",
                                "i dont have access to my medication"
                        )
                )
        );
    }
}