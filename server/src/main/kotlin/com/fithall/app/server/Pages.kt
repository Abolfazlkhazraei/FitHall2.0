package com.fithall.app.server

import com.fithall.app.api.Api
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * The public web pages: privacy policy, terms, account deletion (Google Play needs a web link for
 * it) and a friendly 404. The policy and terms are rendered from docs/ (copied into resources by
 * the build) so there's one source for each.
 */
class Pages(firebaseWebApiKey: String?, firebaseProjectId: String? = null) {

    // Only a well-formed id goes into the page and its security policy.
    private val projectId = firebaseProjectId?.takeIf { PROJECT_ID.matches(it) }

    private val extensions = listOf(TablesExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).build()

    val privacy: String = markdownPage("FitHall Privacy Policy", "pages/privacy-policy.md")
    val terms: String = markdownPage("FitHall Terms of Use", "pages/terms.md")
    val deleteAccount: String = page("Delete your FitHall account", deleteAccountBody(firebaseWebApiKey))

    /**
     * Deleting a Google or Facebook account signs in with Firebase's web SDK in a popup, which
     * needs its scripts, Google's API loader and an iframe on the project's auth domain.
     */
    val deleteAccountCsp: String = if (firebaseWebApiKey == null || projectId == null) PAGE_CSP else
        "default-src 'none'; style-src 'unsafe-inline'; " +
            "script-src 'self' https://www.gstatic.com https://apis.google.com; " +
            "connect-src https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://www.googleapis.com; " +
            "frame-src https://$projectId.firebaseapp.com; " +
            "form-action 'none'; base-uri 'none'; frame-ancestors 'none'"
    val notFound: String = page(
        "Page not found",
        """
        <h1>Page not found</h1>
        <p>There's nothing at this address. It may have moved, or the link may be mistyped.</p>
        <p><a href="/">Go to the FitHall home page</a></p>
        """.trimIndent(),
    )
    val home: String = page(
        "FitHall",
        """
        <h1>FitHall</h1>
        <p>AI-powered nutrition and training, in an app.</p>
        <ul>
          <li><a href="${Api.PRIVACY_PAGE}">Privacy policy</a></li>
          <li><a href="${Api.TERMS_PAGE}">Terms of use</a></li>
          <li><a href="${Api.DELETE_ACCOUNT_PAGE}">Delete your account</a></li>
        </ul>
        """.trimIndent(),
    )

    private fun markdownPage(title: String, resource: String): String {
        val markdown = Pages::class.java.classLoader.getResource(resource)?.readText()
            ?: error("Missing $resource; it's copied from docs/ by processResources")
        return page(title, renderer.render(parser.parse(markdown)))
    }

    private fun deleteAccountBody(apiKey: String?): String {
        val social = if (projectId == null) "" else """
            <p class="muted">Signed up with Google or Facebook? Confirm with the same account:</p>
            <div class="row">
              <button type="button" class="provider" data-provider="google">Delete with Google</button>
              <button type="button" class="provider" data-provider="facebook">Delete with Facebook</button>
            </div>
            <p class="muted">Signed up with an email and password?</p>
        """.trimIndent()
        val form = if (apiKey == null) {
            "<p>Web deletion isn't available right now. Please delete your account in the app, or contact us using the details in the <a href=\"${Api.PRIVACY_PAGE}\">privacy policy</a>.</p>"
        } else {
            """
            <h2>Delete it here</h2>
            <div id="delete" class="card" data-key="${escape(apiKey)}" data-project="${projectId.orEmpty()}">
              <label class="check"><input type="checkbox" id="confirm"> I understand this permanently deletes my account.</label>
              $social
              <form id="delete-form">
                <label>Email <input type="email" id="email" autocomplete="email" required maxlength="254"></label>
                <label>Password <input type="password" id="password" autocomplete="current-password" required maxlength="128"></label>
                <button type="submit" id="submit">Delete my account</button>
              </form>
              <p id="status" role="status" aria-live="polite"></p>
            </div>
            <script type="module" src="${Api.DELETE_ACCOUNT_PAGE}.js"></script>
            """.trimIndent()
        }
        return """
            <h1>Delete your FitHall account</h1>
            <p>Deleting your account removes your FitHall login (your email address, and your password or the link
            to your Google or Facebook account) permanently.
            Your logs (meals, workouts, weigh-ins, plans and coach chat) are stored only on your phone and FitHall's
            server doesn't keep them, so there's nothing else of yours on our servers to delete.</p>
            <h2>In the app</h2>
            <p>Open FitHall, go to <strong>Profile → Account → Delete account</strong>. This deletes your account
            and everything FitHall stored on that phone.</p>
            $form
            <p class="muted">If you delete your account here, your logs stay on your phone until you uninstall FitHall
            or clear its data.</p>
        """.trimIndent()
    }

