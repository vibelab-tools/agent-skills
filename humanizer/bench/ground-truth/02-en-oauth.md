# Claim inventory: 02-en-oauth

Copied from the pre-registered `Skillproofdev/text-humanizer` benchmark inventory dated 2026-07-10.

| ID | Type | Claim that must survive |
| --- | --- | --- |
| C1 | fact | OAuth 2.0 is the industry-standard protocol for delegated authorization. |
| C2 | example | “Sign in with Google” is an example of OAuth in action. |
| C3 | distinction | OAuth concerns authorization, meaning what you can access, rather than authentication, meaning who you are; direction must survive. |
| C4 | claim | OAuth lets a third-party application access user resources without seeing the user's password. |
| C5 | list | Four roles and mappings: resource owner is the user; client is the requesting application; authorization server issues tokens; resource server hosts protected data. |
| C6 | fact | The most common flow is the Authorization Code flow. |
| C7 | sequence | Redirect to authorization server; user consents; code returns to client redirect URI; client exchanges code plus client secret for access token; client uses token against resource server. Order is part of the claim. |
| C8 | number and qualifier | Access tokens are typically short-lived and often expire after one hour; both qualifiers matter. |
| C9 | fact | Refresh tokens let clients obtain new access tokens without prompting the user again. |
| C10 | fact | For single-page applications, PKCE means Proof Key for Code Exchange and removes the need to store a client secret in the browser. |

Hard failures include swapping authorization and authentication; dropping or remapping a role or flow step; changing one hour or hardening its qualifier; changing the PKCE expansion or purpose; or adding new security advice.
