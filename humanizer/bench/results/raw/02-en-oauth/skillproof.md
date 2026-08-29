# OAuth 2.0 Explained for Junior Developers

Authentication and authorization are two basic parts of web security. OAuth 2.0 is the industry-standard protocol for delegated authorization. If you have used “Sign in with Google,” you have already seen OAuth in action.

OAuth is about authorization, not authentication. Authentication checks who you are; authorization controls what you can access. With OAuth 2.0, a third-party application can access a user’s resources—such as an email address or calendar—without receiving the user’s password.

OAuth uses four roles:

- The resource owner is the user.
- The client is the application requesting access.
- The authorization server issues tokens.
- The resource server stores the protected data.

Together, these roles handle the authorization process and help keep each step secure.

The most common flow is the Authorization Code flow. The client first sends the user to the authorization server. After the user grants consent, the authorization server sends an authorization code to the client’s redirect URI. The client then exchanges that code, along with its client secret, for an access token. It can use the access token to request data from the resource server.

Access tokens are usually short-lived and often expire after one hour. A refresh token lets the client get a new access token without asking the user to grant access again.

For single-page applications, the PKCE extension (Proof Key for Code Exchange) provides extra protection by removing the need to store a client secret in the browser.

Once you understand these basics, you have a solid starting point for working with modern API security.
