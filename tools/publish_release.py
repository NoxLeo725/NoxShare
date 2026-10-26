import base64
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.error import HTTPError
from urllib.parse import quote, urlencode, urlparse
from urllib.request import Request, urlopen


REPOSITORY = "NoxLeo725/NoxShare"
VERSION = "1.0.7"
TAG = "v" + VERSION
DIRECTORY = "release-assets/" + TAG


def verify_files(manifest, read):
    assets = []
    for entry in manifest["assets"]:
        name = entry["name"]
        if Path(name).name != name or "/" in name or "\\" in name:
            raise RuntimeError("Invalid asset name")
        data = read(DIRECTORY + "/" + name)
        digest = hashlib.sha256(data).hexdigest()
        if len(data) != entry["size"] or digest != entry["sha256"]:
            raise RuntimeError("Asset verification failed: " + name)
        assets.append((entry, data))
    return assets


def publish():
    if os.environ.get("GITHUB_REPOSITORY") != REPOSITORY:
        raise RuntimeError("This publisher is restricted to " + REPOSITORY)
    commit = os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise RuntimeError("Invalid source commit")
    token = os.environ["GH_TOKEN"]
    root = "https://api.github.com/repos/" + REPOSITORY

    def request(path, method="GET", payload=None, data=None, content_type="application/json"):
        url = path if path.startswith("https://") else root + path
        parsed = urlparse(url)
        if parsed.hostname not in ("api.github.com", "uploads.github.com"):
            raise RuntimeError("Unapproved GitHub endpoint")
        if not parsed.path.startswith("/repos/" + REPOSITORY + "/"):
            raise RuntimeError("Unexpected repository endpoint")
        if payload is not None:
            data = json.dumps(payload).encode("utf-8")
        headers = {
            "Authorization": "Bearer " + token,
            "Accept": "application/vnd.github+json",
            "Content-Type": content_type,
            "User-Agent": "NoxShare-release-publisher",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        with urlopen(Request(url, data=data, headers=headers, method=method), timeout=120) as response:
            return json.load(response)

    def read(path):
        item = request("/contents/" + quote(path, safe="/") + "?" + urlencode({"ref": commit}))
        if item.get("encoding") != "base64":
            raise RuntimeError("Expected a base64 repository file: " + path)
        return base64.b64decode(item["content"])

    manifest = json.loads(read(DIRECTORY + "/manifest.json"))
    if manifest["version"] != VERSION:
        raise RuntimeError("Unexpected release version")
    assets = verify_files(manifest, read)
    body = read("docs/releases/" + TAG + ".md").decode("utf-8")

    try:
        release = request("/releases/tags/" + TAG)
    except HTTPError as error:
        if error.code != 404:
            raise
        drafts = request("/releases?per_page=100")
        release = next((item for item in drafts if item["tag_name"] == TAG), None)
        if release is None:
            release = request("/releases", "POST", {
                "tag_name": TAG,
                "target_commitish": commit,
                "name": "NoxShare " + TAG + " · 中文 / English",
                "body": body,
                "draft": True,
                "prerelease": False,
            })

    existing = {asset["name"]: asset for asset in request("/releases/" + str(release["id"]) + "/assets?per_page=100")}
    for entry, data in assets:
        name = entry["name"]
        if name in existing:
            asset = existing[name]
        else:
            upload = release["upload_url"].split("{", 1)[0] + "?" + urlencode({"name": name})
            asset = request(upload, "POST", data=data, content_type=entry["content_type"])
        if asset.get("state") != "uploaded" or asset.get("size") != entry["size"]:
            raise RuntimeError("Incomplete release asset: " + name)
        for attempt in range(6):
            if asset.get("digest"):
                break
            time.sleep(2)
            asset = request("/releases/assets/" + str(asset["id"]))
        if asset.get("digest") != "sha256:" + entry["sha256"]:
            raise RuntimeError("Release asset digest differs: " + name)
        print("Verified release asset:", name)

    if release["draft"]:
        release = request("/releases/" + str(release["id"]), "PATCH", {
            "draft": False,
            "make_latest": "true",
            "body": body,
        })
    if release["draft"] or release["prerelease"]:
        raise RuntimeError("Release was not published")
    print("Published:", release["html_url"])


if __name__ == "__main__":
    if "--check-local" in sys.argv:
        repository = Path(__file__).resolve().parent.parent
        manifest = json.loads((repository / DIRECTORY / "manifest.json").read_text(encoding="utf-8"))
        verified = verify_files(manifest, lambda path: (repository / path).read_bytes())
        print("Verified", len(verified), "local release assets for", TAG)
    else:
        publish()
