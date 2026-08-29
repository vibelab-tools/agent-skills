# OAuth 2.0 Explained for Junior Developers

OAuth 2.0 is a standard way for an application to get permission to access a user’s data. If you’ve ever clicked “Sign in with Google,” you’ve used OAuth.

OAuth is about authorization, not authentication. Authentication confirms who you are. Authorization determines what you’re allowed to access. With OAuth 2.0, an application can access resources such as your email or calendar without needing your password.

OAuth uses four main roles:

- The resource owner: the user who owns the data
- The client: the application requesting access
- The authorization server: the server that issues tokens
- The resource server: the server that stores the protected data

The most common OAuth flow is the Authorization Code flow. First, the application redirects the user to the authorization server. The user signs in there and grants permission. The authorization server then sends an authorization code to the application’s redirect URI. The application exchanges that code, along with its client secret, for an access token. It can then use the access token to request data from the resource server.

Access tokens are usually short-lived and often expire after about an hour. Refresh tokens let an application request a new access token without asking the user for consent again.

For single-page applications, PKCE (Proof Key for Code Exchange) adds extra protection. It also means the application does not need to store a client secret in the browser.

OAuth 2.0 can seem complicated at first, but understanding these core concepts gives you a solid foundation for working with modern API security.
