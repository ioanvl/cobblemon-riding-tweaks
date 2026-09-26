#!/usr/bin/env python3
"""Validate reviewed release assets and track store uploads; never build jars."""

import hashlib
import io
import json
import os
from pathlib import Path
import re
import sys
import tomllib
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

REPO = "ioanvl/cobblemon-riding-tweaks"
MOD_ID = "cobblemon_riding_tweaks"
PROJECT_ID = "8JWE9x1o"
GITHUB = "https://api.github.com"
MODRINTH = "https://api.modrinth.com/v2"
WORK = Path("build/publishing")
MAX_JAR_SIZE = 32 * 1024 * 1024


class PublishError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise PublishError(message)


def request(url, payload=None, github=False, raw=False):
    headers = {"User-Agent": REPO + " publishing workflow"}
    if github:
        require(url.startswith(GITHUB + "/"), "Unexpected GitHub API host")
        headers["Accept"] = "application/vnd.github+json"
        headers["X-GitHub-Api-Version"] = "2022-11-28"
        if os.environ.get("GH_TOKEN"):
            headers["Authorization"] = "Bearer " + os.environ["GH_TOKEN"]
    data = None
    if payload is not None:
        require(github, "Only GitHub tracking writes are allowed in this helper")
        headers["Content-Type"] = "application/json"
        data = json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            body = response.read(MAX_JAR_SIZE + 1)
        require(len(body) <= MAX_JAR_SIZE, "Response exceeds size limit")
        return body if raw else json.loads(body)
    except urllib.error.HTTPError as error:
        # Do not echo remote bodies or request headers containing credentials.
        raise PublishError(f"HTTP {error.code} from {urllib.parse.urlparse(url).hostname}") from None
    except urllib.error.URLError:
        raise PublishError(f"Network request failed for {urllib.parse.urlparse(url).hostname}") from None


def gh(path, payload=None):
    return request(f"{GITHUB}/repos/{REPO}/{path}", payload, github=True)


def output(**values):
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as target:
            for key, value in values.items():
                delimiter = uuid.uuid4().hex
                target.write(f"{key}<<{delimiter}\n{value}\n{delimiter}\n")


def summary(text):
    print(text)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as target:
            target.write(text + "\n\n")


def select_asset(release, tag, loader):
    require(re.fullmatch(r"v\d+\.\d+\.\d+", tag), "Use a stable release tag such as v1.3.0")
    require(release.get("tag_name") == tag, "Release tag does not match")
    require(not release.get("draft") and not release.get("prerelease") and release.get("published_at"),
            "Only published stable GitHub releases can be distributed")
    require(bool((release.get("body") or "").strip()), "Release notes are empty")
    version = tag[1:]
    pattern = rf"{MOD_ID}-{loader}-(\d+\.\d+(?:\.\d+)?)-{re.escape(version)}\.jar"
    matches = [(a, re.fullmatch(pattern, a["name"])) for a in release["assets"]]
    matches = [(a, match) for a, match in matches if match]
    require(len(matches) == 1, f"Expected exactly one {loader} runtime jar for {tag}")
    asset, match = matches[0]
    require(asset.get("state") == "uploaded", "Jar upload is incomplete")
    require(0 < asset["size"] <= MAX_JAR_SIZE, "Invalid jar size")
    require(re.fullmatch(r"sha256:[0-9a-f]{64}", asset.get("digest") or ""),
            "Release asset must have a GitHub SHA-256 digest")
    expected_url = f"https://github.com/{REPO}/releases/download/{tag}/{asset['name']}"
    require(asset["browser_download_url"] == expected_url, "Unexpected asset download URL")
    return asset, version, match[1]


