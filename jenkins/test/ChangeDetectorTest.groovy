import org.pavangajjala.ci.ChangeDetector

def services = [orders: 'services/orders', 'orders-api': 'services/orders-api', billing: 'services/billing/']
def detector = new ChangeDetector(services, ['libs/', 'Jenkinsfile'])

// Only the service whose files changed
assert detector.affectedServices(['services/orders/src/main.py']) == ['orders']

// A directory name that is a prefix of another must not match it
assert detector.affectedServices(['services/orders-api/handler.py']) == ['orders-api']

// Several services, returned sorted and without duplicates
assert detector.affectedServices([
    'services/billing/a.py', 'services/orders/b.py', 'services/billing/c.py',
]) == ['billing', 'orders']

// Shared paths rebuild everything
assert detector.affectedServices(['libs/common/util.py']) == ['billing', 'orders', 'orders-api']
assert detector.affectedServices(['Jenkinsfile']) == ['billing', 'orders', 'orders-api']

// A file that merely starts with a shared path name is not shared
assert detector.affectedServices(['Jenkinsfile.bak']) == []

// Unrelated files, blanks and odd path styles
assert detector.affectedServices(['README.md', 'docs/x.md']) == []
assert detector.affectedServices([]) == []
assert detector.affectedServices(['', '  ', null]) == []
assert detector.affectedServices(['./services/orders/x', 'services\\billing\\y']) == ['billing', 'orders']

println 'ChangeDetectorTest: ok'
