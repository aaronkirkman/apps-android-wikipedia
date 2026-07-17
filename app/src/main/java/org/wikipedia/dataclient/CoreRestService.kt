package org.wikipedia.dataclient

import org.wikipedia.dataclient.restbase.Revision
import retrofit2.http.GET
import retrofit2.http.Path

interface CoreRestService {

    @GET("v1/revision/{rev}")
    suspend fun getRevision(
        @Path("rev") rev: Long
    ): Revision

    companion object {
        const val CORE_REST_API_PREFIX = "w/rest.php/"
    }
}
