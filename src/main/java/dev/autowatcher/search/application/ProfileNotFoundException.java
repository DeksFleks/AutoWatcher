package dev.autowatcher.search.application;
import java.util.UUID;
public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException(UUID id) { super("Search profile not found: " + id); }
}
