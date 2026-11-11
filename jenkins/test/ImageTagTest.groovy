import org.pavangajjala.ci.ImageTag

def fails = { Closure body ->
    try { body(); return false } catch (IllegalArgumentException ignored) { return true }
}

// Unique, readable build tags
assert ImageTag.forBuild('main', '0123456789abcdef', '42') == 'main-42-01234567'
assert ImageTag.forBuild('origin/feature/JIRA-12_Add Login!', 'abc1234', '7') == 'feature-jira-12_add-login-7-abc1234'
assert ImageTag.forBuild(null, 'abc1234', null) == 'detached-0-abc1234'
assert fails { ImageTag.forBuild('main', '', '1') }

// Always a valid Docker tag: allowed characters only, no leading dot or dash
['--weird//..branch..', '日本語', 'UPPER/Case', 'a' * 200].each { branch ->
    def tag = ImageTag.forBuild(branch, 'deadbeefcafe', '1')
    assert tag ==~ /[a-z0-9_][a-z0-9_.-]{0,127}/ : "invalid tag for ${branch}: ${tag}"
}
assert ImageTag.sanitise('日本語') == 'branch'
assert ImageTag.sanitise('a' * 200).length() == 40

// Release tags
assert ImageTag.forRelease('v1.2.3') == '1.2.3'
assert ImageTag.forRelease('2.0.0-rc.1') == '2.0.0-rc.1'
assert fails { ImageTag.forRelease('latest') }
assert fails { ImageTag.forRelease('v1.2') }

println 'ImageTagTest: ok'
