# 4.1.0 release preparation

This procedure prepares a release; this repository currently remains on `4.1.0-SNAPSHOT`. The [manual release workflow](../.github/workflows/release.yml) only publishes the versioned GHCR image and a GitHub release containing `compose.yaml`. It does **not** deploy Maven artifacts or create a tag. Keep the workflow disabled until the owner has cleared the license/header decision and GHCR visibility.

## Prerequisites and Maven artifacts

1. QQQ must publish a non-draft, non-prerelease `v4.1.0` GitHub release with ESB and the Next dashboard included. Release `qbit-build-parent` against the 4.1 BOM, then publish all eight qbits against that parent. This work occurs in their own repositories.
2. In a separate reviewed change to this repository, set root `revision` and the BOM's `qqq.version` to `4.1.0`; pin each qbit to a published non-snapshot version. Run `mvn -B -ntp verify`, the core and full smoke checks, and review the resolved dependencies and effective POMs. Do not use `-Drevision=4.1.0` against a snapshot source tree as a substitute for reviewed pins.
3. Stage **both** `com.kingsrook.qqq:qqq-all-parent:4.1.0` and `com.kingsrook.qqq:qqq-all-bom:4.1.0` using the opt-in `release-central` Maven profile. The profile runs the release-version/dependency checks, signs the POMs with GPG, and uses the Central Publishing Maven plugin with `autoPublish=false`. It is only for the parent and BOM reactor; the app and BOM test JAR are excluded. POM packaging needs no source or Javadoc JARs. Configure the owner's `central` Portal user token in a private Maven `settings.xml` and a trusted GPG key outside the repository. Do not place credentials in this project or CI.

   ```bash
   mvn -B -ntp -pl qqq-all-bom -am -Prelease-central deploy
   ```

   The command uploads a deployment for **manual** Central Portal review; it does not auto-publish. Check the staged parent and BOM POM coordinates, signatures, license/SCM metadata, 4.1 QQQ import, and all eight qbit pins. Publish in the Portal only after approval. The profile fails validation while any current snapshot version remains. `mvn deploy` without the profile is not a release path.
4. Wait until both release POMs resolve anonymously from Maven Central. The release workflow fetches them from `repo.maven.apache.org` and compares their coordinates and BOM pins with its exact source commit. If either artifact is absent or differs, it stops before image publication.

The owner must resolve the license/header alignment noted in the [plan](plan.md) before enabling release. The existing Apache-2.0 metadata is not itself evidence that every dependency and header is cleared. The owner must also bootstrap the `qqq-all` GHCR package and make it **Public** in package settings; first publication defaults private. The workflow checks public visibility and refuses to push a private release image.

## Exact commit and manual dispatch

After the release POMs are public, merge the reviewed version-pin change and the C7 smoke workflow to `develop`. Require a successful **push** run of `Smoke` on the exact intended commit; both `core` and `full` jobs must pass. Review the run's full SHA and run ID in Actions. Create an **annotated** `4.1.0` tag at that exact `develop` commit through the normal signed-tag process. The workflow checks that the local tag is annotated and that the remote tag resolves to the same SHA; it does not verify a tag signature, so the operator must do that separately. Do not move the tag or advance `develop` before dispatch.

Configure a protected `qqq-all-release` GitHub environment with required reviewers and branch restriction to `develop`. After the owner has cleared licensing and the publication preconditions, set environment variables `QQQ_ALL_RELEASE_LICENSE_APPROVED=true` and `QQQ_ALL_RELEASE_ENABLED=true`. Unset them to close the gate. These variables and the environment approval are operational controls, not substitutes for the release review.

Dispatch **Release 4.1.0** on `develop` with `release_sha` (the full 40-character commit SHA) and `smoke_run_id` (the successful push run ID). The job requires that checkout, `develop`, the annotated tag, and the smoke run all refer to that SHA. It checks upstream GA, both public Central POMs, public GHCR visibility, and the absence of a `4.1.0` GHCR tag; runs `mvn -B -ntp verify`; then rechecks the live `develop` and remote tag refs before publication. Only then does it build and push `ghcr.io/qrun-io/qqq-all:4.1.0` and create the GitHub `4.1.0` release with `compose.yaml` attached.

The image push and GitHub release creation are separate API operations, not an atomic transaction. If the release step fails after the image push, inspect the image digest and workflow logs before recovery; never reuse or overwrite the version tag blindly. Verify the public image pull, release asset, and both Maven POMs from an unauthenticated environment after publication. No workflow in this preparation branch publishes or tags anything by itself.
