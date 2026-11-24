"""Prepare Xcode's manual export mapping for the signed iOS app and its widget."""

import argparse
import hashlib
import plistlib
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID


def distribution_identity(profile, bundle_id, *, needs_push):
    team, = profile["TeamIdentifier"]
    certificate, = profile["DeveloperCertificates"]
    entitlements = profile["Entitlements"]
    expires = profile["ExpirationDate"]
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
        raise ValueError(f"Expected a current App Store distribution profile for {bundle_id}")
    profile_uuid = profile["UUID"]
    if str(UUID(profile_uuid)) != profile_uuid.lower():
        raise ValueError(f"Invalid profile UUID for {bundle_id}")
    # Xcode identifies the certificate by this SHA-1 fingerprint.
    fingerprint = hashlib.sha1(certificate).hexdigest().upper()
    return team, fingerprint, profile_uuid


def export_options(app_profile, widget_profile, *, app_bundle, widget_bundle, audience):
    if audience not in {"internal", "app-store"}:
        raise ValueError("Unknown build audience")
    team, fingerprint, app_id = distribution_identity(app_profile, app_bundle, needs_push=True)
    widget_team, widget_fingerprint, widget_id = distribution_identity(
        widget_profile, widget_bundle, needs_push=False
    )
    if (team, fingerprint) != (widget_team, widget_fingerprint) or app_id.lower() == widget_id.lower():
        raise ValueError("App and widget need distinct profiles for the same distribution identity")
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
    parser.add_argument("app_bundle")
    parser.add_argument("widget_bundle")
    parser.add_argument("audience", choices=("internal", "app-store"))
    args = parser.parse_args()
    root = args.directory
    options = export_options(
        plistlib.loads((root / "profile.plist").read_bytes()),
        plistlib.loads((root / "widget.plist").read_bytes()),
        app_bundle=args.app_bundle,
        widget_bundle=args.widget_bundle,
        audience=args.audience,
    )
    (root / "ExportOptions.plist").write_bytes(plistlib.dumps(options))
