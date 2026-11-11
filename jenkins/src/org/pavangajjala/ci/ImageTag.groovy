package org.pavangajjala.ci

/**
 * Builds image tags from branch, commit and build number.
 *
 * Tags are immutable identifiers: every build gets a unique tag, and the
 * branch name is sanitised so it is always a valid Docker tag.
 */
class ImageTag implements Serializable {
    private static final long serialVersionUID = 1L
    private static final int MAX_TAG_LENGTH = 128

    static String forBuild(String branch, String commit, String buildNumber) {
        if (!commit) {
            throw new IllegalArgumentException('commit is required')
        }
        String shortSha = commit.length() > 8 ? commit.substring(0, 8) : commit
        String tag = "${sanitise(branch)}-${buildNumber ?: '0'}-${shortSha}"
        return tag.length() > MAX_TAG_LENGTH ? tag.substring(tag.length() - MAX_TAG_LENGTH) : tag
    }

    /** Release tags (v1.2.3) are used as they are, without the leading v. */
    static String forRelease(String gitTag) {
        if (!(gitTag ==~ /v?\d+\.\d+\.\d+([-+][0-9A-Za-z.-]+)?/)) {
            throw new IllegalArgumentException("not a semantic version tag: ${gitTag}")
        }
        return gitTag.startsWith('v') ? gitTag.substring(1) : gitTag
    }

    static String sanitise(String branch) {
        String value = (branch ?: 'detached').toLowerCase()
        if (value.startsWith('origin/')) {
            value = value.substring('origin/'.length())
        }
        value = value.replaceAll(/[^a-z0-9._-]+/, '-').replaceAll(/-{2,}/, '-')
        value = value.replaceAll(/^[.-]+/, '').replaceAll(/[.-]+$/, '')
        if (!value) {
            return 'branch'
        }
        return value.length() > 40 ? value.substring(0, 40).replaceAll(/[.-]+$/, '') : value
    }
}
