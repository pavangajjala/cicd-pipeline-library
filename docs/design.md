# Design notes

## Why a shared library at all

When every repository owns its pipeline, a fix (a new scanner flag, a pinned
action after a supply-chain incident) has to be made dozens of times and is
never made everywhere. With a versioned library, callers reference `@v1` and
get fixes by default, and a breaking change ships as `@v2` that teams adopt
when they are ready.

## Docker build: why build twice

The workflow builds once into the local Docker daemon, scans that image, and
then runs the build again with `push: true`. The second build is served
entirely from the layer cache, so it costs seconds. The alternative, pushing
first and scanning the registry copy, means a vulnerable image is already
published by the time the scan fails.

## Terraform: plan and apply as separate jobs

- The plan file is uploaded as an artifact and the apply job downloads it.
  Running `terraform apply` without a plan file would re-plan, and anything
  merged or changed in the cloud since review would slip in unreviewed.
- The apply job targets a GitHub environment. Required reviewers, wait timers
  and branch restrictions are configured there, outside the workflow file, so
  a pull request cannot remove its own approval step.
- `-detailed-exitcode` tells the workflow whether there is anything to apply,
  so approvers are not asked to approve empty plans.
- The concurrency group has `cancel-in-progress: false`. Cancelling an apply
  can leave a state lock and half-created resources behind.

## Promotion through pull requests

The pipeline that builds an image has no access to any cluster. It proposes a
change to the GitOps repository and stops. This keeps cluster credentials out
of CI, leaves an audit trail in Git, and makes rollback a revert.

## Jenkins: what is and is not tested

`ChangeDetector`, `ImageTag` and `Environments` are plain Groovy classes with
unit tests. Two details were worth pinning down with tests:

- `services/orders` must not match files under `services/orders-api`
- any branch name, including non-ASCII ones, must produce a valid image tag

The declarative pipelines in `vars/` are compiled in CI. Running them needs a
controller with the listed plugins; JenkinsPipelineUnit would be the next
step for testing stage logic without one.
