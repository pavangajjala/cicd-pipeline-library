package org.pavangajjala.ci

/**
 * Works out which services in a monorepo are affected by a set of changed
 * files, so a pipeline only builds and deploys what actually changed.
 *
 * Pure logic with no Jenkins dependencies, so it can be unit tested with
 * plain Groovy (see jenkins/test).
 */
class ChangeDetector implements Serializable {
    private static final long serialVersionUID = 1L

    /** service name -> directory, e.g. [orders: 'services/orders'] */
    private final Map<String, String> services
    /** Paths that affect every service when they change. */
    private final List<String> sharedPaths

    ChangeDetector(Map<String, String> services, List<String> sharedPaths = []) {
        this.services = services
        this.sharedPaths = sharedPaths
    }

    List<String> affectedServices(List<String> changedFiles) {
        List<String> files = []
        for (String file : changedFiles) {
            if (file != null && file.trim()) {
                files.add(normalise(file))
            }
        }

        for (String shared : sharedPaths) {
            for (String file : files) {
                if (isUnder(file, shared)) {
                    return sortedNames()
                }
            }
        }

        List<String> affected = []
        for (String name : sortedNames()) {
            String directory = services[name]
            for (String file : files) {
                if (isUnder(file, directory)) {
                    affected.add(name)
                    break
                }
            }
        }
        return affected
    }

    private List<String> sortedNames() {
        List<String> names = new ArrayList<String>(services.keySet())
        Collections.sort(names)
        return names
    }

    private static String normalise(String path) {
        String cleaned = path.trim().replace('\\', '/')
        while (cleaned.startsWith('./')) {
            cleaned = cleaned.substring(2)
        }
        return cleaned
    }

    /** True when file is the path itself or lives below it. 'services/orders' must not match 'services/orders-api'. */
    private static boolean isUnder(String file, String path) {
        String base = normalise(path)
        while (base.endsWith('/')) {
            base = base.substring(0, base.length() - 1)
        }
        return file == base || file.startsWith(base + '/')
    }
}
