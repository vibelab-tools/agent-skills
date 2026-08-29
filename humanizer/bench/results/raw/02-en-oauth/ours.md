# OAuth 2.0 Explained for Junior Developers

Authentication and authorization are central to web security. OAuth 2.0 is the industry-standard protocol for delegated authorization. If you have clicked “Sign in with Google,” you have used OAuth in action.

OAuth is about authorization, not authentication. Authentication verifies who you are. Authorization determines what you can access. OAuth 2.0 lets a third-party application access a user’s resources, such as email or calendar data, without seeing the user’s password.

OAuth uses four main roles:

- The resource owner: the user
- The client: the application requesting access
- The authorization server: the server that issues tokens
- The resource server: the server that stores the protected data

The most common flow is the Authorization Code flow. First, the client redirects the user to the authorization server. The user grants consent there, and the authorization server sends an authorization code to the client’s redirect URI. The client exchanges that code, along with its client secret, for an access token. It can then use the access token to request resources from the resource server.

Access tokens are usually short-lived and often expire after one hour. A refresh token lets the client get a new access token without asking the user for consent again. For single-page applications, the PKCE extension (Proof Key for Code Exchange) provides extra protection by removing the need to store a client secret in the browser.

OAuth 2.0 has many details, but these concepts are the foundation. Once you understand them, it becomes easier to work with APIs that use OAuth for security.
