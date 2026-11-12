package dev.jordy.jordylab.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link UserTheme} to its API key in the database ({@code content},
 * {@code system}, …), so the column and the web/desktop preference values are
 * the same words. {@code @Enumerated(STRING)} would store the constant name
 * ({@code SYSTEM}) and fail to read a row written with {@code system}.
 */
@Converter
public class UserThemeConverter implements AttributeConverter<UserTheme, String> {

    @Override
    public String convertToDatabaseColumn(UserTheme theme) {
        return theme == null ? null : theme.key();
    }

    @Override
    public UserTheme convertToEntityAttribute(String value) {
        return UserTheme.fromKey(value);
    }
}
