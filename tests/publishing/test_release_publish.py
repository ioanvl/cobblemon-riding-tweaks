"""Release guards and retry behavior without credentials or remote writes."""

import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("release_publish", ROOT / "scripts/release_publish.py")
publish = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(publish)


def fixture(loader="fabric"):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as jar:
        if loader == "fabric":
            jar.writestr("fabric.mod.json", json.dumps({"id": publish.MOD_ID, "version": "1.3.0",
                "depends": {"minecraft": "1.21.1", "java": ">=21", "fabricloader": "*",
                            "fabric-api": "*", "cobblemon": ">=1.7.3"}}))
        else:
            jar.writestr("META-INF/neoforge.mods.toml", '''
[[mods]]
modId="cobblemon_riding_tweaks"
version="1.3.0"
[[dependencies.cobblemon_riding_tweaks]]
modId="minecraft"
type="required"
versionRange="[1.21.1]"
[[dependencies.cobblemon_riding_tweaks]]
modId="neoforge"
type="required"
[[dependencies.cobblemon_riding_tweaks]]
modId="cobblemon"
type="required"
''')
    data = buffer.getvalue()
    name = f"cobblemon_riding_tweaks-{loader}-1.21.1-1.3.0.jar"
    asset = {"name": name, "size": len(data), "state": "uploaded",
             "digest": "sha256:" + hashlib.sha256(data).hexdigest(),
             "browser_download_url": f"https://github.com/{publish.REPO}/releases/download/v1.3.0/{name}"}
    return {"id": 42, "tag_name": "v1.3.0", "draft": False, "prerelease": False,
            "published_at": "2026-09-26T00:00:00Z", "body": "Reviewed notes\n", "assets": [asset]}, data


class ReleaseGuards(unittest.TestCase):
    def test_both_loaders_validate_reviewed_bytes(self):
        for loader in ["fabric", "neoforge"]:
            with self.subTest(loader=loader):
                release, data = fixture(loader)
                asset, version, minecraft = publish.select_asset(release, "v1.3.0", loader)
                self.assertEqual(publish.check_jar(data, asset, version, minecraft, loader),
                                 hashlib.sha256(data).hexdigest())

    def test_unreviewed_or_mismatched_releases_are_rejected(self):
        release, _ = fixture()
        variants = [{"draft": True}, {"prerelease": True}, {"body": " "},
                    {"tag_name": "v1.2.0"}, {"published_at": None}, {"assets": []},
                    {"assets": release["assets"] * 2}]
        for changes in variants:
            with self.subTest(changes=changes), self.assertRaises(publish.PublishError):
                publish.select_asset(release | changes, "v1.3.0", "fabric")
        for tag in ["../main", "$(exit 1)", "v1.3.0-beta", "main"]:
            with self.subTest(tag=tag), self.assertRaises(publish.PublishError):
                publish.select_asset(release, tag, "fabric")

    def test_asset_substitution_and_sources_are_rejected(self):
        release, data = fixture()
        for changes in [{"digest": None}, {"state": "new"},
                        {"name": release["assets"][0]["name"].replace(".jar", "-sources.jar")},
                        {"browser_download_url": "https://example.com/other.jar"}]:
            changed = copy.deepcopy(release)
            changed["assets"][0].update(changes)
            with self.subTest(changes=changes), self.assertRaises(publish.PublishError):
                publish.select_asset(changed, "v1.3.0", "fabric")
        asset = release["assets"][0]
        for changed in [data + b"x", b"X" + data[1:]]:
            with self.assertRaises(publish.PublishError):
                publish.check_jar(changed, asset, "1.3.0", "1.21.1", "fabric")
        with self.assertRaises(publish.PublishError):
            publish.check_jar(data, asset, "1.4.0", "1.21.1", "fabric")

    def test_modrinth_existing_version_checks_loader_and_bytes(self):
        plan = {"version": "1.3.0", "loader": "fabric", "minecraft": "1.21.1", "sha512": "abc"}
        version = {"version_number": "1.3.0", "loaders": ["fabric"], "game_versions": ["1.21.1"],
                   "project_id": publish.PROJECT_ID, "id": "version1", "status": "listed",
                   "files": [{"hashes": {"sha512": "abc"}}]}
        self.assertTrue(publish.modrinth_existing([version], plan).endswith("/version1"))
        self.assertIsNone(publish.modrinth_existing([version | {"loaders": ["neoforge"]}], plan))
        for changes in [{"files": [{"hashes": {"sha512": "different"}}]}, {"status": "draft"}]:
            with self.assertRaises(publish.PublishError):
                publish.modrinth_existing([version | changes], plan)