def check_jar(data, asset, version, minecraft, loader):
    require(len(data) == asset["size"], "Downloaded jar size differs from GitHub")
    digest = hashlib.sha256(data).hexdigest()
    require("sha256:" + digest == asset["digest"], "Downloaded jar hash differs from GitHub")
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        if loader == "fabric":
            metadata = json.loads(jar.read("fabric.mod.json"))
            require(metadata["id"] == MOD_ID and metadata["version"] == version, "Wrong Fabric mod/version")
            require(metadata["depends"]["minecraft"] == minecraft, "Wrong Minecraft version")
            required = set(metadata["depends"]) - {"minecraft", "java", "fabricloader"}
            require(required == {"cobblemon", "fabric-api"}, "Update publishing dependency mappings for this jar")
        else:
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
            require(len(metadata["mods"]) == 1 and metadata["mods"][0]["modId"] == MOD_ID
                    and metadata["mods"][0]["version"] == version, "Wrong NeoForge mod/version")
            deps = {d["modId"]: d for d in metadata["dependencies"][MOD_ID]}
            require(deps["minecraft"]["versionRange"] == f"[{minecraft}]", "Wrong Minecraft version")
            required = {name for name, dep in deps.items() if dep["type"] == "required"} - {"minecraft", "neoforge"}
            require(required == {"cobblemon"}, "Update publishing dependency mappings for this jar")
    return digest


def modrinth_existing(versions, plan):
    candidates = [v for v in versions if plan["loader"] in v["loaders"] and
                  plan["minecraft"] in v["game_versions"] and v["version_number"] == plan["version"]]
    if not candidates:
        return None
    require(len(candidates) == 1, "Multiple matching Modrinth versions; inspect the store before proceeding")
    version = candidates[0]
    require(version["project_id"] == PROJECT_ID and version.get("status") == "listed", "Existing Modrinth version needs review")
    require(any(f["hashes"].get("sha512") == plan["sha512"] for f in version["files"]),
            "Modrinth already has this version/loader with different jar bytes")
    return f"https://modrinth.com/mod/{PROJECT_ID}/version/{version['id']}"


def prior_upload(plan):
    query = urllib.parse.urlencode({"environment": plan["environment"], "task": "publish-mod", "per_page": 100})
    page = 1
    while True:
        deployments = gh(f"deployments?{query}&page={page}")
        for deployment in deployments:
            payload = deployment.get("payload") or {}
            if isinstance(payload, str):
                payload = json.loads(payload)
            if payload.get("release_id") != plan["release_id"]:
                continue
            require(payload.get("sha256") == plan["sha256"], "Release jar changed after an upload attempt")
            statuses = gh(f"deployments/{deployment['id']}/statuses?per_page=1")
            state = statuses[0] if statuses else {"state": "pending"}
            return state
        if len(deployments) < 100:
            return None
        page += 1


