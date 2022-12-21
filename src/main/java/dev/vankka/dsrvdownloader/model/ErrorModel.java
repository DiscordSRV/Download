package dev.vankka.dsrvdownloader.model;

public record ErrorModel(int status, String statusDescription, String message) {}
