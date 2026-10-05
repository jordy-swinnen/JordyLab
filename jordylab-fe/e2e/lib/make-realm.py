#!/usr/bin/env python3
"""Derive the throwaway realm for one E2E run from the dev realm export (never edits it).

Reads credentials and ports from the environment and writes the realm JSON to the path in argv[1]. Nothing here prints a
credential. Users: the service accounts of the dev realm stay; the dev person is dropped; two test users are added
(admin, and a guest that tests create through the sign-up UI instead, so only the admin is imported), plus a service
account client ('e2e-ingest') that can call the scanner endpoints.
"""
import json
import os
import sys

DEV_REALM = os.path.join(os.path.dirname(__file__), "..", "..", "..", "jordylab-be", "compose", "keycloak-realm-export.json")


def main() -> None:
    output_path = sys.argv[1]
    web_origin = f"http://localhost:{os.environ['E2E_WEB_PORT']}"
    realm = json.load(open(DEV_REALM))

    realm.pop("loginTheme", None)  # the custom theme is not mounted in the throwaway Keycloak
    realm["sslRequired"] = "none"

    for client in realm["clients"]:
        if client["clientId"] == "jordylab-host":
            client["redirectUris"] = [f"{web_origin}/*", web_origin]
            client["webOrigins"] = [web_origin]
            # keycloak-js signs out with the bare origin as the redirect target; the dev realm lists its dev ports here.
            client.setdefault("attributes", {})["post.logout.redirect.uris"] = f"{web_origin}/*##{web_origin}"

        if client["clientId"] == "jordylab-backend":
            client["secret"] = os.environ["E2E_BACKEND_CLIENT_SECRET"]  # per run, never the dev constant
        if client["clientId"] == "jordylab-mobile":
            # The web origin callback is for browser runs; the App Link callback is what the debug APK declares for the emulator tests
            # (-PjordylabAppLinkHost, see run.sh). The WebView origin https://localhost stays a web origin from the dev export.
            client["redirectUris"] = [f"{web_origin}/mobile/callback", "https://e2e.jordylab.test/mobile/callback"]
            client.setdefault("attributes", {})["post.logout.redirect.uris"] = f"{web_origin}/mobile/*"

    realm["clients"].append({
        "clientId": "e2e-ingest",
        "enabled": True,
        "publicClient": False,
        "serviceAccountsEnabled": True,
        "standardFlowEnabled": False,
        "directAccessGrantsEnabled": False,
        "secret": os.environ["E2E_INGEST_CLIENT_SECRET"],
        "protocol": "openid-connect",
    })

    service_accounts = [user for user in realm["users"] if user["username"].startswith("service-account-")]
    realm["users"] = service_accounts + [
        {
            "username": "service-account-e2e-ingest",
            "enabled": True,
            "serviceAccountClientId": "e2e-ingest",
            "realmRoles": ["gamecatalog-scanner", "mobile-release-publisher"],
        },
        {
            "username": os.environ["E2E_ADMIN_USERNAME"],
            "email": "e2e-admin@example.test",
            "emailVerified": True,
            "enabled": True,
            "firstName": "E2E",
            "lastName": "Admin",
            "realmRoles": ["admin"],
            "credentials": [{"type": "password", "value": os.environ["E2E_ADMIN_PASSWORD"], "temporary": False}],
        },
    ]

    with open(output_path, "w") as handle:
        json.dump(realm, handle, indent=2)


if __name__ == "__main__":
    main()