def prepare():
    tag = os.environ["RELEASE_TAG"]
    loader = os.environ["PUBLISH_LOADER"]
    platform = os.environ["PUBLISH_PLATFORM"]
    require(loader in {"fabric", "neoforge"} and platform in {"modrinth", "curseforge"}, "Invalid publishing target")
    require(re.fullmatch(r"v\d+\.\d+\.\d+", tag), "Invalid release tag")
    release = gh("releases/tags/" + tag)
    asset, version, minecraft = select_asset(release, tag, loader)
    data = request(asset["browser_download_url"], raw=True)
    digest = check_jar(data, asset, version, minecraft, loader)
    WORK.mkdir(parents=True, exist_ok=True)
    path = WORK / asset["name"]
    path.write_bytes(data)
    (WORK / "changelog.md").write_text(release["body"], encoding="utf-8")
    plan = {"tag": tag, "version": version, "release_id": release["id"], "loader": loader,
            "platform": platform, "environment": f"{platform}-{loader}", "minecraft": minecraft,
            "file": str(path), "sha256": digest, "sha512": hashlib.sha512(data).hexdigest()}
    existing = None
    if platform == "modrinth":
        versions = request(f"{MODRINTH}/project/{PROJECT_ID}/version")
        existing = modrinth_existing(versions, plan)
    prior = prior_upload(plan)
    if prior and prior["state"] == "success":
        existing = existing or prior.get("environment_url")
        require(existing, "Successful upload record has no store URL")
    if prior and prior["state"] != "success" and not existing:
        require(os.environ.get("RETRY_UNCONFIRMED") == "true" or os.environ.get("DO_PUBLISH") != "true",
                "Previous upload is unconfirmed. Check the store before explicitly retrying this store/loader")
    plan["existing"] = existing
    (WORK / "plan.json").write_text(json.dumps(plan, indent=2) + "\n")
    dependencies = ["cobblemon(required){modrinth:MdwFAVRL}{curseforge:687131}"]
    if loader == "fabric":
        dependencies += ["fabric-api(required){modrinth:P7dR8mSH}{curseforge:306612}",
                         "modmenu(optional){modrinth:mOgUt4GM}{curseforge:308702}"]
    output(file=str(path), name=f"{version} ({loader.title() if loader == 'fabric' else 'NeoForge'})",
           version=version, minecraft=minecraft, dependencies="\n".join(dependencies),
           exists=str(bool(existing)).lower())
    summary(f"{platform}/{loader}: verified {asset['name']} (SHA-256 {digest}).")
    if existing:
        summary(f"Already published: {existing}. Upload skipped.")
    elif os.environ.get("DO_PUBLISH") != "true":
        summary("Dry run only: no upload or deployment record created. Store authentication is not tested.")
    if prior and prior["state"] != "success":
        summary("A previous attempt is unconfirmed; inspect its run and the store before retrying.")


def reserve():
    require(os.environ.get("DO_PUBLISH") == "true", "Publishing is not enabled")
    plan = json.loads((WORK / "plan.json").read_text())
    require(not plan["existing"], "This upload already exists")
    # Workflow concurrency serializes each release/store/loader, including manual retries.
    deployment = gh("deployments", {"ref": plan["tag"], "task": "publish-mod", "auto_merge": False,
                    "required_contexts": [], "environment": plan["environment"],
                    "description": f"Publish {plan['tag']} to {plan['environment']}",
                    "payload": {"release_id": plan["release_id"], "sha256": plan["sha256"]},
                    "production_environment": True})
    require(isinstance(deployment.get("id"), int), "GitHub did not create an upload record")
    output(deployment_id=deployment["id"])
    gh(f"deployments/{deployment['id']}/statuses", {"state": "in_progress", "auto_inactive": False,
                                                  "description": "Store upload started"})
    summary(f"Upload attempt recorded as GitHub deployment {deployment['id']}.")


def finish():
    deployment_id = os.environ["DEPLOYMENT_ID"]
    require(deployment_id.isdecimal(), "Invalid deployment record")
    platform = os.environ["PUBLISH_PLATFORM"]
    url = os.environ.get("UPLOAD_URL", "")
    success = os.environ.get("UPLOAD_OUTCOME") == "success"
    prefix = (f"https://modrinth.com/mod/" if platform == "modrinth" else
              "https://www.curseforge.com/minecraft/mc-mods/cobblemon-riding-tweaks/files/")
    require(not success or url.startswith(prefix), "Missing or unexpected published version URL")
    status = {"state": "success" if success else "error", "auto_inactive": False,
              "description": "Store accepted upload" if success else "Unconfirmed upload; inspect store before retry"}
    if success:
        status["environment_url"] = url
    gh(f"deployments/{deployment_id}/statuses", status)
    summary(f"{platform}/{os.environ['PUBLISH_LOADER']}: " +
            (f"upload accepted: {url}" if success else "upload unconfirmed; inspect the store before retrying."))


if __name__ == "__main__":
    try:
        commands = {"prepare": prepare, "reserve": reserve, "finish": finish}
        require(len(sys.argv) == 2 and sys.argv[1] in commands, "Usage: release_publish.py prepare|reserve|finish")
        commands[sys.argv[1]]()
    except (PublishError, KeyError, ValueError, zipfile.BadZipFile) as error:
        print(f"Publishing check failed: {error}", file=sys.stderr)
        sys.exit(1)
