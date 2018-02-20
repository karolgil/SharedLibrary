package io.jenkins.plugins.shared_library;

/**
 * Specifies one or more libraries to load.
 * Example usage:
 * <pre><code>
 * SharedLibrary("shared_libraries") _
 * </code></pre>
 */
public @interface SharedLibrary {
  String value();
}