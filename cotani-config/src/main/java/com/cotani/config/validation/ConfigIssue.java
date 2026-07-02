package com.cotani.config.validation;

public record ConfigIssue(String file, String path, String message) {

    public String format() {
        return file + ":" + path + " - " + message;
    }
}
