import org.pavangajjala.ci.Environments

assert Environments.isProtected('prod')
assert Environments.isProtected('Production')
assert Environments.isProtected(' live ')
assert !Environments.isProtected('dev')
assert !Environments.isProtected('staging')
assert !Environments.isProtected('preprod')
assert !Environments.isProtected(null)

println 'EnvironmentsTest: ok'