class WorkflowBehavior(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.env = {"RELEASE_TAG": "v1.3.0", "PUBLISH_PLATFORM": "curseforge", "PUBLISH_LOADER": "fabric",
                    "DO_PUBLISH": "false", "RETRY_UNCONFIRMED": "false"}
        self.env_patch = patch.dict(os.environ, self.env, clear=True)
        self.env_patch.start()
        self.addCleanup(self.env_patch.stop)
        self.work_patch = patch.object(publish, "WORK", Path(self.temp.name))
        self.work_patch.start()
        self.addCleanup(self.work_patch.stop)

    def prepare(self, status=None):
        release, data = fixture()
        with patch.object(publish, "gh", return_value=release) as gh, \
             patch.object(publish, "request", return_value=data), \
             patch.object(publish, "prior_upload", return_value=status), \
             patch.object(publish, "summary"), patch.object(publish, "output") as output:
            publish.prepare()
            gh.assert_called_once_with("releases/tags/v1.3.0")
            return output.call_args.kwargs

    def test_dry_run_writes_exact_changelog_and_never_mutates_github(self):
        self.prepare()
        self.assertEqual((publish.WORK / "changelog.md").read_bytes(), b"Reviewed notes\n")
        with patch.object(publish, "gh") as gh, self.assertRaises(publish.PublishError):
            publish.reserve()
        gh.assert_not_called()

    def test_successful_upload_is_skipped(self):
        os.environ["DO_PUBLISH"] = "true"
        result = self.prepare({"state": "success", "environment_url": "https://www.curseforge.com/file/1"})
        self.assertEqual(result["exists"], "true")
        with patch.object(publish, "gh") as gh, self.assertRaises(publish.PublishError):
            publish.reserve()
        gh.assert_not_called()

    def test_uncertain_upload_blocks_until_explicit_retry(self):
        os.environ["DO_PUBLISH"] = "true"
        for state in ["pending", "in_progress", "error"]:
            with self.subTest(state=state), self.assertRaises(publish.PublishError):
                self.prepare({"state": state})
        os.environ["RETRY_UNCONFIRMED"] = "true"
        self.assertEqual(self.prepare({"state": "error"})["exists"], "false")

    def test_attempt_is_recorded_without_merging_or_changing_release(self):
        self.prepare()
        os.environ["DO_PUBLISH"] = "true"
        with patch.object(publish, "gh", side_effect=[{"id": 123}, {}]) as gh, patch.object(publish, "output"):
            publish.reserve()
        path, payload = gh.call_args_list[0].args
        self.assertEqual(path, "deployments")
        self.assertFalse(payload["auto_merge"])
        self.assertEqual(payload["ref"], "v1.3.0")
        self.assertEqual(payload["payload"]["release_id"], 42)
        self.assertEqual(gh.call_args_list[1].args[1]["state"], "in_progress")

    def test_changed_jar_cannot_reuse_previous_upload_record(self):
        plan = {"release_id": 42, "sha256": "new", "environment": "curseforge-fabric"}
        with patch.object(publish, "gh", return_value=[{"id": 1, "payload": {"release_id": 42, "sha256": "old"}}]), \
             self.assertRaises(publish.PublishError):
            publish.prior_upload(plan)

    def test_finish_requires_store_url_and_preserves_older_successes(self):
        os.environ.update({"DEPLOYMENT_ID": "123", "UPLOAD_OUTCOME": "success", "UPLOAD_URL": ""})
        with patch.object(publish, "gh") as gh, self.assertRaises(publish.PublishError):
            publish.finish()
        gh.assert_not_called()
        os.environ["UPLOAD_URL"] = "https://www.curseforge.com/minecraft/mc-mods/cobblemon-riding-tweaks/files/123"
        with patch.object(publish, "gh") as gh, patch.object(publish, "summary"):
            publish.finish()
        status = gh.call_args.args[1]
        self.assertEqual(status["state"], "success")
        self.assertFalse(status["auto_inactive"])


if __name__ == "__main__":
    unittest.main()
