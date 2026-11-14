package com.lunar_prototype.impossbleEscapeMC.core.di.exceptions;

public class CircularDependencyException extends DependencyResolutionException {
    public CircularDependencyException(String message) { super(message); }
    public CircularDependencyException(String message, Throwable cause) { super(message, cause); }
}
