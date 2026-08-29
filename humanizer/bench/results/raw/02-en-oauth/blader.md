# OAuth 2.0 explained for junior developers

Authentication and authorization are two parts of web security. Authentication verifies who you are. Authorization determines what you can access. OAuth 2.0 handles authorization, such as when you click "Sign in with Google."

OAuth lets another application access a user's resources, such as email or calendar data, without giving that application the user's password.

OAuth 2.0 has four roles:

- The resource owner is the user.
- The client is the application requesting access.
- The authorization server issues tokens.
- The resource server stores the protected data.

The most common flow is the Authorization Code flow. First, the client sends the user to the authorization server. The user grants access there, and the server sends an authorization code to the client's redirect URI. The client exchanges that code, along with its client secret, for an access token. It can then use the access token to request data from the resource server.

Access tokens are usually short-lived and often expire after about one hour. A refresh token lets the client get a new access token without asking the user for consent again. For single-page applications, PKCE (Proof Key for Code Exchange) adds protection by removing the need to store a client secret in the browser.

These concepts provide a foundation for understanding modern API security.
