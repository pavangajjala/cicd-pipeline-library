package org.pavangajjala.ci

/** Which environments need a person to approve a deployment. */
class Environments implements Serializable {
    private static final long serialVersionUID = 1L
    private static final List<String> PROTECTED = ['prod', 'production', 'live']

    static boolean isProtected(String environment) {
        return environment != null && environment.trim().toLowerCase() in PROTECTED
    }
}