    companion object {
        /**
         * Deletes the account straight from the browser through Firebase; nothing passes through
         * our server. Email accounts use the REST API; Google and Facebook accounts sign in again
         * with Firebase's web SDK in a popup, kept in memory only, so nothing stays in the browser.
         */
        val deleteAccountScript = """
            const FIREBASE = 'https://www.gstatic.com/firebasejs/$FIREBASE_JS_VERSION/';
            const root = document.getElementById('delete');
            const form = document.getElementById('delete-form');
            const status = document.getElementById('status');
            const confirmBox = document.getElementById('confirm');
            const key = root.dataset.key;
            const projectId = root.dataset.project;
            const messages = {
              INVALID_LOGIN_CREDENTIALS: 'Email or password is incorrect.',
              INVALID_PASSWORD: 'Email or password is incorrect.',
              EMAIL_NOT_FOUND: 'Email or password is incorrect.',
              USER_DISABLED: 'This account has been disabled.',
              TOO_MANY_ATTEMPTS_TRY_LATER: 'Too many attempts. Please wait a few minutes and try again.',
            };
            const providerMessages = {
              'auth/popup-closed-by-user': '',
              'auth/cancelled-popup-request': '',
              'auth/popup-blocked': 'Your browser blocked the sign-in window. Allow pop-ups for this page and try again.',
              'auth/account-exists-with-different-credential': 'This email signs in to FitHall another way. Use the method you signed up with.',
              'auth/unauthorized-domain': "Deleting with Google or Facebook isn't set up for this website yet. Please delete your account in the app.",
              'auth/operation-not-allowed': "This sign-in method isn't turned on. Please delete your account in the app.",
              'auth/user-disabled': 'This account has been disabled.',
            };

            function setBusy(busy, text) {
              root.querySelectorAll('button').forEach((b) => { b.disabled = busy; });
              if (text !== undefined) status.textContent = text;
            }
            function confirmed() {
              if (confirmBox.checked) return true;
              status.textContent = 'Tick the box above to confirm first.';
              confirmBox.focus();
              return false;
            }

            async function call(method, body) {
              const response = await fetch('https://identitytoolkit.googleapis.com/v1/accounts:' + method + '?key=' + encodeURIComponent(key), {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
              });
              const data = await response.json().catch(() => ({}));
              if (!response.ok) {
                const code = ((data.error && data.error.message) || '').split(' ')[0];
                throw new Error(messages[code] || 'Something went wrong. Please try again.');
              }
              return data;
            }

            form.addEventListener('submit', async (event) => {
              event.preventDefault();
              if (!confirmed()) return;
              setBusy(true, 'Deleting…');
              try {
                const email = document.getElementById('email').value.trim();
                const password = document.getElementById('password').value;
                const session = await call('signInWithPassword', { email, password, returnSecureToken: true });
                await call('delete', { idToken: session.idToken });
                form.reset();
                status.textContent = 'Your account has been deleted.';
              } catch (e) {
                status.textContent = e.message;
              } finally {
                setBusy(false);
              }
            });

            // The SDK loads once the user ticks the box, so the popup can open straight from the
            // button press (browsers block popups opened after a long wait).
            let firebase = null;
            function loadFirebase() {
              if (!firebase) {
                firebase = Promise.all([import(FIREBASE + 'firebase-app.js'), import(FIREBASE + 'firebase-auth.js')])
                  .then(([app, auth]) => {
                    const instance = app.initializeApp({ apiKey: key, authDomain: projectId + '.firebaseapp.com', projectId });
                    return {
                      sdk: auth,
                      auth: auth.initializeAuth(instance, {
                        persistence: auth.inMemoryPersistence,
                        popupRedirectResolver: auth.browserPopupRedirectResolver,
                      }),
                    };
                  });
                firebase.catch(() => { firebase = null; });
              }
              return firebase;
            }
            if (projectId) confirmBox.addEventListener('change', () => { if (confirmBox.checked) loadFirebase(); });

            root.querySelectorAll('button.provider').forEach((button) => {
              button.addEventListener('click', async () => {
                if (!confirmed()) return;
                const google = button.dataset.provider === 'google';
                const label = google ? 'Google' : 'Facebook';
                setBusy(true, 'Waiting for ' + label + '…');
                try {
                  const { sdk, auth } = await loadFirebase();
                  const provider = google ? new sdk.GoogleAuthProvider() : new sdk.FacebookAuthProvider();
                  if (google) provider.setCustomParameters({ prompt: 'select_account' });
                  const result = await sdk.signInWithPopup(auth, provider);
                  // Signing in with an account FitHall has never seen creates one; it's removed straight away.
                  const isNew = (sdk.getAdditionalUserInfo(result) || {}).isNewUser;
                  status.textContent = 'Deleting…';
                  await sdk.deleteUser(result.user);
                  status.textContent = isNew
                    ? 'There was no FitHall account for that ' + label + ' account, so there was nothing to delete.'
                    : 'Your account has been deleted.';
                } catch (e) {
                  const code = e && e.code;
                  status.textContent = code in providerMessages ? providerMessages[code] : 'Something went wrong. Please try again.';
                } finally {
                  setBusy(false);
                }
              });
            });
        """.trimIndent()

        /** Pinned so the page never picks up an untested SDK. */
        private const val FIREBASE_JS_VERSION = "12.19.0"
        private val PROJECT_ID = Regex("^[a-z0-9][a-z0-9-]{4,28}[a-z0-9]$")

        private fun escape(text: String) = text
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        fun page(title: String, body: String): String = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>${escape(title)}</title>
            <style>
              :root { color-scheme: light dark; --accent: #16774A; --muted: #5b6660; --bg: #f7faf8; --fg: #16201b; --card: #ffffff; }
              @media (prefers-color-scheme: dark) { :root { --accent: #7fd8a6; --muted: #a7b3ad; --bg: #101512; --fg: #e5ece8; --card: #18201c; } }
              * { box-sizing: border-box; }
              body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
              header { background: linear-gradient(135deg, #0B5A37, #16774A, #0E6E73); color: #fff; padding: 20px 16px; }
              header a { color: #fff; text-decoration: none; font-weight: 700; font-size: 20px; }
              main { max-width: 760px; margin: 0 auto; padding: 24px 16px 48px; }
              h1 { font-size: 28px; line-height: 1.25; } h2 { font-size: 20px; margin-top: 32px; }
              a { color: var(--accent); }
              table { border-collapse: collapse; width: 100%; display: block; overflow-x: auto; font-size: 14px; }
              th, td { border: 1px solid color-mix(in srgb, var(--fg) 20%, transparent); padding: 8px; text-align: left; vertical-align: top; }
              .card { background: var(--card); border-radius: 16px; padding: 16px; display: grid; gap: 12px; }
              form { display: grid; gap: 12px; }
              .row { display: flex; flex-wrap: wrap; gap: 8px; } .row button { flex: 1 1 200px; }
              button.provider { background: transparent; color: inherit; border: 1px solid var(--muted); }
              label { display: grid; gap: 4px; font-weight: 600; } label.check { display: flex; gap: 8px; font-weight: 400; align-items: flex-start; }
              input[type=email], input[type=password] { font: inherit; padding: 10px 12px; border-radius: 10px; border: 1px solid var(--muted); background: transparent; color: inherit; }
              button { font: inherit; font-weight: 600; padding: 12px; border: 0; border-radius: 999px; background: #b3261e; color: #fff; cursor: pointer; }
              button:disabled { opacity: .6; cursor: default; }
              .muted { color: var(--muted); font-size: 14px; }
            </style>
            </head>
            <body>
            <header><a href="/">FitHall</a></header>
            <main>
            $body
            </main>
            </body>
            </html>
        """.trimIndent()
    }
}

/** Pages only load our own script and talk to Firebase; nothing else. */
private const val PAGE_CSP = "default-src 'none'; style-src 'unsafe-inline'; script-src 'self'; " +
    "connect-src https://identitytoolkit.googleapis.com; form-action 'none'; base-uri 'none'; frame-ancestors 'none'"

suspend fun ApplicationCall.respondPage(html: String, status: HttpStatusCode = HttpStatusCode.OK, csp: String = PAGE_CSP) {
    response.header("Content-Security-Policy", csp)
    response.header("X-Content-Type-Options", "nosniff")
    response.header("Referrer-Policy", "no-referrer")
    respondText(html, ContentType.Text.Html.withParameter("charset", "utf-8"), status)
}

fun Route.pages(pages: Pages) {
    get("/") { call.respondPage(pages.home) }
    get(Api.PRIVACY_PAGE) { call.respondPage(pages.privacy) }
    get(Api.TERMS_PAGE) { call.respondPage(pages.terms) }
    get(Api.DELETE_ACCOUNT_PAGE) { call.respondPage(pages.deleteAccount, csp = pages.deleteAccountCsp) }
    get("${Api.DELETE_ACCOUNT_PAGE}.js") {
        call.response.header("X-Content-Type-Options", "nosniff")
        call.respondText(Pages.deleteAccountScript, ContentType.Application.JavaScript)
    }
}
