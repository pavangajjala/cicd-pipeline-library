import org.pavangajjala.ci.ChangeDetector

/**
 * Returns the services affected by the current change.
 *
 *   def services = changedServices(
 *       services: [orders: 'services/orders', billing: 'services/billing'],
 *       sharedPaths: ['libs/', 'Jenkinsfile'])
 */
def call(Map config) {
    Map services = config.services ?: [:]
    List sharedPaths = config.sharedPaths ?: []

    // On a pull request compare with the target branch; otherwise with the
    // last commit that built successfully, so a failed build is not skipped
    // over by the next one.
    String base
    if (env.CHANGE_TARGET) {
        sh(script: "git fetch --no-tags origin ${env.CHANGE_TARGET}", label: 'Fetch target branch')
        base = "origin/${env.CHANGE_TARGET}"
    } else {
        base = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: 'HEAD~1'
    }

    String output = sh(
        script: "git diff --name-only ${base}...HEAD || git diff --name-only HEAD~1 HEAD",
        returnStdout: true,
        label: 'List changed files'
    ).trim()

    List<String> files = output ? output.split('\n') as List<String> : []
    List<String> affected = new ChangeDetector(services, sharedPaths).affectedServices(files)
    echo "Changed files: ${files.size()}, affected services: ${affected ?: 'none'}"
    return affected
}
