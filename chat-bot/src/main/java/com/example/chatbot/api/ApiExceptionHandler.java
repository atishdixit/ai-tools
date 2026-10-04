package com.example.chatbot.api;

import com.example.chatbot.chat.ChatService.BusyException;
import com.example.chatbot.chat.ChatService.InvalidQuestionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns failures into RFC 7807 problem responses. Questions are never echoed back or logged. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidQuestionException.class)
    ProblemDetail invalid(InvalidQuestionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(BusyException.class)
    ProblemDetail busy(BusyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }
    // Spring's own errors (malformed JSON 400, wrong method 405, wrong content type 415) are handled by Spring Boot's
    // problem-details support; a catch-all here would wrongly turn them into 500s.
}
