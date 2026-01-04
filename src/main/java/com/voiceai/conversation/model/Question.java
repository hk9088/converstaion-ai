package com.voiceai.conversation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Represents a questionnaire question with valid response categories.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int id;
    private String text;
    private List<String> validCategories;

    public boolean isValidCategory(String category) {
        if (category == null) {
            return false;
        }
        return validCategories.stream()
                .anyMatch(valid -> valid.equalsIgnoreCase(category.trim()));
    }
}