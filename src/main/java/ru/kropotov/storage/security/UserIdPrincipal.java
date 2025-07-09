package ru.kropotov.storage.security;

public record UserIdPrincipal(String name) implements java.security.Principal {
    @Override
    public String getName() {
        return name;
    }
}