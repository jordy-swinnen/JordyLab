package dev.jordy.jordylab.shared.security;

/**
 * Implemented by a security principal that knows the stable user id. Shared plumbing (AI tracing, audit) uses this
 * instead of importing the identity module, which would create a module cycle (identity already depends on shared).
 */
public interface UserIdAware {

    String userId();
}
