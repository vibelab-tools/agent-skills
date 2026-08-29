# OAuth 2.0 Explained for Junior Developers

Authentication and authorization are crucial pillars of modern web security, and OAuth 2.0 stands as the industry-standard protocol for delegated authorization. If you've ever clicked "Sign in with Google," you've experienced OAuth in action. Let's delve into how it actually works.

First, it's important to note that OAuth is about authorization, not authentication. Authorization determines what you can access, while authentication verifies who you are. OAuth 2.0 allows a third-party application to access a user's resources — such as their email address or calendar — without ever seeing the user's password.

The protocol involves four key roles: the resource owner (the user), the client (the application requesting access), the authorization server (which issues tokens), and the resource server (which hosts the protected data). These roles work together in a carefully orchestrated dance to ensure security at every step.

The most common flow is the Authorization Code flow. It works as follows: the client redirects the user to the authorization server, where the user grants consent. The authorization server then returns an authorization code to the client's redirect URI. The client exchanges this code — along with its client secret — for an access token. Finally, the client uses the access token to request resources from the resource server.

Access tokens are typically short-lived, often expiring after one hour, and refresh tokens allow clients to obtain new access tokens without prompting the user again. For single-page applications, the PKCE extension (Proof Key for Code Exchange) adds an additional layer of protection by eliminating the need to store a client secret in the browser.

Ultimately, mastering OAuth 2.0 is a journey, not a destination. By understanding these foundational concepts, you'll be well-equipped to navigate the complex landscape of modern API security.
