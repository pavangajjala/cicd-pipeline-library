# CI/CD pipeline library

[![CI](https://github.com/pavangajjala/cicd-pipeline-library/actions/workflows/ci.yml/badge.svg)](https://github.com/pavangajjala/cicd-pipeline-library/actions/workflows/ci.yml)

Reusable pipelines for GitHub Actions and Jenkins, so that every repository
builds, scans and deploys the same way instead of carrying its own copy of
the pipeline.

The same three rules run through all of them:

1. **Scan before publish.** An image that fails the vulnerability scan is never pushed.
2. **Apply what was reviewed.** Terraform applies the saved plan file, not a fresh plan.
3. **No standing credentials.** Cloud access comes from OIDC; deployments happen through pull requests.

## GitHub Actions

| Workflow | What it does |
| --- | --- |
| [`docker-build.yml`](.github/workflows/docker-build.yml) | Build, Trivy scan, push with SBOM and provenance, sign the digest with cosign |
| [`terraform.yml`](.github/workflows/terraform.yml) | fmt, validate, plan with summary, optional VaultScan gate, apply behind environment approval |
| [`gitops-promote.yml`](.github/workflows/gitops-promote.yml) | Open a pull request that bumps an image tag in a Kustomize overlay |

```yaml
jobs:
  image:
    uses: pavangajjala/cicd-pipeline-library/.github/workflows/docker-build.yml@v1
    permissions:
      contents: read
      packages: write
      id-token: write
    with:
      image-name: ghcr.io/example-org/orders
      push: ${{ github.event_name == 'push' }}
```

Complete callers are in [examples/](examples): a service pipeline that builds
and promotes to dev, and an infrastructure pipeline that plans on pull
requests and applies dev then prod from `main`.

## Jenkins shared library

| Step | What it does |
| --- | --- |
| `dockerPipeline` | Test, build, scan and push an image; pushes only from the main branch |
| `terraformPipeline` | Parameterised by stack, environment and action; per-environment lock, archived plan, approval before production |
| `changedServices` | Lists the services in a monorepo affected by the current change |

```groovy
@Library('cicd-pipeline-library@v1') _

terraformPipeline(
    stacks: ['network', 'eks', 'data'],
    environments: ['dev', 'staging', 'prod'],
    awsCredentialsId: 'aws-deploy'
)
```

Register the library in Jenkins under *Manage Jenkins > System > Global
Trusted Pipeline Libraries* with this repository as the source and
**Library path** set to `jenkins/`.

Required plugins: Pipeline, Docker Pipeline, Lockable Resources, AWS
Credentials, AnsiColor, Timestamper, Workspace Cleanup.

## Security choices

- Third-party actions are pinned to commit SHAs, with the version in a comment.
- Workflow inputs reach shell steps through `env`, never by inline `${{ }}`
  expansion, which is how script injection into CI usually happens.
- Each workflow declares the minimum `permissions` it needs.
- Images are signed by digest, because a tag can be moved after signing.
- The promotion workflow validates the tag and path it is given and holds no
  cluster credentials.
- A Terraform apply is never cancelled halfway by a newer run.

More detail is in [docs/design.md](docs/design.md).

## Testing

```bash
actionlint .github/workflows/*.yml examples/*.yml
./jenkins/test/run.sh        # needs groovy
```

The Groovy logic that does not depend on Jenkins (monorepo change detection,
image tag generation, protected environments) is unit tested. The pipeline
steps themselves are compiled for syntax but need a Jenkins controller to
run, so treat them as reviewed rather than integration tested.

## License

MIT
