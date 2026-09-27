# Publishing releases

The `Publish mod releases` GitHub Actions workflow distributes the two reviewed
GitHub release jars to Modrinth and CurseForge. It downloads the released jars,
checks their SHA-256 digests and packaged mod/version/loader metadata, and uses
the GitHub release body as the changelog. It does not rebuild the mod.

Release preparation and review still happen before publishing on GitHub.
Ordinary commits, branch pushes, tags alone, drafts and prereleases do not
publish to either store.

## One-time setup

1. Merge the workflow and its helper into `main`, the default branch.
2. Add these **repository secrets** in
   [Settings → Secrets and variables → Actions](https://github.com/ioanvl/cobblemon-riding-tweaks/settings/secrets/actions):

   | Secret | Where to create it | Permissions |
   | --- | --- | --- |
   | `MODRINTH_TOKEN` | [Modrinth personal access tokens](https://modrinth.com/settings/pats) | **Create versions** (`VERSION_CREATE`) for the account with publishing access to this project. Existing versions are not edited or unfeatured. |
   | `CURSEFORGE_TOKEN` | [CurseForge author API tokens](https://authors-old.curseforge.com/account/api-tokens) | Author Upload API token from an account allowed to upload to this project. This is different from a CurseForge for Studios API key. |

   Paste token values directly into GitHub's secret form, not into chat, source
   files, workflow YAML or command arguments. No additional GitHub token secret
   is needed: Actions supplies its own token for reading releases and recording
   deployment statuses.

3. Run a manual dry run as described below. The dry run validates public release
   assets and prior publication records; it does **not** validate store tokens
   or prove that either store will accept an upload.
4. Automatic distribution is enabled by default for future published stable
   releases. No repository variable is required. To pause it, set the optional
   repository variable `PUBLISH_RELEASES` to `false` in
   [Actions variables](https://github.com/ioanvl/cobblemon-riding-tweaks/settings/variables/actions).
   Removing that variable or setting it to `true` resumes automatic publishing.
   Manual publishing remains available to maintainers while the automatic
   trigger is paused.

Project IDs are already configured: Modrinth `8JWE9x1o`, CurseForge `1592779`.
Cobblemon is marked required on both loaders; Fabric API is required and Mod
Menu optional on Fabric. If required dependencies change, update the helper's
dependency checks and mappings alongside the mod metadata.

## Normal release

Prepare and test the release, review its notes and jars, then explicitly publish
the GitHub release with both runtime jars already attached. Once enabled, the
workflow uploads each loader to each store in separate jobs. The workflow's
summary links to the accepted uploads. CurseForge approval may happen later;
an accepted upload is not a guarantee that the file is already publicly listed.

GitHub release publication currently uses the maintainer's repository-specific
token. If that moves into another Actions workflow using `GITHUB_TOKEN`, call
this workflow explicitly with `workflow_dispatch`: GitHub suppresses most new
workflow events caused by its own Actions token.

## Dry runs, backfills and retries

Open **Actions → Publish mod releases → Run workflow**, select branch `main`,
and enter an existing published stable tag such as `v1.3.0`.

- Leave **Upload to the selected stores** unchecked for a read-only dry run.
- Select a platform and loader to operate on just one of the four uploads.
- Check **Upload to the selected stores** only when ready to publish that tag.
- An already-published GitHub release does not trigger retroactively when this
  workflow is installed. Use a manual run for a backfill.

Successful uploads are tracked in GitHub Deployments, with the release ID and
jar hash. A rerun skips successes. Modrinth is also checked for an existing
listed version with the same version number, loader, Minecraft version and
file hash; different bytes for the same version stop the upload.

CurseForge's author API does not document a file-listing endpoint. Its duplicate
guard therefore covers uploads recorded by this workflow. **Before backfilling
a release that might have been uploaded manually, check the CurseForge author
dashboard and select only missing uploads.** Keep deployment records: deleting
them removes that retry history. A recorded success also assumes the store
file has not subsequently been deleted by hand.

If a run fails or loses its connection after starting an upload, its state is
uncertain: the store may have accepted the file even if GitHub never received a
success response. Check the store, including pending-review files. If the file
is absent, manually select that single platform/loader and check **I checked
the store and the previous uncertain upload is absent**. Otherwise leave it
alone and reconcile the GitHub deployment record with the accepted store file.
Automatic upload retries are disabled to avoid resubmitting an uncertain write.

Deployment tracking is separate from release assets: it does not add extra
downloads to the GitHub release or change its notes. The helper records attempts
before upload and successful store URLs afterward. A dry run creates no records.

## Local checks

Python 3.11 or newer is required. No Python packages or Gradle publishing plugin
are needed.

```sh
python3 -m unittest discover -s tests/publishing -v
RELEASE_TAG=v1.3.0 PUBLISH_PLATFORM=modrinth PUBLISH_LOADER=fabric DO_PUBLISH=false \
  python3 scripts/release_publish.py prepare
```

The second command downloads public release data and writes verification files
under ignored `build/publishing/`. It does not upload anything. Local GitHub
authentication is optional for these public reads, subject to API rate limits.

Workflow actions are pinned to reviewed commits. Recheck the upstream action's
behavior and these tests when updating the pins.

References: [Modrinth version API](https://docs.modrinth.com/api/operations/createversion/),
[CurseForge Upload API](https://support.curseforge.com/support/solutions/articles/9000197321),
[mc-publish](https://github.com/Kira-NT/mc-publish),
[GitHub deployments](https://docs.github.com/en/rest/deployments/deployments).
