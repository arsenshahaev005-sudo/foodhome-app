"""Verify resolved metadata of the built iOS app and widget before uploading."""

import argparse
import plistlib
from pathlib import Path

from ios_export_options import distribution_target


def plist(path):
    return plistlib.loads(path.read_bytes())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify_archive(app, target, audience, *, build=None, signing_dir=None):
    config = distribution_target(target, audience)
    bundle = config["bundleId"]
    widget = app / "PlugIns" / "FoodHomeOrderStatus.appex"
    info, extension = plist(app / "Info.plist"), plist(widget / "Info.plist")
    require(info.get("CFBundleIdentifier") == bundle and extension.get("CFBundleIdentifier") == bundle + ".OrderStatus",
            "Идентификаторы приложения или виджета не совпадают с выбранным окружением")
    require(info.get("CFBundleDisplayName") == config["displayName"], "Неверное имя приложения на экране iPhone")
    require(info.get("FoodHomeWebHost") == extension.get("FoodHomeWebHost") == config["webHost"],
            "Приложение или виджет открывает неверный домен")
    schemes = [scheme for entry in info.get("CFBundleURLTypes", []) for scheme in entry.get("CFBundleURLSchemes", [])]
    require(schemes == [bundle + ".auth"] and not extension.get("CFBundleURLTypes"), "Неверный callback авторизации")
    require(info.get("FoodHomeAPSEnvironment") == "production" and info.get("NSSupportsLiveActivities") is True,
            "Неверные Release-настройки APNs или Live Activities")
    require(info.get("ITSAppUsesNonExemptEncryption") is False, "Нет ожидаемой декларации шифрования")
    require(extension.get("NSExtension", {}).get("NSExtensionPointIdentifier") == "com.apple.widgetkit-extension",
            "Расширение не является виджетом статуса заказа")
    version, number = info.get("CFBundleShortVersionString"), info.get("CFBundleVersion")
    require(version and version == extension.get("CFBundleShortVersionString") and
            number and number == extension.get("CFBundleVersion") and (build is None or number == build),
            "Версии или номера сборки приложения и виджета не совпадают")
    if signing_dir is not None:
        options = plist(signing_dir / "ExportOptions.plist")
        require(options.get("testFlightInternalTestingOnly") is (audience == "internal"), "Неверная аудитория экспорта")
        team = options["teamID"]
        for kind, expected in (("profile", bundle), ("widget", bundle + ".OrderStatus")):
            signed = plist(signing_dir / (kind + "-entitlements.plist"))
            embedded = plist(signing_dir / (kind + "-embedded.plist"))
            require(signed.get("application-identifier") == team + "." + expected and
                    signed.get("com.apple.developer.team-identifier") == team and not signed.get("get-task-allow"),
                    "Подпись не подтверждает команду и идентификатор " + expected)
            require(embedded.get("UUID") == options["provisioningProfiles"].get(expected),
                    "В архив встроен другой профиль " + expected)
            if kind == "profile":
                require(signed.get("aps-environment") == "production", "Подпись приложения не разрешает APNs Production")
    return version


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("app", type=Path)
    parser.add_argument("target", choices=("production", "staging"))
    parser.add_argument("audience", choices=("internal", "app-store"))
    parser.add_argument("--build")
    parser.add_argument("--signing-dir", type=Path)
    args = parser.parse_args()
    print(verify_archive(args.app, args.target, args.audience, build=args.build, signing_dir=args.signing_dir))
