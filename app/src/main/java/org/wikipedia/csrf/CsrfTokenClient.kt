package org.wikipedia.csrf

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wikipedia.auth.AccountUtil
import org.wikipedia.dataclient.Service
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.util.log.L
import java.io.IOException
import java.util.concurrent.Semaphore

object CsrfTokenClient {
    private val MUTEX: Semaphore = Semaphore(1)
    private const val ANON_TOKEN = "+\\"
    private const val MAX_RETRIES = 3

    suspend fun getToken(site: WikiSite, type: String = "csrf", svc: Service? = null): String {
        var token = ""
        withContext(Dispatchers.IO) {
            try {
                MUTEX.acquire()
                val service = svc ?: ServiceFactory.get(site)
                var lastError: Throwable? = null
                for (retry in 0 until MAX_RETRIES) {
                    try {
                        val tokenResponse = service.getToken(type)
                        token = if (type == "rollback") {
                            tokenResponse.query?.rollbackToken().orEmpty()
                        } else {
                            tokenResponse.query?.csrfToken().orEmpty()
                        }
                        if (tokenRequiresLogin(token)) {
                            AccountUtil.bailWithLogout()
                            break
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        L.e(e)
                        lastError = e
                    }
                    if (token.isEmpty() || tokenRequiresLogin(token)) {
                        continue
                    }
                    break
                }
                if (token.isEmpty() || tokenRequiresLogin(token)) {
                    throw lastError ?: IOException("Invalid token, or login failure.")
                }
            } finally {
                MUTEX.release()
            }
        }
        return token
    }

    private fun tokenRequiresLogin(token: String): Boolean {
        return (AccountUtil.isLoggedIn && !AccountUtil.isTemporaryAccount && token == ANON_TOKEN)
    }
}
