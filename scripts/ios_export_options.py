"""Prepare Xcode's manual export mapping for the signed iOS app and its widget."""

import argparse
import hashlib
import json
import plistlib
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID


def distribution_target(name, audience):
    targets = json.loads(Path(__file__).with_name("ios_distribution.json").read_text(encoding="utf-8"))
    if name not in targets or audience not in targets[name]["audiences"]:
        raise ValueError("Эта сборка не допускает запрошенную аудиторию TestFlight")
    return targets[name]


def distribution_identity(profile, bundle_id, *, needs_push):
    teams = profile.get("TeamIdentifier", [])
    certificates = profile.get("DeveloperCertificates", [])
    entitlements = profile.get("Entitlements")
    expires = profile.get("ExpirationDate")
    if (len(teams) != 1 or not isinstance(teams[0], str) or not teams[0]
            or len(certificates) != 1 or not isinstance(certificates[0], bytes) or not certificates[0]
            or not isinstance(entitlements, dict) or not isinstance(expires, datetime)):
        raise ValueError(f"Нет ожидаемых данных команды, сертификата или срока в профиле {bundle_id}")
    team, certificate = teams[0], certificates[0]
    if expires.tzinfo is None:
        expires = expires.replace(tzinfo=timezone.utc)
    if (
        entitlements.get("application-identifier") != f"{team}.{bundle_id}"
        or not entitlements.get("beta-reports-active")
        or entitlements.get("get-task-allow")
        or profile.get("ProvisionsAllDevices")
        or "ProvisionedDevices" in profile
        or expires <= datetime.now(timezone.utc)
        or (needs_push and entitlements.get("aps-environment") != "production")
    ):
        raise ValueError(f"Нужен действующий профиль App Store для {bundle_id}")
    profile_uuid = profile.get("UUID")
    try:
        valid_uuid = isinstance(profile_uuid, str) and str(UUID(profile_uuid)) == profile_uuid.lower()
    except ValueError:
        valid_uuid = False
    if not valid_uuid:
        raise ValueError(f"Некорректный UUID профиля для {bundle_id}")
    # Xcode identifies the certificate by this SHA-1 fingerprint.
    fingerprint = hashlib.sha1(certificate).hexdigest().upper()
    return team, fingerprint, profile_uuid


def export_options(app_profile, widget_profile, *, app_bundle, widget_bundle, audience):
    target_name = "staging" if app_bundle == "market.foodhome.app.staging" else "production"
    target = distribution_target(target_name, audience)
    if app_bundle != target["bundleId"] or widget_bundle != app_bundle + ".OrderStatus":
        raise ValueError("Идентификаторы приложения и расширения не соответствуют целевой сборке")
    if "profileNames" in target and [app_profile.get("Name"), widget_profile.get("Name")] != target["profileNames"]:
        raise ValueError("CMS содержит неверные имена профилей staging")
    team, fingerprint, app_id = distribution_identity(app_profile, app_bundle, needs_push=True)
    widget_team, widget_fingerprint, widget_id = distribution_identity(
        widget_profile, widget_bundle, needs_push=False
    )
    if (team, fingerprint) != (widget_team, widget_fingerprint) or app_id.lower() == widget_id.lower():
        raise ValueError("Для приложения и виджета нужны разные профили с одним сертификатом команды")
    return {
        "method": "app-store-connect",
        "destination": "upload",
        "teamID": team,
        "signingStyle": "manual",
        "signingCertificate": fingerprint,
        "provisioningProfiles": {app_bundle: app_id, widget_bundle: widget_id},
        "manageAppVersionAndBuildNumber": False,
        "testFlightInternalTestingOnly": audience == "internal",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("target", choices=("production", "staging"))
    parser.add_argument("audience", choices=("internal", "app-store"))
    args = parser.parse_args()
    root = args.directory
    bundle = distribution_target(args.target, args.audience)["bundleId"]
    options = export_options(
        plistlib.loads((root / "profile.plist").read_bytes()),
        plistlib.loads((root / "widget.plist").read_bytes()),
        app_bundle=bundle,
        widget_bundle=bundle + ".OrderStatus",
        audience=args.audience,
    )
    (root / "ExportOptions.plist").write_bytes(plistlib.dumps(options))
